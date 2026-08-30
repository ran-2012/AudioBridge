# Power Resume Handler

## MODIFIED Requirements

### Requirement: 系统睡眠恢复时恢复服务器就绪

系统 SHALL 监听 Windows 电源模式变化事件，在系统从睡眠或休眠状态恢复时确保 `adb reverse` 与服务器监听就绪，等待 Android 客户端重连。

#### Scenario: 系统从睡眠恢复时确保服务器就绪

- **WHEN** 系统接收到 `PowerModeChanged` 事件且 `Mode` 为 `Resume`
- **THEN** 系统 SHALL 在短暂延迟（2 秒）后确保 `adb reverse` 已建立且服务器处于监听状态，等待 Android 客户端重连（不再调用旧的 `AutoConnectIfPossibleAsync` 建立连接）

#### Scenario: 系统进入睡眠时不触发重连

- **WHEN** 系统接收到 `PowerModeChanged` 事件且 `Mode` 为 `Suspend`
- **THEN** 系统 SHALL 不触发任何重连操作

#### Scenario: 恢复时已在推流中则跳过

- **WHEN** 系统从睡眠恢复，但 `StreamingCoordinator.Status.State` 为 `Streaming`
- **THEN** 系统 SHALL 跳过重连操作，不中断当前推流

#### Scenario: 应用退出时注销电源事件

- **WHEN** 应用正常退出
- **THEN** 系统 SHALL 注销 `PowerModeChanged` 事件监听，防止内存泄漏
