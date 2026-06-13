## Why

USB 设备插拔和系统从睡眠恢复时，Windows 端无法自动发现设备并重连音频推流，用户必须手动重启应用。这降低了产品可用性，尤其是在"插线即用"的核心体验场景中。

## What Changes

- 新增 **ADB 设备状态轮询监控**，周期性检测已连接设备的变化（新增、移除、状态变更），当检测到之前选中的目标设备恢复在线时自动触发重连
- 新增 **系统电源事件监听**，在系统从睡眠/休眠恢复（`PowerModeChanged` → `Resume`）时自动检查设备并重连
- 在 `AppSettings` 中新增 `EnableDeviceMonitor` 开关，允许用户关闭设备轮询监控（默认开启）
- 重连逻辑复用现有的 `ScheduleReconnect` / `ReconnectLoopAsync` 机制，避免重复实现

## Capabilities

### New Capabilities

- `device-monitor`: ADB 设备状态周期性轮询，检测设备插拔和应用运行状态变化，触发自动重连
- `power-resume-handler`: 监听系统电源状态变化，在从睡眠恢复时自动触发重连检查

### Modified Capabilities

<!-- No existing specs to modify -->

## Impact

- **WinAudioBridge/App.xaml.cs**：注册 `SystemEvents.PowerModeChanged`，启动设备监控服务
- **WinAudioBridge/Services/**：新增 `DeviceMonitorService`，负责定时轮询 ADB 设备状态
- **WinAudioBridge/Models/AppSettings.cs**：新增 `EnableDeviceMonitor` 属性
- **WinAudioBridge/Services/StreamingCoordinator.cs**：暴露 `AutoConnectIfPossibleAsync` 供外部触发（已有），无需改动核心逻辑
