# LAN Discovery

## ADDED Requirements

### Requirement: Android 端发送发现探测广播

系统 SHALL 支持 Android 端在 LAN 模式下发起发现，向 UDP 发现端口发送探测广播，探测内容包含服务标识与应用标识。

#### Scenario: 打开搜索时发送探测广播

- **WHEN** Android 端进入服务器搜索/列表界面且连接模式为 `Lan`
- **THEN** 系统 SHALL 向 `255.255.255.255:<DiscoveryPort>` 发送一次 JSON 格式探测广播，字段含服务标识与应用标识

#### Scenario: 周期或手动刷新探测

- **WHEN** 用户手动刷新，或列表处于自动刷新周期
- **THEN** 系统 SHALL 重新发送探测广播以刷新可用服务器

### Requirement: Windows 端响应发现探测

系统 SHALL 支持 Windows 端在启用局域网发现时，监听 UDP 发现端口；收到来自 Android 的有效探测后，单播回复服务公告，公告内容包含服务标识、主机名、本机 IPv4 地址、LAN 监听端口与协议版本。

#### Scenario: 收到探测后单播回复公告

- **WHEN** Windows 端处于 LAN 模式、启用局域网发现，且收到一条服务标识匹配的探测数据报
- **THEN** 系统 SHALL 校验探测内容，并向该探测的来源地址单播回复 JSON 格式服务公告（含服务标识、名称、主机、端口与版本）

#### Scenario: 忽略无效探测

- **WHEN** Windows 端收到内容不完整或服务标识不匹配的探测数据报
- **THEN** 系统 SHALL 忽略该数据报并记录调试日志，不回复公告

#### Scenario: 关闭发现开关后停止响应

- **WHEN** 用户将局域网发现开关设置为 `false`
- **THEN** 系统 SHALL 停止监听与响应发现探测，且不影响已建立的 LAN 推流连接

#### Scenario: LAN 模式退出时停止响应

- **WHEN** 连接模式从 `Lan` 切换为非 `Lan`
- **THEN** 系统 SHALL 停止监听与响应发现探测

### Requirement: Android 端接收应答并维护服务器列表

系统 SHALL 支持 Android 端绑定 UDP 发现端口接收服务公告应答，解析并维护可用的 Windows 服务器列表，用于用户选择连接。

#### Scenario: 收到应答后加入或更新列表

- **WHEN** Android 端收到一条来自某台 Windows 服务器的有效公告应答
- **THEN** 系统 SHALL 解析公告并按「服务标识 + 主机」去重，将服务器（含名称、IP、端口、最近接收时间）加入或更新到可用服务器列表

#### Scenario: 应答超时后标记离线

- **WHEN** 某台服务器超过 15 秒未再次收到其应答
- **THEN** 系统 SHALL 将该服务器标记为离线或从可用列表移除，并更新界面展示

#### Scenario: 忽略无效应答

- **WHEN** Android 端收到内容不完整或服务标识不匹配的数据报
- **THEN** 系统 SHALL 忽略该数据报并记录调试日志，不加入服务器列表

### Requirement: 手动 IP 连接回退

系统 SHALL 提供手动输入服务器 IP 与端口的方式，作为广播不可达（如 AP 隔离或跨网段）时的回退入口，并支持向该地址发送定向探测。

#### Scenario: 手动输入服务器连接

- **WHEN** 用户手动输入 Windows 服务器的 IP 与端口并发起连接
- **THEN** 系统 SHALL 以该地址建立 LAN 连接，并允许将其纳入与自动发现列表相同的一键连接流程

#### Scenario: 定向探测手动输入的地址

- **WHEN** 用户手动输入 IP（与端口）并触发搜索
- **THEN** 系统 SHALL 向该 IP 单播发送探测，并按应答结果将该服务器纳入列表

