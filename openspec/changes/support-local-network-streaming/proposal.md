## Why

当前架构维护两套相反拓扑：ADB 模式为 **Windows 作 TCP 客户端**（经 `adb forward` 连 `127.0.0.1:5000`）、Android 作服务器（`ServerSocket(5000)`）；而新需求的局域网模式要求 **Windows 作服务器**。两套拓扑并存导致 Windows 与 Android 两端都要维护「客户端/服务器」两种角色，连接发起方、自动重连责任不一致，复杂且易错。统一为「**Windows 始终作为 TCP 服务器，Android 始终作为 TCP 客户端**」可消除该分裂：ADB 模式改用 `adb reverse` 让 Android 经 USB 主动连接 Windows，LAN 模式让 Android 经 Wi-Fi 主动连接 Windows；同时支持延迟显示与局域网自动发现。

## What Changes

- **统一拓扑（BREAKING）**：Windows 端始终作为 TCP 服务器监听端口，Android 端始终作为 TCP 客户端主动连接。该变化同时作用于 ADB 与 LAN 模式，需 Windows 与 Android 两端同步升级；旧版 Android（`ServerSocket` 服务端模式）无法与新 Windows 服务器模式匹配。
  - **ADB 模式**：Windows 监听本机端口（默认 `5000`），改用 `adb reverse tcp:5000 tcp:5000` 将设备端口转发到 Windows；Android 连接设备本地 `127.0.0.1:5000`。
  - **LAN 模式**：Windows 监听局域网端口（默认 `6000`），Android 经 Wi-Fi 连接 Windows 的 IP 与端口。
- **连接与重连责任调整（BREAKING）**：Windows 不再主动「连接设备」，改为负责服务器监听、`adb reverse` 保活（设备上线 / 电源恢复时重建）并等待客户端；Android 端负责主动连接与断线自动重连。
- **局域网自动发现**：LAN 模式下 Android 端主动发送探测广播，Windows 端响应并返回服务公告，Android 自动发现并展示可连接的 Windows 服务器列表（保留手动输入 IP 回退与定向探测）。
- **延迟显示**：双端基于往返探测估算单向传输延迟，Windows 主界面与 Android 详情页展示。
- **ADB 模式保留为默认**：连接模式（`Adb` / `Lan`）可切换，默认 `Adb`；音频参数协商（SessionInit）、音量控制、播放状态等既有协议流程在统一拓扑下保持不变。

## Capabilities

### New Capabilities

- `server-streaming`: Windows 端以 TCP 服务器身份监听端口，在 ADB（`adb reverse` 转发）与 LAN（局域网直连）两种模式下接受 Android 客户端连接，复用现有桥接协议推流；维护单活跃客户端，断开后恢复监听。取代原 `lan-streaming` 能力（仅覆盖 LAN）。
- `client-reconnect`: Android 端作为 TCP 客户端主动连接 Windows 服务器，并具备断线自动重连能力；连接目标来自发现列表、手动输入或本机 reverse 端口。
- `lan-discovery`: LAN 模式下 Windows 端通过 UDP 广播周期公告服务信息（服务名、IP、端口），Android 端监听广播、维护并展示可用服务器列表，支持一键连接。
- `latency-reporting`: 双端基于往返探测估算单向传输延迟，Windows 主界面与 Android 详情页展示当前延迟。

### Modified Capabilities

- `device-monitor`: 目标设备上线时的触发语义从「调用 `AutoConnectIfPossibleAsync` 建立连接」改为「确保 `adb reverse` 与服务器监听就绪，等待 Android 客户端连接」。
- `power-resume-handler`: 系统从睡眠恢复时的触发语义同步调整：确保服务器监听与 `adb reverse` 就绪，等待 Android 客户端重连。

## Impact

- 协议：
  - 复用现有消息类型（SessionInit、AudioFrame、控制消息、Heartbeat/HeartbeatAck）。
  - 新增：`LatencyProbe = 0x1B` / `LatencyProbeAck = 0x1C`（延迟测量，payload 为毫秒时间戳）。
  - ADB 转发命令从 `adb forward` 改为 `adb reverse`。
  - 需要 Windows 与 Android 两端同步，并更新 `doc/Windows-Android通信技术文档.md`。
- Windows 代码：
  - `Models/AppSettings.cs`（新增连接模式、监听端口、发现开关、延迟显示开关）
  - `Models/BridgeMessageType.cs`（新增 LatencyProbe/LatencyProbeAck）
  - `Services/AdbService.cs`（新增 `adb reverse` 建立/移除能力）
  - `Services/AudioTransportService.cs`（从客户端改为服务器监听角色）
  - `Services/StreamingCoordinator.cs`（服务器模式路径：StartServer → Accept → SessionInit → 推流；断开恢复监听）
  - `Services/DeviceMonitorService.cs`、`PowerResumeHandler`（触发语义改为 reverse/服务器就绪）
  - 新增服务：`LanDiscoveryService`（监听发现端口、应答探测并单播返回公告）、延迟测量逻辑
  - `ViewModels` 与 `MainWindow`（模式选择、延迟显示、客户端状态）
- Android 代码：
  - `service/AudioBridgeService.kt`（从 `ServerSocket` 监听改为主动连接 Windows，增加断线自动重连）
  - 新增：发现探测广播发送、应答接收、服务器列表管理、连接/重连状态机
  - `network/ProtocolReader.kt`（处理 `LatencyProbe` 并回送 `LatencyProbeAck`）
  - `ui/`（服务器列表页、连接状态与延迟显示）
- 测试：两端纯逻辑单元测试（reverse 命令构造、公告编解码、RTT 计算、服务器接受/替换状态机、客户端重连状态机）；现有测试保持通过。
- 文档：`doc/Windows-Android通信技术文档.md`、`doc/技术方案.md`、`doc/开发状态.md`、`doc/测试方案.md`。
