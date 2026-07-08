## Why

现有自动重连仍可能在 `adb forward` 假存活、Android 播放线程卡住或音频链路半断开时失效：Windows 端看起来仍连接，但 Android 已经不播放或延迟持续累积。需要把“Android 是否真的在播放、播放延迟是否正常”作为存活信号，并提供一个可手动触发的音频重启入口用于现场恢复。

## What Changes

- Android 端定期向 Windows 发送播放状态心跳，包含当前是否播放、最近音频序号、播放缓冲/延迟等信息。
- Windows 端收到 Android 播放状态心跳后立即回包确认，并记录最近一次 Android 状态时间。
- Windows 端在当前仍有目标设备在线或已处于连接/推流状态时，如果超过阈值收不到 Android 状态心跳，则判定链路不可用，停止当前音频链路并按自动重连设置重新建立 ADB forward、TCP 连接和音频采集。
- Windows 端托盘右键菜单新增“重启音频”操作，手动停止当前链路并重新执行自动连接/推流流程。
- 更新通信协议、开发状态和测试策略文档，明确状态心跳、确认包、超时重连和手动重启行为。

## Capabilities

### New Capabilities

- `android-playback-liveness`: Android 主动上报播放状态、Windows 确认并基于状态心跳超时判定链路失活的能力。
- `manual-audio-restart`: Windows 托盘菜单提供手动重启音频链路的能力，用于无需打开主窗口即可恢复播放。

### Modified Capabilities

<!-- 现有主线 spec（device-monitor、power-resume-handler）的需求级行为不变；本变更新增独立能力，并复用既有设备检测与自动重连流程。 -->

## Impact

- 协议：新增或正式化 Android → Windows 播放状态心跳消息，以及 Windows → Android 心跳确认消息；两端消息类型常量和 JSON payload 需保持一致。
- Windows 代码：`BridgeMessageType`、`AudioTransportService`、`StreamingCoordinator`、`TrayService`、`App.xaml.cs`，以及相关单元测试。
- Android 代码：`BridgeMessageType.kt`、`BridgeFrameEncoder.kt`、`AudioBridgeService.kt`、`AudioPlaybackManager.kt`、`PlaybackStateRepository.kt`，以及相关 JVM 单元测试。
- 文档：`doc/Windows-Android通信技术文档.md`、`doc/开发状态.md`、`doc/测试方案.md`。
- 无新增外部依赖；保持既有 12 字节二进制协议头和 ADB forward 连接方式。