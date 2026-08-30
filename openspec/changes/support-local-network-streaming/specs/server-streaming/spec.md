# Server Streaming

## ADDED Requirements

### Requirement: Windows 端作为 TCP 服务器监听（ADB 与 LAN 双模式）

系统 SHALL 使 Windows 端始终以 TCP 服务器身份监听端口，接受 Android 客户端连接，并复用现有桥接协议传输音频与控制数据。ADB 模式下监听本机端口并配合 `adb reverse`；LAN 模式下监听局域网端口。

#### Scenario: ADB 模式下启动本机监听并建立 reverse

- **WHEN** 连接模式为 `Adb` 且 ADB 监听端口未被占用
- **THEN** 系统 SHALL 监听 `127.0.0.1:<端口>`（默认 `5000`），并执行 `adb reverse tcp:<端口> tcp:<端口>` 使设备端口转发到 Windows，进入等待客户端接入状态

#### Scenario: LAN 模式下启动局域网监听

- **WHEN** 连接模式为 `Lan` 且 LAN 监听端口未被占用
- **THEN** 系统 SHALL 监听 `0.0.0.0:<LanListenPort>`（默认 `6000`），进入等待客户端接入状态

#### Scenario: 监听端口被占用时回退提示

- **WHEN** 监听端口绑定失败（端口被占用或权限不足）
- **THEN** 系统 SHALL 记录错误日志并停止监听，保持另一模式可用，不中断其他功能

### Requirement: Android 端主动连接 Windows 服务器

系统 SHALL 支持 Android 端作为 TCP 客户端主动连接 Windows 服务器；ADB 模式下连接设备本地 reverse 端口（`127.0.0.1:<端口>`），LAN 模式下连接发现或手动输入的服务器地址。

#### Scenario: ADB 模式下连接本机 reverse 端口

- **WHEN** Android 端处于 ADB 连接模式且本机 `127.0.0.1:<端口>` 可连接
- **THEN** 系统 SHALL 以 TCP 客户端身份连接该端口，并在成功后进入推流就绪状态

#### Scenario: LAN 模式下连接选定服务器

- **WHEN** 用户在 Android 端选择一台已发现的或手动输入的 Windows 服务器
- **THEN** 系统 SHALL 以 TCP 客户端身份连接该服务器的 IP 与端口，并在成功后进入推流就绪状态

#### Scenario: 连接失败时给出可恢复状态

- **WHEN** Android 端连接目标服务器失败（超时、拒绝或网络不可达）
- **THEN** 系统 SHALL 记录失败原因、保持目标列表可用，并允许用户重试或选择其他目标

### Requirement: 连接建立后按现有协议推流

系统 SHALL 在连接建立后复用现有桥接协议流程：Windows 端发送 `SessionInit`（含编码、采样率、声道、位深、Buffer 毫秒数等参数），随后持续发送 `AudioFrame`；控制与状态消息在同一连接上双向传输。

#### Scenario: 接受连接后启动推流

- **WHEN** Windows 端接受一个 Android 客户端连接
- **THEN** 系统 SHALL 发送 `SessionInit` 并开始音频采集与帧发送

#### Scenario: 参数协商沿用 SessionInit

- **WHEN** Windows 端发送 `SessionInit`
- **THEN** Android 端 SHALL 依据其中的编码、采样率、声道、位深与缓冲参数初始化播放，行为与既有版本一致

### Requirement: 单活跃客户端与新连接替换

系统 SHALL 使 Windows 服务器同一时刻仅维护一个活跃客户端连接；当新连接接入且已有活跃连接时，SHALL 关闭旧连接后接受新连接。

#### Scenario: 新客户端连接替换旧客户端

- **WHEN** Windows 端已有活跃客户端连接，又有新的 Android 客户端发起连接
- **THEN** 系统 SHALL 关闭旧客户端连接、记录日志、接受新连接并为其建立推流

### Requirement: 连接断开后自动恢复监听

系统 SHALL 使 Windows 端在活跃客户端断开时回到监听状态等待新连接，且不影响服务器监听本身的存活。

#### Scenario: 客户端断开后回到监听

- **WHEN** 活跃客户端连接断开
- **THEN** 系统 SHALL 停止采集、释放该连接资源，并重新进入等待客户端接入状态

### Requirement: 连接模式切换（Adb / Lan）

系统 SHALL 提供连接模式设置（`Adb` / `Lan`），默认值为 `Adb`；切换模式不破坏另一模式的功能可用性。

#### Scenario: 默认使用 ADB 模式

- **WHEN** 应用首次启动且未显式设置连接模式
- **THEN** 系统 SHALL 默认采用 `Adb` 模式，拓扑为 Windows 服务器 + Android 客户端

#### Scenario: 切换至 LAN 模式

- **WHEN** 用户将连接模式切换为 `Lan`
- **THEN** 系统 SHALL 停止 ADB 监听与 reverse（如适用），启动局域网监听与发现服务

#### Scenario: 切回 ADB 模式

- **WHEN** 用户将连接模式从 `Lan` 切回 `Adb`
- **THEN** 系统 SHALL 停止局域网监听与发现服务，恢复 ADB 监听与 reverse 流程
