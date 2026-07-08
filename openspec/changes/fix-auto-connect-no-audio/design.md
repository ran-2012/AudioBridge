## Context

自动连接由四个触发点驱动，均最终调用 `StreamingCoordinator.AutoConnectIfPossibleAsync` → `StartStreamingAsync`：

- `App.xaml.cs:91` 启动后自动连接（`Dispatcher.BeginInvoke` 异步发起）
- `App.xaml.cs:114` 配置修改后自动连接
- `App.xaml.cs:225` 休眠恢复后自动连接（延迟约 2 秒）
- `Services/DeviceMonitorService.cs:155` 设备轮询（每 ~5 秒）以 `_ = ...` 即发即弃方式触发

`StreamingCoordinator` 内部没有任何同步原语（无 `SemaphoreSlim`、无 `lock`）。`StartStreamingAsync` 的流程是：必要时 `PrepareAsync` → 检查 `Status.State == Ready` → `ConnectAsync` → 发送会话头/音量目录 → 启动心跳 → `AudioCaptureService.Start()` → 置为 `Streaming`。

而 `AudioTransportService.ConnectAsync` 的第一步是 `await DisconnectAsync()`，会无条件拆掉现有连接。音频帧发送受 `OnAudioFrameCaptured` 中的 `if (!_audioTransportService.IsConnected) return;` 闸门控制。

当两个触发点在毫秒级内并发进入 `StartStreamingAsync`：第一条已 `ConnectAsync` 成功并开始捕获，第二条进入后调用 `ConnectAsync` → `DisconnectAsync` 拆掉第一条的连接，造成状态错乱——状态显示已连接，但 `IsConnected` 与实际流不一致，音频帧被闸门拦截，表现为“连上了没声音”。手动断开再连接之所以有效，是因为这是一次干净的串行操作，没有并发干扰。

约束：协议不变；不改动 Android 端；尽量小改动，集中在协调器内部；保留现有公共方法签名与调用方代码。

## Goals / Non-Goals

**Goals:**

- 消除并发触发导致的 TCP 连接状态破坏，使自动连接一次成功即可出声。
- 自动连接与手动连接走同一条受保护路径，结果一致。
- 异常路径下能干净恢复，后续触发可重新连接。

**Non-Goals:**

- 不修改协议、不改动 Android 端。
- 不重构四个触发点的触发策略（仍保留启动/配置/休眠/轮询四个入口）。
- 不引入连接重试退避算法的改动（现有 `ReconnectDelay` 重连逻辑保持不变）。

## Decisions

### 决策 1：在 `StreamingCoordinator` 引入 `SemaphoreSlim(1,1)` 串行化连接生命周期

新增私有字段 `private readonly SemaphoreSlim _lifecycleLock = new(1, 1);`，用它包裹连接生命周期的核心区段。

- `StartStreamingAsync`、`StopStreamingAsync`、`AutoConnectIfPossibleAsync` 在入口 `await _lifecycleLock.WaitAsync(...)`，在 `finally` 中 `Release()`。
- 由于 `AutoConnectIfPossibleAsync` 内部会调用 `StopStreamingAsync` / `StartStreamingAsync`，为避免自我死锁，采用“外层公共方法持锁、内层提取无锁核心方法”的结构：
  - 提取 `StartStreamingCoreAsync` / `StopStreamingCoreAsync`（不获取锁，包含现有逻辑）。
  - 公共方法 `StartStreamingAsync` / `StopStreamingAsync` 仅负责获取锁后调用对应 Core 方法。
  - `AutoConnectIfPossibleAsync` 获取锁后，直接调用 Core 方法完成“状态判断 → 是否重建 → 启动”，整个决策在持锁状态下完成，关闭检查与执行之间的竞态窗口。

**为什么选 `SemaphoreSlim` 而非 `lock`**：生命周期方法是 `async`，区段内有 `await`，`lock`（Monitor）不支持跨 `await` 持有，`SemaphoreSlim` 是异步友好的标准选择。

**替代方案**：用 `_isStopping` 式的布尔标志同时拦截启动。被否决，因为布尔标志无法让后到的请求“等待并接续”，只能直接丢弃，会让本应生效的自动连接被静默跳过，且仍有 check-then-act 竞态。

### 决策 2：保留 `_isStopping` 标志语义，但不再依赖它做并发拦截

`_isStopping` 仍用于 `OnWindowsVolumeSnapshotChanged` 等回调中判断是否处于停止过程，避免在停止中发送数据。并发互斥改由 `_lifecycleLock` 负责，职责分离。

### 决策 3：异常路径在 Core 方法内清理，锁在外层 `finally` 释放

`StartStreamingCoreAsync` 的 `catch` 仍负责 `Stop` 捕获、`DisconnectAsync`、状态置为 `Faulted`；锁的释放放在公共方法的 `finally`，确保无论成功失败都释放，避免死锁。

## Risks / Trade-offs

- [自我死锁风险] `AutoConnectIfPossibleAsync` 调用已持锁方法 → 通过提取无锁 Core 方法、公共方法只在最外层加锁来规避。实现时必须确保 Core 方法内部不再获取 `_lifecycleLock`。
- [串行化带来轻微延迟] 并发触发会被排队，后到者需等待前一个生命周期操作完成。该延迟可接受，且正是期望行为（避免互相打断）。
- [`Dispose` 期间的锁] `Dispose`/`DisposeAsync` 不获取生命周期锁，仅做事件解绑与资源释放；需确认释放时不会与进行中的生命周期操作冲突。现有 `Dispose` 行为保持不变，风险低。
- [取消令牌] `WaitAsync(cancellationToken)` 在取消时抛 `OperationCanceledException`，由现有 catch 处理，不会泄漏锁（在 `finally` 释放，但若 `WaitAsync` 本身被取消则未获得锁，不应 `Release`）→ 需注意仅在成功获取锁后才在 `finally` 释放。

## Migration Plan

无数据/协议迁移。改动为单文件内部重构 + 新增测试。回滚策略：还原 `StreamingCoordinator.cs` 即可，无外部副作用。

## Open Questions

- 无。`AutoConnectIfPossibleAsync` 当前重复的状态判断（`restartIfRunning` 分支与随后的 `Streaming/Preparing` 跳过判断）在持锁后保持原语义即可，无需额外行为变更。
