# Streaming Coordinator

## ADDED Requirements

### Requirement: 连接生命周期操作串行化

`StreamingCoordinator` SHALL 对连接生命周期操作（启动推流、停止推流、自动连接）进行串行化，确保任意时刻最多只有一个生命周期操作在执行，避免并发触发导致 TCP 连接状态被破坏。

#### Scenario: 并发自动连接只产生一条有效连接

- **WHEN** 多个触发点（启动、配置变更、休眠恢复、设备轮询）在极短时间内并发调用 `AutoConnectIfPossibleAsync`
- **THEN** 系统 SHALL 串行执行这些请求，最终建立且仅保留一条有效的 TCP 连接，并正常发送音频帧

#### Scenario: 启动期间的重复启动请求不破坏已有连接

- **WHEN** 一次 `StartStreamingAsync` 正在执行（已建立 TCP 连接但尚未进入 `Streaming`），此时另一次启动请求到达
- **THEN** 系统 SHALL 等待前一次操作完成后再处理，且 SHALL NOT 在前一次连接进行中调用 `DisconnectAsync` 破坏其连接

#### Scenario: 自动连接在持锁状态下完成状态判断与执行

- **WHEN** `AutoConnectIfPossibleAsync` 被调用
- **THEN** 系统 SHALL 在持有串行化锁的状态下完成“判断当前状态 → 决定是否重建 → 启动推流”的完整决策，确保状态检查与后续执行之间不存在竞态窗口

### Requirement: 自动连接与手动连接结果一致

`StreamingCoordinator` SHALL 保证自动连接成功后与手动连接成功后处于相同的可用状态，音频帧能够正常发送，无需用户手动断开再连接。

#### Scenario: 自动连接成功后立即发送音频帧

- **WHEN** 自动连接流程成功进入 `Streaming` 状态
- **THEN** 系统 SHALL 在传输层 `IsConnected` 为真的前提下持续发送捕获到的音频帧，用户 SHALL 能听到声音而无需手动重连

#### Scenario: 启动过程中出现异常时清理为可恢复状态

- **WHEN** `StartStreamingAsync` 在建立连接或初始化过程中抛出异常
- **THEN** 系统 SHALL 停止音频捕获、断开传输连接并释放串行化锁，使后续的自动连接或手动连接能够重新发起
