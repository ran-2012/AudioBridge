# Tasks

## 1. 引入串行化锁

- [x] 1.1 在 `StreamingCoordinator` 新增私有字段 `SemaphoreSlim _lifecycleLock = new(1, 1)`
- [x] 1.2 在 `Dispose` / `DisposeAsync` 中释放 `_lifecycleLock`（`Dispose()`），确保资源回收

## 2. 重构连接生命周期方法为“外层加锁 + 内层无锁 Core”

- [x] 2.1 将现有 `StartStreamingAsync` 主体提取为无锁的 `StartStreamingCoreAsync(CancellationToken, bool)`
- [x] 2.2 将现有 `StopStreamingAsync` 主体提取为无锁的 `StopStreamingCoreAsync()`
- [x] 2.3 公共 `StartStreamingAsync` 改为：成功 `await _lifecycleLock.WaitAsync(ct)` 后调用 Core，并在 `finally` 中仅在已获锁时 `Release()`
- [x] 2.4 公共 `StopStreamingAsync` 改为：获取锁后调用 Core，`finally` 释放锁
- [x] 2.5 `AutoConnectIfPossibleAsync` 改为：获取锁后在持锁状态下完成“状态判断 → 是否重建（调用 `StopStreamingCoreAsync`）→ 调用 `StartStreamingCoreAsync`”，`finally` 释放锁
- [x] 2.6 确认 Core 方法内部不再获取 `_lifecycleLock`，避免自我死锁
- [x] 2.7 确认 `WaitAsync` 取消（`OperationCanceledException`）时不会执行 `Release()`（仅在成功获锁后释放）

## 3. 验证音频帧路径

- [x] 3.1 确认自动连接成功后 `Status.State == Streaming` 且 `AudioTransportService.IsConnected == true`，`OnAudioFrameCaptured` 闸门不再误拦截
- [x] 3.2 确认 `_isStopping` 语义不变，仅用于停止过程中的回调拦截，不再承担并发互斥职责

## 4. 测试

- [x] 4.1 在 `AudioBridge.Tests/AudioTransportServiceTests.cs` 新增传输层连接契约测试（折中方案，避免对 7 个 sealed 服务做接口反转）：锁定 `ConnectAsync` 在已连接时会拆掉旧连接的 destructive 行为——这正是并发 `StartStreamingAsync` 导致“连上没声音”的根因，现已由 `StreamingCoordinator._lifecycleLock` 串行化规避
- [x] 4.2 新增传输层用例：单次连接建立成功、未连接时 `SendAudioFrameAsync` 抛 `InvalidOperationException`、`DisconnectAsync` 清除连接状态
- [x] 4.3 运行 `dotnet test .\WinAudioBridge.sln /p:UseAppHost=false`，确保全部通过（39 通过）

## 5. 文档

- [x] 5.1 按需更新 `doc/开发状态.md` 记录该修复（自动连接并发竞态导致无声音）
