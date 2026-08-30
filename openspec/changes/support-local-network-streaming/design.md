## Context

当前链路为 **USB + ADB 转发**，拓扑为 Windows 作 TCP **客户端**（经 `adb forward` 连 `127.0.0.1:5000`）、Android 作 **服务器**（`ServerSocket(5000)`）；设备发现依赖 `adb devices` 轮询，自动重连由 Windows 侧 `AutoConnectIfPossibleAsync` 驱动。

本变更将拓扑**统一为 Windows 始终作服务器、Android 始终作客户端**，同时覆盖 ADB 与 LAN 两种模式：

- ADB 模式：Windows 监听本机端口，`adb reverse` 让 Android 经 USB 主动连接。
- LAN 模式：Windows 监听局域网端口，Android 经 Wi-Fi 主动连接（配 UDP 广播发现）。
- 双端基于往返探测估算单向延迟并展示。

约束：

- 复用既有桥接协议（SessionInit / AudioFrame / 控制消息 / Heartbeat），保持 Windows 与 Android 行为一致。
- 连接/重连责任从 Windows 转移至 Android（Windows 作服务器无法主动连设备）。
- 最小侵入；协议与行为变更需双端同步并更新测试。

## Goals / Non-Goals

**Goals:**

- Windows 端提供 TCP 服务器监听，在 ADB（`adb reverse`）与 LAN（局域网直连）两种模式下接受 Android 客户端连接并推流。
- Android 端作为客户端主动连接 Windows，并具备断线自动重连能力。
- LAN 模式下 Android 主动发送探测、Windows 响应服务公告、Android 自动发现并一键连接。
- 双端测量并展示单向传输延迟（估算值）。
- 连接模式（ADB / LAN）可切换，默认 ADB 以贴近现有使用习惯（拓扑本身已反转）。

**Non-Goals:**

- 不做公网 / 跨网段穿透（仅同网段局域网）。
- 不做音频编码压缩（仍为 PCM，参数协商沿用 SessionInit）。
- 不做多客户端并发接入（首版单活跃客户端）。
- 不做发现与连接的加密配对（信任本地网络）。
- 不改变音频采集、播放、音量控制等既有业务逻辑。

## Decisions

### D1：统一服务器拓扑 —— Windows=Server、Android=Client（ADB 与 LAN 通用）

现状是 Windows 作客户端、Android 作服务器。统一后：Windows 用 `TcpListener` 监听端口，Android 用 `Socket` 主动连接。

- **ADB 模式**：Windows 监听 `127.0.0.1:5000`（仅本机，避免局域网暴露），并执行 `adb reverse tcp:5000 tcp:5000`，使设备上的 `127.0.0.1:5000` 转发到 Windows；Android 连接设备本地 `127.0.0.1:5000`。
- **LAN 模式**：Windows 监听 `0.0.0.0:6000`，Android 连接 Windows 的 IP:6000（来自发现或手动输入）。
- **实现**：`AppSettings` 新增 `ConnectionMode`（`Adb` / `Lan`，默认 `Adb`）。`AudioTransportService` 保留既有包读写/音频帧/控制消息/心跳逻辑，由客户端角色改为服务器监听角色（`TcpListener` 接受连接后复用同一套逻辑）；`StreamingCoordinator` 按模式路由：启动服务器 → 接受连接 → 发 `SessionInit` → 推流 → 断开后恢复监听。
- **备选**：新建独立 `LanServerTransportService` 完全复用服务器通道 → 与既有服务重复收发逻辑；统一让 `AudioTransportService` 承担服务器角色更利于双端一致与最小改动。
- **端口分配**：ADB 复用 `5000`（协议文档既有值），LAN 用 `6000` 与既有 Android `5000` 监听语义区分，避免混淆。

### D2：连接与重连责任在 Android 端，Windows 负责「就绪 + reverse 保活」

Windows 作服务器后无法主动「连」设备，因此自动连接/重连的责任转移到 Android 端：

- **Android**：前台服务启动后即尝试连接（ADB 模式连 `127.0.0.1:5000`；LAN 模式连发现/保存的服务器），失败按退避策略重试；断线后自动重连（新增 `client-reconnect` 能力，替代 Windows 侧 `AutoConnect` 的角色）。
- **Windows**：负责两件事——(a) 服务器始终监听并等待接入；(b) 在 ADB 模式下确保 `adb reverse` 有效。设备上线（`DeviceMonitor`）或系统电源恢复（`PowerResumeHandler`）时，触发语义从「`AutoConnectIfPossibleAsync` 建立连接」改为「确保 reverse 建立 + 服务器在监听」（reverse 在设备拔插/休眠后失效，需重建）。
- **备选**：Windows 在 reverse 就绪后经 `adb shell am broadcast` 通知 Android 立即连接 → 多一层 ADB 依赖与权限，首版不采用；Android 侧周期重试更简单可靠。
- **兼容性**：此为行为反转，**BREAKING**；需双端同步升级，旧版 Android（`ServerSocket` 服务端）无法匹配新版 Windows 服务器。

### D3：发现机制 —— Android 主动探测，Windows 被动应答（仅 LAN 模式）

采用 **pull 模式**：Android 在需要时发送 UDP 广播探测，Windows 监听发现端口、收到后单播回复服务公告。相比 Windows 周期广播，省去无谓的周期流量、Android 打开即搜索、发现更即时。

- **探测（Android → 广播 `255.255.255.255:<DiscoveryPort>`，默认 `9000`）**：
  `{"t":"winAudioBridgeProbe","app":"dev.ran.audiobridge","ver":1}`
- **应答（Windows → 单播到探测来源）**：
  `{"t":"winAudioBridgeAnnounce","name":"<主机名>","host":"<本机IPv4>","port":6000,"ver":1}`
- **Windows 端 `LanDiscoveryService`**：绑定 `0.0.0.0:<DiscoveryPort>` 监听 UDP；收到探测后校验 `t == "winAudioBridgeProbe"`，从数据报取得请求来源地址与本机对外 IPv4（socket 本地地址或枚举网卡），单播回送公告。
- **Android 端 `LanDiscoveryClient`**：打开搜索/列表时发送广播探测；绑定 `<DiscoveryPort>` 接收应答，解析并按 `name+host` 去重维护服务器列表，`15s` 未收到应答则标记离线；支持周期重发（如每 3~5s 刷新）与手动刷新；定向探测（单播到手动输入的 IP）复用同一应答协议。
- **备选**：Windows 周期广播 + Android 被动监听（Android 无需主动搜索即出现，但需持续广播）→ 保留为可选的补充广播；UDP 组播（`239.x.x.x` + `MulticastLock`）→ 需额外锁与路由支持。首版采用 Android 主动探测 + Windows 应答。
- **回退**：广播在 AP 隔离或跨网段时不可达，保留「手动输入 IP:端口」入口。

### D4：延迟测量 —— 新增 `LatencyProbe` / `LatencyProbeAck`，RTT/2 估算单向延迟

- 协议新增：`LatencyProbe = 0x1B`（Windows → Android，payload 为 UInt64 毫秒发送时间戳）、`LatencyProbeAck = 0x1C`（Android → Windows，回显时间戳）。
- Windows 在推流期间每 `3s` 发送一次 `LatencyProbe`，收到 `LatencyProbeAck` 后计算 `RTT = now - sendTimestamp`，单向延迟 ≈ `RTT / 2`，对最近样本滑动平均后暴露给 ViewModel，主界面显示「延迟 ≈ XX ms」。
- **备选**：给现有 `Heartbeat` 加时间戳 → 改动 `add-heartbeat-liveness-detection` 定义的空负载语义，耦合存活判定；NTP 时钟同步按帧时间戳算端到端延迟 → 精度高但复杂度高。首版选独立消息 + RTT/2 估算。
- **已知局限**：RTT/2 不含 Android 播放缓冲排队时间，UI 标注「估算延迟」。
- **兼容性**：`ProtocolReader` 对未知消息会 `error(...)`，故 `LatencyProbe` 仅在 LAN 模式推流时发送（LAN 模式要求双端新版）；ADB 模式暂不发送以最小化旧端风险（也可视联调统一启用）。

### D5：单活跃客户端，新连接替换旧连接

Windows 服务器同一时刻仅维护一个活跃客户端连接。新连接接入时，若已有活跃连接，先关闭旧连接再接受新连接，并记录日志。

- **备选**：拒绝新连接（Android 显示「服务器忙」）→ 换设备场景体验差；「替换」更符合直觉。首版选择替换。

### D6：UI 与设置暴露

- `AppSettings` 新增：`ConnectionMode`（默认 `Adb`）、`LanListenPort`（`6000`）、`DiscoveryPort`（`9000`）、`EnableLanDiscovery`（默认 `true`）、`EnableLatencyDisplay`（默认 `true`）。
- Windows `MainWindow`：状态栏显示当前模式、已连接客户端、估算延迟。
- Android：新增服务器列表选择入口（自动发现 + 手动输入回退），详情页展示连接 RTT 与重连状态。

## Risks / Trade-offs

- **BREAKING 拓扑反转**：旧版两端无法互连，需同步升级 → 明确版本升级说明；ADB 模式默认保留、协议其余部分不变以降低迁移成本。
- **`adb reverse` 在设备拔插/休眠后失效** → `DeviceMonitor` / `PowerResumeHandler` 负责在设备上线、电源恢复时重建 reverse。
- **广播不可达（AP 隔离 / 跨网段）** → 保留手动输入 IP 回退；文档说明适用同网段。
- **RTT/2 低估真实端到端延迟（未计播放缓冲）** → UI 标注「估算延迟」；后续可结合播放缓冲水位修正。
- **Android 断线重连可能长时间无目标（LAN 下服务器下线）** → 重连退避 + 列表超时剔除；用户可手动切换目标。
- **任意同网段设备可连接或冒充服务器（无加密/配对）** → 首版面向受信任本地网络，接受风险；后续可加 PIN/token。
- **Android WiFi 休眠导致发现监听中断** → 发现监听运行于前台服务（网络类型），必要时持 WiFi 锁。

## Migration Plan

1. 双端同时升级：Windows 发布服务器版本、Android 发布客户端版本（拓扑反转无法单端升级）。
2. 设置默认 `ConnectionMode = Adb`，用户在配置好 LAN 后切换；两端端口/开关可配置。
3. 回滚：恢复到旧版双端即可（协议其余部分未变），或保持 ADB 模式（新双端仍支持 ADB 链路）。
4. 协议文档、状态文档、测试方案随变更同步更新。

## Open Questions

- `LatencyProbe` 是否在 ADB 模式也发送？首版仅 LAN 模式发送以控制风险，待联调确认是否统一。
- 是否需要 Android 连接后发送 `ClientHello`（携带设备名）供 Windows 端展示/校验客户端？首版默认不要求。
- 延迟显示精度是否满足需求？若需端到端（含缓冲）延迟，需引入时钟同步或缓冲水位估算，作后续迭代。
- 多客户端（手机 + 平板同时接收）是否纳入后续版本？首版明确单客户端。
- Android 端连接目标的持久化：LAN 模式下是否记住上次成功连接的服务器以加快重连？建议在 `client-reconnect` 中实现，待确认。
