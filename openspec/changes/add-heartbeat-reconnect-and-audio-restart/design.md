## Context

当前链路通过 `adb forward` 把 Windows 本地 TCP 连接转发到 Android 服务。这个拓扑会产生“假存活”：Windows 只连到本机 adb，Android App 断开、播放线程停滞或 AudioTrack 不再推进时，Windows 仍可能继续写入成功，导致状态显示已连接但用户听不到声音。

仓库中已经存在 Windows 单向心跳和 Android 回送 `HeartbeatAck` 的基础，但它只证明 Android 读循环能收到包，不能表达 Android 当前是否真的在播放、播放延迟是否已经不可接受，也缺少 Windows 对 Android 主动状态包的确认。托盘菜单当前只有主界面、设置和退出，无法在不打开主窗口的情况下手动重建音频链路。

约束：保持 12 字节协议头不变；不改变 ADB forward 的连接方式；不新增外部依赖；Windows 与 Android 协议常量必须同步；自动重连应复用现有 `StreamingCoordinator` 的停止、启动和自动重连路径。

## Goals / Non-Goals

**Goals:**

- Android 定期主动上报播放状态，让 Windows 以真实播放端状态判断链路是否健康。
- Windows 对 Android 状态心跳回包，帮助 Android 识别 PC 端仍在接收状态上报。
- Windows 在仍存在候选设备或当前处于连接/推流状态时，因状态心跳超时触发音频链路重建。
- 托盘右键提供“重启音频”，执行与自动重连一致的干净停止和重新启动。
- 为状态 payload、超时判定、手动重启行为补充单元测试。

**Non-Goals:**

- 不重写传输协议头或切换到 WebSocket/UDP。
- 不把播放延迟做成完整监控面板；本次只作为链路健康字段和日志依据。
- 不新增用户可配置的心跳周期和超时阈值，先用常量实现。
- 不改变 Windows 音量控制协议语义。

## Decisions

### 决策 1：Android 主动发送播放状态心跳，替代单纯依赖 Windows 心跳

Android 服务在有活动客户端连接时定期发送播放状态心跳。payload 使用 JSON，字段包括：

- `isPlaying`：Android 端是否认为当前正在播放。
- `lastSequence`：最近收到并写入播放链路的音频帧序号。
- `bufferedLatencyMillis`：估算的播放缓冲/延迟，无法精确计算时可为 `null`。
- `lastAudioFrameAgeMillis`：距离最近一次音频帧的时间。
- `timestampElapsedRealtimeMillis`：Android 端单调时钟时间戳，用于日志和排障。

理由：只有 Android 播放端知道 AudioTrack 是否配置、最近是否收到音频帧、是否存在堆积延迟。Windows 写入成功不足以证明对端播放正常。

替代方案：继续使用 Windows `Heartbeat` + Android `HeartbeatAck`。被否决，因为 ACK 只覆盖 socket 读写活性，无法表达“已连接但没声音”的核心状态。

### 决策 2：新增明确的状态消息类型和 ACK 消息类型

新增消息类型建议为：

- `AndroidPlaybackStatus = 0x19`：Android → Windows，JSON payload。
- `AndroidPlaybackStatusAck = 0x1A`：Windows → Android，JSON payload，包含收到的 `sequence` 或 `timestamp`、`accepted=true`。

保留既有 `Heartbeat` / `HeartbeatAck` 作为底层保活兼容能力。Windows 的链路健康判定以 Android 主动状态心跳为主，收到其他 Android 入站消息仍可作为辅助活动信号，但不能替代播放状态心跳的超时判定。

理由：不复用 `HeartbeatAck`，避免把“收到心跳”与“播放健康状态”混在一起；不复用 `CommandAck`，避免和用户命令回执语义冲突。

### 决策 3：Windows 使用独立的 Android 状态时间戳和纯函数判定超时

Windows 在 `StreamingCoordinator` 中维护独立字段，如 `_lastAndroidPlaybackStatusUtcTicks`。收到 `AndroidPlaybackStatus` 后：

1. 解析并记录状态。
2. 更新最近状态时间。
3. 立即通过 `AudioTransportService` 回送 `AndroidPlaybackStatusAck`。

心跳循环或独立轻量检查循环定期调用纯函数，例如 `IsAndroidPlaybackStatusExpired(nowUtc, lastStatusUtc, timeout)`。当当前状态仍为 `Streaming` / `Preparing` / `Ready`，或设备监控仍发现目标设备在线，并且超过阈值未收到状态心跳时，触发与发送失败一致的 fault + 自动重连路径。

理由：播放状态时间戳不能被 Windows 自己发送音频或心跳刷新，否则会重新引入假存活问题。纯函数便于单元测试。

### 决策 4：播放延迟超阈值先记录和触发温和重建

当 Android 连续上报 `isPlaying=false` 或 `bufferedLatencyMillis` 超过阈值时，Windows 记录警告；若连续多次异常，按链路不健康处理并重启音频链路。阈值先用常量，例如状态周期 3 秒、超时 10 秒、连续异常 3 次、延迟异常阈值 2000ms。

理由：单次延迟尖峰可能来自调度或 GC，立即重连会抖动。连续异常更接近用户可感知故障。

### 决策 5：托盘“重启音频”调用协调器统一入口

`TrayService` 增加一个 `Action` 或 `Func<Task>` 回调。`App.xaml.cs` 注入回调，回调调用 `StreamingCoordinator.AutoConnectIfPossibleAsync("手动重启音频", restartIfRunning: true)`，或新增语义更清晰的 `RestartAudioAsync(reason)` 封装。菜单点击后立即记录日志，不阻塞 UI 线程。

理由：复用协调器中已有的串行化生命周期控制，避免托盘菜单直接操作传输层或采集层造成竞态。

## Risks / Trade-offs

- [误判重连] Android 状态协程短暂卡顿可能导致 Windows 误判 → 超时阈值大于发送周期 3 倍，并要求连续异常才重建。
- [旧端兼容] 旧 Android 不发送新状态心跳会被新版 Windows 判定超时 → 本功能要求两端同版本发布；文档中注明协议版本行为。
- [延迟估算不精确] AudioTrack 缓冲无法在所有设备上精确读取 → 字段允许为 `null`，超时判活不依赖该字段；延迟异常只作为辅助健康信号。
- [并发重启] 托盘手动重启与设备监控自动重连可能同时发生 → 统一进入 `StreamingCoordinator` 生命周期锁，保证串行执行。
- [日志噪音] 周期状态包可能产生大量日志 → 仅记录首包、异常和固定间隔摘要。

## Migration Plan

无数据迁移。实现时先同步协议常量和文档，再分别落地 Windows 与 Android 逻辑，最后补充测试。发布时 Windows 与 Android 应一起升级。回滚时移除新消息处理和托盘菜单项，保留既有 `Heartbeat` / `HeartbeatAck` 不影响旧行为。

## Open Questions

- `bufferedLatencyMillis` 的估算公式是否应基于 AudioTrack playback head、收到帧时间戳，还是先用 `lastAudioFrameAgeMillis` 作为近似指标？实现阶段需按现有 `AudioPlaybackManager` 可获得数据选择最小可靠方案。