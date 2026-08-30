# Client Reconnect

## ADDED Requirements

### Requirement: Android 端作为客户端主动连接并自动重连

系统 SHALL 支持 Android 端作为 TCP 客户端主动连接 Windows 服务器，并在连接断开后依据重连策略自动重连，直至成功、用户停止或目标不可达超时。

#### Scenario: 启动后自动尝试连接

- **WHEN** Android 端后台服务启动且存在可用的连接目标（reverse 端口、已保存服务器或当前选定的服务器）
- **THEN** 系统 SHALL 主动发起连接，并在失败时按退避策略自动重试

#### Scenario: 连接断开后自动重连

- **WHEN** 已建立的连接意外断开且用户未主动停止
- **THEN** 系统 SHALL 释放播放资源、记录断开原因，并按退避策略自动重连目标服务器

#### Scenario: 用户停止后不再重连

- **WHEN** 用户主动停止服务或退出应用
- **THEN** 系统 SHALL 停止自动重连并释放连接与播放资源

### Requirement: 连接目标来源

系统 SHALL 支持 Android 端连接目标来自三类来源：ADB 模式的设备本地 reverse 端口、LAN 模式的自动发现服务器列表、用户手动输入的 IP 与端口。

#### Scenario: 使用 reverse 端口作为 ADB 目标

- **WHEN** Android 端处于 ADB 连接模式
- **THEN** 系统 SHALL 以设备本地 `127.0.0.1:<端口>`（reverse 转发端口）作为连接目标

#### Scenario: 使用发现列表中的服务器

- **WHEN** 用户在自动发现列表中选择一台 Windows 服务器
- **THEN** 系统 SHALL 以该服务器的 IP 与端口作为连接目标并建立连接

#### Scenario: 使用手动输入的目标

- **WHEN** 用户手动输入 Windows 服务器的 IP 与端口并发起连接
- **THEN** 系统 SHALL 以该地址作为连接目标建立连接，并允许纳入自动重连流程

### Requirement: 重连状态可见

系统 SHALL 在 Android 端界面展示当前连接与重连状态（连接中、已连接、重连中、已断开），便于用户判断。

#### Scenario: 展示连接与重连状态

- **WHEN** Android 端正在连接、已连接或处于重连中
- **THEN** 系统 SHALL 在界面展示对应状态，并显示当前连接目标
