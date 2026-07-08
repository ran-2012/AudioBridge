# Android Playback Liveness

## ADDED Requirements

### Requirement: Android 播放状态心跳

Android 接收端 SHALL 在与 Windows 建立活动连接后定期发送播放状态心跳。状态心跳 SHALL 至少包含 Android 当前是否播放、最近处理的音频帧序号、最近音频帧距当前的时间，以及可获得时的播放缓冲或延迟估算。

#### Scenario: 连接建立后周期上报播放状态

- **WHEN** Android 服务已接受 Windows TCP 连接并完成或正在等待 `SessionInit`
- **THEN** Android SHALL 按固定周期向 Windows 发送播放状态心跳

#### Scenario: 播放中状态包含最新音频进度

- **WHEN** Android 已收到并写入音频帧
- **THEN** 播放状态心跳 SHALL 包含最近处理的音频帧序号和最近音频帧距当前的时间

#### Scenario: 延迟不可估算时仍发送状态

- **WHEN** Android 无法可靠计算播放缓冲或延迟
- **THEN** Android SHALL 继续发送播放状态心跳，并将延迟字段置为空或使用约定的未知值

### Requirement: Windows 状态心跳确认

Windows 端 SHALL 在收到 Android 播放状态心跳后回送确认包。确认包 SHALL 使用协议定义的消息类型，并 SHALL 标识本次状态心跳已被 Windows 接收。

#### Scenario: Windows 收到播放状态后回包

- **WHEN** Windows 接收循环成功解析 Android 播放状态心跳
- **THEN** Windows SHALL 通过当前传输连接回送一条播放状态确认包

#### Scenario: Android 收到确认后更新 PC 活动状态

- **WHEN** Android 收到 Windows 播放状态确认包
- **THEN** Android SHALL 记录 PC 端仍在接收状态心跳，且 SHALL NOT 因该确认包改变本地播放音量或 Windows 音量状态

### Requirement: Windows 基于 Android 状态心跳判定链路健康

Windows 端 SHALL 使用最近一次 Android 播放状态心跳时间作为播放端存活依据。该时间戳 SHALL 独立于 Windows 发送音频帧、发送心跳或发送状态确认包的时间。

#### Scenario: 收到状态心跳刷新播放端存活时间

- **WHEN** Windows 收到 Android 播放状态心跳
- **THEN** Windows SHALL 更新最近一次 Android 播放状态时间

#### Scenario: 仅 Windows 发送成功不刷新播放端存活时间

- **WHEN** Windows 仅成功发送音频帧、心跳或确认包，但未收到 Android 播放状态心跳
- **THEN** Windows SHALL NOT 更新最近一次 Android 播放状态时间

#### Scenario: 状态心跳超时触发音频链路重建

- **WHEN** Windows 当前处于连接或推流相关状态，且超过存活超时阈值未收到 Android 播放状态心跳
- **THEN** Windows SHALL 判定音频链路不健康，停止当前采集与传输，并按自动重连设置重新建立音频链路

#### Scenario: 自动重连关闭时不重新连接

- **WHEN** Android 播放状态心跳超时且用户关闭了自动重连
- **THEN** Windows SHALL 将链路状态置为故障并记录日志，但 SHALL NOT 自动发起重连

### Requirement: Android 播放异常上报

Android 接收端 SHALL 在状态心跳中反映播放异常，包括未播放、长时间未收到音频帧或播放延迟超过阈值。Windows SHALL 对连续异常状态进行记录，并在达到判定条件后重建音频链路。

#### Scenario: Android 上报未播放

- **WHEN** Android 已连接 Windows 但本地 AudioTrack 未处于播放状态
- **THEN** Android 播放状态心跳 SHALL 将播放状态标记为未播放

#### Scenario: Windows 对连续异常状态重建链路

- **WHEN** Windows 连续收到多个未播放或延迟异常的 Android 播放状态心跳
- **THEN** Windows SHALL 判定音频链路不健康，并复用自动重连路径重建链路

#### Scenario: 单次异常不立即重连

- **WHEN** Windows 只收到一次播放异常状态，随后收到正常播放状态
- **THEN** Windows SHALL 记录异常但 SHALL NOT 重建链路