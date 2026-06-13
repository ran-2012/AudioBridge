# Power Resume Handler

## Purpose

监听 Windows 系统电源模式变化事件，在系统从睡眠或休眠恢复时自动检查 ADB 设备连接并尝试重建音频推流。

## Requirements

### Requirement: 系统睡眠恢复时自动重连

系统 SHALL 监听 Windows 电源模式变化事件，在系统从睡眠或休眠状态恢复时自动检查设备连接并尝试重连。

#### Scenario: 系统从睡眠恢复时触发重连

- **WHEN** 系统接收到 `PowerModeChanged` 事件且 `Mode` 为 `Resume`
- **THEN** 系统 SHALL 在短暂延迟（2 秒）后调用 `StreamingCoordinator.AutoConnectIfPossibleAsync` 触发自动重连检查

#### Scenario: 系统进入睡眠时不触发重连

- **WHEN** 系统接收到 `PowerModeChanged` 事件且 `Mode` 为 `Suspend`
- **THEN** 系统 SHALL 不触发任何重连操作

#### Scenario: 恢复时已在推流中则跳过

- **WHEN** 系统从睡眠恢复，但 `StreamingCoordinator.Status.State` 为 `Streaming`
- **THEN** 系统 SHALL 跳过重连操作，不中断当前推流

#### Scenario: 应用退出时注销电源事件

- **WHEN** 应用正常退出
- **THEN** 系统 SHALL 注销 `PowerModeChanged` 事件监听，防止内存泄漏
