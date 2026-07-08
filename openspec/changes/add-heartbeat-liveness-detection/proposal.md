## Why

通过 `adb forward` 转发的 TCP 链路存在“假存活”问题：当 Android 接收端断开时，手机侧 socket 关闭，但 Windows ↔ 本地 adb 进程之间的 TCP 段仍然存活，`adb forward` 通道不拆。实测日志显示，设备侧手动断连后，Windows 端 `stream.WriteAsync` 仍持续成功、`TcpClient.Connected` 仍为 true、接收循环也收不到 FIN，因此 Windows 完全无法感知对端已断开——既不报错也不触发重连，表现为“设备显示已连接、无声音、永不恢复”。现有 Heartbeat 仅 Windows 单向发送、Android 收到不回 ACK、Windows 也不校验对端存活，无法覆盖该场景。

## What Changes

- 新增 `HeartbeatAck`（0x18）协议消息类型，由 Android 在收到 Windows `Heartbeat` 时回送，作为应用层存活确认。
- Android 端：收到 `Heartbeat` 后通过既有出站写通道回送 `HeartbeatAck`。
- Windows 端：
  - 接收到任意对端消息（含 `HeartbeatAck` 与音量控制消息）时更新“最近一次收到对端消息时间”。
  - 新增基于“接收侧空闲超时”的存活判定：在持续发送 Heartbeat 的同时，若超过阈值仍未收到任何对端消息，则判定链路死亡，走 fault + 自动重连路径（复用现有 `HandleTransportSendFailureAsync` / `ScheduleReconnect`）。
- 更新协议文档 `doc/Windows-Android通信技术文档.md` 中 Heartbeat/HeartbeatAck 的定义（从“预留”转为正式）。

## Capabilities

### New Capabilities

- `connection-liveness`: 基于应用层心跳与 ACK 的链路存活检测能力，覆盖 `adb forward` 假存活场景，确保对端断开后 Windows 能在有限时间内判定链路死亡并触发自动重连。

### Modified Capabilities

<!-- 现有 spec（device-monitor、power-resume-handler、streaming-coordinator）的需求级行为不变；本变更新增独立能力，复用而非修改既有重连触发逻辑。 -->

## Impact

- 协议：新增消息类型 `HeartbeatAck = 0x18`，需 Windows 与 Android 两端同步。
- Windows 代码：
  - `Models/BridgeMessageType.cs`（新增枚举值）
  - `Services/AudioTransportService.cs`（接收侧活动时间戳、发送 `HeartbeatAck` 能力不需要——由 Android 回送；Windows 仅需在接收时上报活动）
  - `Services/StreamingCoordinator.cs`（接收消息更新活动时间、新增接收侧空闲超时判活与触发重连）
- Android 代码：
  - `network/BridgeMessageType.kt`（新增常量）
  - `network/ProtocolReader.kt` 视情况无需改动（仅出站）
  - `service/AudioBridgeService.kt`（收到 Heartbeat 后经 `sendControlMessage` 回送 `HeartbeatAck`）
- 测试：Windows 端接收侧空闲判活单元测试；Android 端心跳回送逻辑单元测试。
- 文档：`doc/Windows-Android通信技术文档.md`、`doc/开发状态.md`。
