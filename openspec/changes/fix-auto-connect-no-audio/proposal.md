## Why

自动连接功能上线后，出现“设备已连接但没有声音”的问题：自动连接成功后状态显示已连接，却收不到音频，必须手动断开再连接才能恢复播放。根因是多个自动连接触发点（启动、配置变更、休眠恢复、设备轮询）会并发调用 `StreamingCoordinator.StartStreamingAsync`，而协调器对启动/停止/自动连接没有任何串行化保护，导致并发调用互相踩踏 TCP 连接状态，最终音频帧被 `IsConnected` 闸门拦截而不发送。

## What Changes

- 为 `StreamingCoordinator` 的启动、停止、自动连接操作引入串行化机制（互斥锁），确保任意时刻只有一个连接生命周期操作在执行。
- 调整 `AutoConnectIfPossibleAsync`，使其在持锁状态下完成“判断当前状态 → 决定是否重建 → 启动推流”的完整决策，避免检查与执行之间出现竞态窗口。
- 保证手动断开/连接与自动连接走同一条受保护的代码路径，行为一致，消除“只有手动重连才有声音”的差异。

## Capabilities

### New Capabilities

- `streaming-coordinator`: 推流协调器对连接生命周期（准备、启动、停止、自动连接）的并发控制与状态一致性保证，确保自动连接与手动连接得到相同且可靠的音频推流结果。

### Modified Capabilities

<!-- 无现有 spec 的需求级行为变更。device-monitor 与 power-resume-handler 的触发行为不变，仅协调器内部串行化方式改变。 -->

## Impact

- 代码：`WinAudioBridge/AudioBridge/Services/StreamingCoordinator.cs`（新增串行化锁，包裹 `StartStreamingAsync`、`StopStreamingAsync`、`AutoConnectIfPossibleAsync`）。
- 调用方：`App.xaml.cs`（启动、配置变更、休眠恢复触发）、`Services/DeviceMonitorService.cs`（设备轮询触发）无需改动调用方式，仅依赖协调器内部的串行化。
- 测试：`AudioBridge.Tests/StreamingCoordinatorTests.cs` 新增并发自动连接的回归测试。
- 无协议变更，不影响 Android 端。
