## 1. 设置模型扩展

- [x] 1.1 在 `AppSettings.cs` 中新增 `EnableDeviceMonitor` 属性（`bool`，默认 `true`）
- [x] 1.2 在 `SettingsService` 中增加对 `EnableDeviceMonitor` 的读写和变更通知支持

## 2. 设备监控服务实现

- [x] 2.1 创建 `DeviceMonitorService.cs`，实现 `IDisposable`
- [x] 2.2 实现 `StartAsync`：使用 `PeriodicTimer` 每 5 秒轮询 ADB 设备列表
- [x] 2.3 实现设备状态对比逻辑：记录上一次设备列表，检测目标设备的出现/消失/状态变化
- [x] 2.4 当检测到目标设备从不在线变为在线且应用运行时，调用 `StreamingCoordinator.AutoConnectIfPossibleAsync`
- [x] 2.5 仅在 `StreamingCoordinator.Status.State` 为 `Idle` 或 `Faulted` 时触发重连
- [x] 2.6 实现 `StopAsync`：停止轮询计时器并释放资源
- [x] 2.7 监听 `SettingsService.SettingsChanged`，当 `EnableDeviceMonitor` 变化时启停轮询

## 3. 电源事件处理

- [x] 3.1 在 `App.xaml.cs` 中注册 `SystemEvents.PowerModeChanged` 事件
- [x] 3.2 在 `Resume` 事件处理中，延迟 2 秒后调用 `StreamingCoordinator.AutoConnectIfPossibleAsync`
- [x] 3.3 仅在当前非推流状态时触发重连
- [x] 3.4 在应用退出（`CleanupBeforeShutdownAsync`）时注销事件

## 4. App.xaml.cs 编排

- [x] 4.1 在 `OnStartup` 中初始化 `DeviceMonitorService` 并启动（若设置开启）
- [x] 4.2 在 `CleanupBeforeShutdownAsync` 中停止 `DeviceMonitorService`

## 5. 测试

- [x] 5.1 为 `DeviceMonitorService` 添加单元测试：设备上线触发重连
- [x] 5.2 为 `DeviceMonitorService` 添加单元测试：正在推流时跳过触发
- [x] 5.3 为 `DeviceMonitorService` 添加单元测试：`EnableDeviceMonitor` 开关行为
- [x] 5.4 为电源恢复逻辑添加单元测试
