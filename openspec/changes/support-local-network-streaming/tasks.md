## 1. 协议与文档定义

- [x] 1.1 在 Windows `Models/BridgeMessageType.cs` 与 Android `network/BridgeMessageType.kt` 新增 `LatencyProbe = 0x1B`、`LatencyProbeAck = 0x1C`，payload 为 8 字节毫秒时间戳（UInt64 LE）
- [x] 1.2 更新 `doc/Windows-Android通信技术文档.md`：拓扑改为「Windows 作服务器、Android 作客户端」统一架构；ADB 模式改用 `adb reverse tcp:5000 tcp:5000`（原 `adb forward` 废弃）；新增 LAN 模式章节（监听 `6000`）、UDP 发现公告格式（JSON：`t/name/host/port/ver`，发现端口 `9000`）、`LatencyProbe`/`LatencyProbeAck` 定义
- [x] 1.3 更新 `doc/技术方案.md`：补充统一服务器拓扑、reverse 转发、LAN 发现与延迟测量的总体设计说明

## 2. Windows：设置与模型

- [x] 2.1 `Models/AppSettings.cs` 新增 `ConnectionMode`（`Adb`/`Lan`，默认 `Adb`）、`LanListenPort`（默认 `6000`）、`DiscoveryPort`（默认 `9000`）、`EnableLanDiscovery`（默认 `true`）、`EnableLatencyDisplay`（默认 `true`），并补充默认值工厂
- [x] 2.2 `SettingsWindow` 增加连接模式、LAN 端口、发现开关、延迟显示开关的设置项与持久化

## 3. Windows：服务器传输与 reverse

- [x] 3.1 `Services/AdbService.cs` 新增 `adb reverse` 建立/移除能力（`EnsureReverseForwardAsync` / 移除），供设备上线与电源恢复时调用
- [x] 3.2 扩展 `Services/AudioTransportService.cs` 为服务器监听角色：以 `TcpListener` 监听（ADB 模式 `127.0.0.1:5000`、LAN 模式 `0.0.0.0:6000`），接受连接后复用既有写包/读包/音频帧/控制消息逻辑
- [x] 3.3 `Services/StreamingCoordinator.cs` 改造为服务器模式路径：`StartServer → Accept → Send SessionInit → 推流`；断开后恢复监听；移除旧的客户端 `ConnectAsync` 主动连接路径（或仅保留 LAN 目标解析）
- [x] 3.4 实现单活跃客户端语义：新连接接入时关闭旧连接、记录日志、接受新连接并重建推流
- [x] 3.5 `DeviceMonitorService` 与 `PowerResumeHandler` 触发语义改为：确保 `adb reverse` 建立 + 服务器监听就绪，等待 Android 客户端连接（不再调用旧的 `AutoConnectIfPossibleAsync` 建立连接）

## 4. Windows：发现服务与延迟测量

- [x] 4.1 新增 `Services/LanDiscoveryService.cs`：绑定 `<DiscoveryPort>` 监听 UDP 探测，校验 `winAudioBridgeProbe` 后向探测来源单播回复 JSON 公告（服务标识、主机名、本机 IPv4、LAN 端口、版本），受 `EnableLanDiscovery` 与连接模式控制
- [x] 4.2 在推流期间周期（3s）发送 `LatencyProbe`，收到 `LatencyProbeAck` 后计算 RTT、滑动平均得单向延迟估算，受 `EnableLatencyDisplay` 控制
- [x] 4.3 将估算延迟、当前模式、已连接客户端暴露给 ViewModel，并在 `MainWindow` 状态栏/界面展示（标注「估算延迟」，无样本时显示待测量）

## 5. Android：客户端连接与重连

- [x] 5.1 `service/AudioBridgeService.kt` 从 `ServerSocket` 监听改为主动连接 Windows：ADB 模式连 `127.0.0.1:5000`、LAN 模式连选定服务器，复用既有协议解析与播放流程
- [x] 5.2 实现客户端断线自动重连状态机：启动即尝试连接、失败退避重试、断线自动重连、用户停止后不再重连，并在 UI 展示连接/重连状态
- [x] 5.3 新增局域网发现客户端：搜索时向 `255.255.255.255:<DiscoveryPort>` 发送探测广播（支持周期/手动刷新与定向探测），绑定端口接收应答、解析公告 JSON、按「服务标识 + 主机」去重维护服务器列表、15s 超时标记离线、忽略无效数据报
- [x] 5.4 `network/ProtocolReader.kt` 处理 `LatencyProbe` 并立即回送 `LatencyProbeAck`（回显时间戳）
- [x] 5.5 `MainViewModel` 与 UI：新增服务器列表选择页（自动发现 + 手动 IP/端口回退入口）、连接目标来源选择（reverse/发现/手动）、连接状态与 RTT 展示
- [x] 5.6 AndroidManifest 确认 `INTERNET`、网络状态相关权限与前台服务网络类型，支持局域网发现与连接

## 6. 单元测试（双端）

- [x] 6.1 Windows：`adb reverse` 命令/参数构造、`LatencyProbe`/`LatencyProbeAck` payload 编解码、RTT/滑动平均计算纯函数单元测试
- [x] 6.2 Windows：连接模式状态切换、服务器接受/替换/断开重监听状态机、reverse 保活触发（设备上线/电源恢复）单元测试
- [x] 6.3 Android：公告解析与服务器列表合并/超时剔除、`LatencyProbe` 处理回送逻辑单元测试
- [x] 6.4 Android：客户端连接/断线重连状态机（退避、停止、目标切换）单元测试
- [x] 6.5 双端协议一致性：`LatencyProbe` payload 格式（UInt64 LE 毫秒时间戳）与既有协议的帧头校验保持一致

## 7. 联调与文档收尾

- [ ] 7.1 ADB 链路冒烟（USB）：Windows 监听 + `adb reverse` → Android 主动连接 → 推流 → 音量控制全链路验证，设备拔插后 reverse 自动重建
- [ ] 7.2 LAN 链路冒烟（Wi-Fi）：发现 → 连接 → 推流 → 音量控制 → 延迟显示 全链路验证
- [ ] 7.3 断线重连验证：ADB/LAN 下断线后 Android 自动重连、服务器新客户端替换旧客户端行为
- [x] 7.4 更新 `doc/开发状态.md`（统一拓扑里程碑）与 `doc/测试方案.md`（新增 reverse/重连/发现/延迟测试项）

