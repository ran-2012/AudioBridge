# Tasks

## 1. 协议类型定义（两端）

- [x] 1.1 Windows：在 `Models/BridgeMessageType.cs` 新增 `HeartbeatAck = 0x18`
- [x] 1.2 Android：在 `network/BridgeMessageType.kt` 新增 `const val HEARTBEAT_ACK = 0x18`

## 2. Android 端：收到 Heartbeat 回送 HeartbeatAck

- [x] 2.1 在 `service/AudioBridgeService.kt` 的 `handleClient` 的 `Heartbeat` 分支，调用既有出站通道回送 `HEARTBEAT_ACK`（空负载，复用 12 字节头与 `outputLock`）
- [x] 2.2 复用或微调 `sendControlMessage`，支持发送空负载的二进制控制消息（或新增一个最小的 `sendHeartbeatAck` 辅助），避免与音量请求写冲突
- [x] 2.3 增加简洁日志（与现有 heartbeat 日志风格一致，按计数采样打印）

## 3. Windows 端：接收侧活动时间跟踪

- [x] 3.1 在 `StreamingCoordinator` 新增 `_lastInboundActivityUtcTicks` 字段及 `MarkInboundActivity()`（`Interlocked.Exchange`）
- [x] 3.2 在 `OnTransportMessageReceived` 入口对任意收到的对端消息调用 `MarkInboundActivity()`（包括 `HeartbeatAck` 与音量控制消息）
- [x] 3.3 在进入 `Streaming` 以及每次（重）连接成功后，将接收侧时间戳重置为当前时间，避免连接初期误判

## 4. Windows 端：接收侧空闲超时判活

- [x] 4.1 新增常量 `LivenessTimeout`（建议 15s，约 3× `HeartbeatIdleThreshold`）
- [x] 4.2 新增纯函数 `IsInboundLinkDead(DateTime nowUtc, DateTime lastInboundUtc, TimeSpan timeout)`，风格与 `ShouldSendHeartbeat` 一致
- [x] 4.3 在 `HeartbeatLoopAsync` 每次 tick 发送心跳后，调用判活逻辑；若判定死亡则调用 `HandleTransportSendFailureAsync("接收侧心跳超时，判定链路断开", ...)` 走既有 fault + `ScheduleReconnect`
- [x] 4.4 确认 `HandleTransportSendFailureAsync` 的去重标志（`_frameSendFaulted`）在该路径下行为正确，重连成功后被重置

## 5. 测试

- [x] 5.1 Windows：为 `IsInboundLinkDead` 新增单元测试（未超时/恰好超时/无活动初始态）
- [x] 5.2 Windows：验证 `OnTransportMessageReceived` 对 `HeartbeatAck` 等入站消息会刷新接收侧时间戳（如需可提取可测逻辑）
- [x] 5.3 Android：为“收到 Heartbeat 回送 HeartbeatAck”的编码逻辑新增单元测试（构造的字节符合 12 字节头 + type=0x18 + 空负载）
- [x] 5.4 运行 `dotnet test .\WinAudioBridge.sln /p:UseAppHost=false` 与 `.\gradlew.bat testDebugUnitTest`，确保全部通过

## 6. 真机验证

- [ ] 6.1 启动 Windows 端自动连接并出声后，在设备侧手动断开接收端
- [ ] 6.2 通过日志确认：Windows 在 `LivenessTimeout` 内判定链路死亡 → fault → 自动重连 → 恢复出声

## 7. 文档

- [x] 7.1 更新 `doc/Windows-Android通信技术文档.md`：将 Heartbeat 从“预留”转正，新增 `HeartbeatAck (0x18)` 定义与存活检测说明
- [x] 7.2 更新 `doc/开发状态.md`：记录 adb 假存活根因与应用层心跳判活修复
