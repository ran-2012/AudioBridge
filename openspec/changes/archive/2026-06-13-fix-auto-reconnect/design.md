## Context

当前系统仅在以下时机触发自动连接：
1. 应用启动时（`App.OnStartup` → `AutoConnectIfPossibleAsync`）
2. 设置变更时（`SettingsService_SettingsChanged`）
3. 音频传输失败后（`HandleTransportSendFailureAsync` → `ScheduleReconnect`）

但以下两种常见场景未被覆盖：
- 用户在应用运行后插入 USB 线连接设备 → 无任何检测机制
- 系统从睡眠/休眠恢复 → TCP 连接已断开，但无触发源通知应用重连

`StreamingCoordinator` 已有成熟的 `ScheduleReconnect` / `ReconnectLoopAsync` 重连机制，只需补充"何时触发"的逻辑。

## Goals / Non-Goals

**Goals:**
- 检测 ADB 设备的新增、移除、状态变更，并在目标设备恢复在线时自动触发重连
- 在系统从睡眠恢复时自动检查设备并重连
- 新增功能通过设置项可开关，不影响现有行为
- 复用现有重连机制，不引入新的重连循环

**Non-Goals:**
- 不实现 USB 事件驱动检测（Windows USB 设备事件），使用轮询方案更简单可靠
- 不修改 Android 端
- 不修改 ADB 端口转发层
- 不改变现有 `StreamingCoordinator` 的核心状态机

## Decisions

### 1. 使用定时轮询而非 USB 事件

**选择**：在 `DeviceMonitorService` 中使用 `PeriodicTimer` 定时轮询 ADB 设备列表。

**替代方案**：监听 `WM_DEVICECHANGE` Windows 消息检测 USB 设备插拔。
- 理由：USB 事件只能检测硬件插拔，但 ADB 授权、应用启动等状态变化仍需轮询。统一使用轮询更简单，且 ADB 查询本身轻量（本地 ADB server 通信）。

**轮询间隔**：默认 5 秒。此间隔平衡了响应速度和资源消耗。ADB 设备查询通常在 100ms 内完成。

### 2. 仅在非流式状态下触发重连

**选择**：`DeviceMonitorService` 仅在 `StreamingCoordinator.Status.State` 为 `Idle` 或 `Faulted` 时触发 `AutoConnectIfPossibleAsync`。

**理由**：传输层已有自己的故障检测和重连机制（`HandleTransportSendFailureAsync`）。设备监控不应干扰正在进行的正常推流。

### 3. 使用 `SystemEvents.PowerModeChanged` 处理睡眠恢复

**选择**：在 `App.xaml.cs` 中注册 `Microsoft.Win32.SystemEvents.PowerModeChanged` 事件，当 `Mode == PowerModes.Resume` 时触发重连。

**理由**：这是 .NET 标准的电源事件通知机制，无需 P/Invoke。`SystemEvents` 在 WPF 应用中天然可用。

### 4. 在 App.xaml.cs 层编排，而非 StreamingCoordinator 内部

**选择**：`DeviceMonitorService` 和电源事件处理均在 `App.xaml.cs` 层初始化和编排，通过调用 `StreamingCoordinator.AutoConnectIfPossibleAsync` 触发重连。

**理由**：
- `StreamingCoordinator` 保持专注（推流协调），不膨胀其职责
- 生命周期管理清晰：设备和电源监控与应用程序同生命周期
- 已测试的 `AutoConnectIfPossibleAsync` 零改动

### 5. 新增 EnableDeviceMonitor 设置项

**选择**：在 `AppSettings` 中增加 `EnableDeviceMonitor`（默认 `true`），控制设备轮询的启停。

**理由**：部分用户可能不希望后台轮询（如电池供电的笔记本），保留关闭选项。

## Risks / Trade-offs

- **[轮询开销]** 每 5 秒执行一次 ADB 设备查询 → 影响极小，ADB 查询仅查询本地 server 状态，不涉及 USB 通信
- **[频繁重连]** 设备快速插拔可能导致多次重连尝试 → `ReconnectLoopAsync` 已有 3 秒退避延迟，自然抑制抖动
- **[睡眠恢复时机]** `Resume` 事件触发时 ADB server 可能尚未恢复 → 重连循环会自行重试，最多几次失败后成功
- **[与现有重连冲突]** 设备监控和传输层同时触发重连 → `ScheduleReconnect` 已有去重保护（`_reconnectTask is { IsCompleted: false }` 时跳过）
