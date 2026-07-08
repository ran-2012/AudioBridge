# Connection Liveness

## ADDED Requirements

### Requirement: HeartbeatAck 协议消息

系统 SHALL 定义 `HeartbeatAck = 0x18` 协议消息类型，Windows 与 Android 两端的消息类型常量 SHALL 保持一致。Android 端在收到 Windows 发送的 `Heartbeat` 消息时 SHALL 通过既有出站写通道回送一条 `HeartbeatAck` 消息。

#### Scenario: Android 收到心跳后回送 ACK

- **WHEN** Android 接收端从 Windows 收到 `Heartbeat`（0x03）消息
- **THEN** Android SHALL 使用相同的 12 字节协议头格式回送一条 `HeartbeatAck`（0x18）消息

#### Scenario: 两端消息类型常量一致

- **WHEN** 比对 Windows `BridgeMessageType` 与 Android `BridgeMessageType`
- **THEN** 两侧 SHALL 均包含值为 `0x18` 的 `HeartbeatAck` 定义

### Requirement: 接收侧活动时间跟踪

Windows 端 SHALL 在收到来自对端的任意协议消息（包括 `HeartbeatAck` 及音量控制类消息）时更新“最近一次收到对端消息时间”。该接收侧时间戳 SHALL 独立于既有的发送侧活动时间戳。

#### Scenario: 收到对端消息刷新接收侧时间

- **WHEN** Windows 接收循环成功解析并派发任意一条来自对端的消息
- **THEN** 系统 SHALL 将“最近一次收到对端消息时间”更新为当前时间

#### Scenario: 仅发送不更新接收侧时间

- **WHEN** Windows 仅发送音频帧或心跳而未收到任何对端消息
- **THEN** 系统 SHALL NOT 更新“最近一次收到对端消息时间”

### Requirement: 接收侧空闲超时判活与重连

Windows 端 SHALL 在推流期间持续发送 `Heartbeat` 的同时，监控“最近一次收到对端消息时间”。当距离上次收到对端消息超过配置的存活超时阈值时，系统 SHALL 判定链路已死亡，并触发与发送失败相同的 fault + 自动重连路径。

#### Scenario: adb 假存活场景下的链路死亡判定

- **WHEN** 设备侧断开接收端，导致 Windows 在超过存活超时阈值内收不到任何对端消息（即使本地 `stream.WriteAsync` 仍“成功”、`TcpClient.Connected` 仍为 true）
- **THEN** 系统 SHALL 判定链路死亡，停止采集、断开传输并按 `EnableAutoReconnect` 设置触发自动重连

#### Scenario: 收到 ACK 时保持连接

- **WHEN** Windows 在存活超时阈值内持续收到 `HeartbeatAck` 或其他对端消息
- **THEN** 系统 SHALL 维持当前推流连接，不触发重连

#### Scenario: 用户关闭自动重连时仅判定不重连

- **WHEN** 链路被判定死亡但用户已将 `EnableAutoReconnect` 设为 `false`
- **THEN** 系统 SHALL 将状态置为 `Faulted` 并记录日志，但不发起自动重连
