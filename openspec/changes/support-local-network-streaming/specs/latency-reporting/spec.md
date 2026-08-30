# Latency Reporting

## ADDED Requirements

### Requirement: Windows 端周期发送延迟探测

系统 SHALL 在 LAN 模式推流期间，由 Windows 端周期性发送 `LatencyProbe` 消息（payload 为毫秒级发送时间戳），用于测量链路延迟。

#### Scenario: 推流期间周期发送 LatencyProbe

- **WHEN** Windows 端处于 LAN 模式且正在推流
- **THEN** 系统 SHALL 每 3 秒发送一次 `LatencyProbe`，payload 携带发送时刻的毫秒时间戳

#### Scenario: 非 LAN 模式不发送 LatencyProbe

- **WHEN** 连接模式为 `Adb`
- **THEN** 系统 SHALL 不发送 `LatencyProbe`，保持与旧版 Android 端兼容

### Requirement: Android 端回送延迟探测确认

系统 SHALL 支持 Android 端在收到 `LatencyProbe` 后立即回送 `LatencyProbeAck`，payload 回显 Windows 发送的时间戳。

#### Scenario: 收到探测后立即回送确认

- **WHEN** Android 端收到 `LatencyProbe`
- **THEN** 系统 SHALL 立即回送 `LatencyProbeAck`，payload 回显收到的毫秒时间戳

### Requirement: Windows 端计算并展示单向延迟

系统 SHALL 在 Windows 端收到 `LatencyProbeAck` 后计算往返时间（RTT = 当前时间 − 探测时间戳），以 RTT/2 估算单向传输延迟，对最近样本做滑动平均，并在主界面展示估算延迟值。

#### Scenario: 收到确认后计算并展示延迟

- **WHEN** Windows 端收到 `LatencyProbeAck`
- **THEN** 系统 SHALL 计算本次 RTT、更新滑动平均的单向延迟估算值，并在主界面显示「延迟 ≈ XX ms」且标注为估算值

#### Scenario: 暂无有效样本时显示待测量状态

- **WHEN** LAN 模式推流刚开始且尚无有效的 `LatencyProbeAck` 样本
- **THEN** 系统 SHALL 显示延迟为待测量状态，不显示误导性数值

### Requirement: 延迟显示开关

系统 SHALL 提供延迟显示开关；关闭后停止周期性延迟测量与展示。

#### Scenario: 关闭延迟显示

- **WHEN** 用户将延迟显示开关设置为 `false`
- **THEN** 系统 SHALL 停止发送 `LatencyProbe`、不再更新延迟展示，且不影响音频推流

### Requirement: Android 端展示连接 RTT

系统 SHALL 在 Android 端详情页展示当前连接的往返时间（RTT），辅助用户判断网络质量。

#### Scenario: 详情页展示 RTT

- **WHEN** Android 端存在活跃的 LAN 连接且近期发送过探测确认
- **THEN** 系统 SHALL 在详情页展示估算的往返时间或单向延迟值
