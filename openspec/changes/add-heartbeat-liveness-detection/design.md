## Context

实测复现：设备侧断开接收端后，Windows 日志显示音频帧仍持续“已发送”（count 一路递增），无 IOException、无 `ConnectionLost`、无发送失败，ADB 轮询仍显示设备 Online。根因是 `adb forward` 的链路拓扑：

```
Windows App  ──TCP──>  127.0.0.1:5000 (本地 adb)  ──adb forward──>  手机 adbd  ──>  Android App
```

Windows 的 `AudioTransportService` 连接的是本机 adb 进程，而非手机 App。设备侧 App 关闭时，只有最后一段（adbd→App）断开，Windows↔本地 adb 这段 TCP 仍存活，写入持续被 adb 缓冲接收，`TcpClient.Connected` 不变，接收循环也收不到 FIN。因此现有所有断线检测（发送失败、接收循环 `ConnectionLost`）都无法触发。

现状（来自代码勘察）：
- 协议头固定 12 字节：Magic(4) + Version(2) + MessageType(2) + PayloadLength(4)，小端。
- `BridgeMessageType` 当前最大值 `CommandAck = 0x17`，下一个空闲值 `0x18`。两端常量一致。
- Windows `MarkTransportActivity()` 只在**发送**音频帧/心跳后调用；**接收消息不更新活动时间**。
- Windows 心跳：`HeartbeatLoopAsync` 用 `PeriodicTimer(5s)`，`ShouldSendHeartbeat` 在发送侧空闲≥5s 时发送。
- Windows `OnTransportMessageReceived` 只处理 `VolumeCatalogRequest/VolumeSetMaster/VolumeSetSession`，无 `Heartbeat`/`HeartbeatAck` 分支。
- Android `ProtocolReader` 已能解析 `Heartbeat`；`AudioBridgeService.handleClient` 收到 Heartbeat 仅记日志，不回送。
- Android 已有出站写通道 `sendControlMessage(messageType, json, log)`，用相同 12 字节头，现用于发送音量请求。

约束：协议向后兼容（新增类型，不改旧类型）；最小改动；两端常量与文档同步；复用现有 fault/重连路径（`HandleTransportSendFailureAsync` → `ScheduleReconnect`）。

## Goals / Non-Goals

**Goals:**

- 在对端断开后的有限时间内（目标 ≤ ~15s），Windows 能判定链路死亡并按设置自动重连。
- 覆盖 `adb forward` 假存活场景，不依赖 TCP 层断开信号。
- 复用既有心跳发送与重连逻辑，改动集中、可测。

**Non-Goals:**

- 不做 RTT 延迟统计（文档预留项，后续可加）。
- 不改协议头结构、不改既有消息类型语义。
- 不引入新的网络库或线程模型。
- 不改 `adb forward` 的建立方式。

## Decisions

### 决策 1：新增 `HeartbeatAck = 0x18`，由 Android 回送

Windows 已周期性发 `Heartbeat`。让 Android 收到后回送 `HeartbeatAck`，Windows 据此确认对端存活。

**为什么用新类型而非复用 `Heartbeat` 回送**：避免 Windows 收到自己语义的 Heartbeat 产生歧义，且便于两端日志区分方向。`HeartbeatAck` 负载为空（0 字节）即可，无需 RTT 字段（Non-Goal）。

**为什么由 Android 回送而非 Windows 主动探测对端**：Windows 无法绕过 adb 直接探测手机；只有让真正的对端（Android App）回包，才能穿透 adb 假存活。

### 决策 2：Windows 引入独立的“接收侧活动时间戳”

新增 `_lastInboundActivityUtcTicks`，在 `OnTransportMessageReceived`（或传输层接收成功点）每收到一条对端消息就 `Interlocked.Exchange` 更新。与既有发送侧 `_lastTransportActivityUtcTicks` 区分：

- 发送侧时间戳：决定“是否该发心跳”（保持原逻辑不变）。
- 接收侧时间戳：决定“对端是否还活着”。

**为什么不复用同一个时间戳**：现有时间戳被发送行为刷新，而发送在 adb 假存活下一直“成功”，无法反映对端存活。必须用“收到对端消息”这一独立信号。

### 决策 3：在心跳循环内增加接收侧空闲超时判活

复用 `HeartbeatLoopAsync` 的周期 tick：每次 tick 在发送心跳后，检查 `now - _lastInboundActivityUtc >= LivenessTimeout`。超时则调用 `HandleTransportSendFailureAsync("接收侧心跳超时，判定链路断开", ...)`，从而走既有 fault + `ScheduleReconnect`。

- 阈值 `LivenessTimeout` 取 `HeartbeatIdleThreshold` 的约 3 倍（建议 15s），容忍偶发丢包/卡顿。
- 进入 `Streaming` 与每次（重）连接成功时，SHALL 把接收侧时间戳重置为“当前时间”，避免连接初期误判。
- 判活逻辑提取为纯函数（如 `IsInboundLinkDead(nowUtc, lastInboundUtc, timeout)`），便于单元测试，与现有 `ShouldSendHeartbeat` 风格一致。

**替代方案**：单独起一个判活 timer。被否决——复用心跳 tick 更简单，且判活与心跳节奏天然对齐。

### 决策 4：Android 回送复用 `sendControlMessage`

在 `handleClient` 的 `Heartbeat` 分支调用既有出站通道写 `HeartbeatAck`（空 JSON/空负载）。复用 `outputLock` 串行化，避免与音量请求写冲突。

## Risks / Trade-offs

- [误判风险] 真机偶发卡顿导致 ACK 延迟 → 用 3× 心跳周期（~15s）阈值容忍；并要求连接初期重置接收时间戳，避免握手阶段误判。
- [旧版 Android 不回 ACK] 若 Android 端未升级，则 Windows 永远收不到 ACK，会判定链路死亡并不断重连。缓解：本变更要求两端同时发布；且音量控制等任意入站消息也会刷新接收侧时间戳，但空闲音频场景下 Android 仍需回 ACK 才能保活。属预期行为（旧端视为不兼容）。
- [写冲突] Android 出站心跳与音量请求并发 → 复用既有 `outputLock` 串行化，无新增竞态。
- [Windows 接收线程更新时间戳的可见性] → 用 `Interlocked` 读写，跨线程安全。
- [阈值过短/过长权衡] 过短易误判，过长恢复慢 → 15s 为折中，作为常量便于后续调整。

## Migration Plan

无数据迁移。协议向后兼容（新增类型）。发布要求：Windows 与 Android 同版本一起发布，确保 Android 能回 `HeartbeatAck`。回滚：还原两端改动即可，旧逻辑不依赖新类型。

## Open Questions

- `LivenessTimeout` 是否需要做成用户可配置项？当前决定先用常量（15s），后续如有需要再接入设置项。
