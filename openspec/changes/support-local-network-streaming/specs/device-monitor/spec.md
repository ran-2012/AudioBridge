# Device Monitor

## MODIFIED Requirements

### Requirement: 周期性 ADB 设备轮询

系统 SHALL 在应用启动后以固定间隔轮询已连接的 ADB 设备列表，检测设备的新增、移除和状态变更。

#### Scenario: 目标设备上线时确保 reverse 与服务器就绪

- **WHEN** 轮询检测到上一次查询中目标设备不在线，而本次查询中目标设备在线且 AudioBridge 应用正在运行
- **THEN** 系统 SHALL 确保建立 `adb reverse` 转发并确认 Windows 服务器处于监听状态，等待 Android 客户端连接（不再调用旧的 `AutoConnectIfPossibleAsync` 建立连接）

#### Scenario: 已连接设备被移除时不触发重连

- **WHEN** 轮询检测到目标设备从已连接变为未连接
- **THEN** 系统 SHALL 不触发任何重连操作，仅记录日志

#### Scenario: 当前正在推流时不触发重连

- **WHEN** 轮询检测到设备状态变化，但 `StreamingCoordinator.Status.State` 为 `Streaming` 或 `Preparing`
- **THEN** 系统 SHALL 跳过本次触发，不中断当前推流

### Requirement: 设备轮询开关

系统 SHALL 在设置中提供 `EnableDeviceMonitor` 开关，允许用户启用或禁用设备轮询监控。

#### Scenario: 用户关闭设备监控

- **WHEN** 用户将 `EnableDeviceMonitor` 设置为 `false`
- **THEN** 系统 SHALL 停止设备轮询计时器，不再检测设备状态变化

#### Scenario: 用户开启设备监控

- **WHEN** 用户将 `EnableDeviceMonitor` 设置为 `true`
- **THEN** 系统 SHALL 启动设备轮询计时器，开始周期性检测设备状态

#### Scenario: 默认启用设备监控

- **WHEN** 应用首次启动且 `EnableDeviceMonitor` 未被显式设置
- **THEN** 系统 SHALL 默认启用设备监控（值为 `true`）
