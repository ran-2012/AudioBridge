# WinAudioBridge Windows 与 Android 通信技术文档

## 1. 文档目标

本文档定义 WinAudioBridge 中 Windows 端与 Android 端之间的通信方式、连接流程、数据协议、异常处理与扩展方案，用于指导两端联调与后续版本迭代。

本文聚焦以下范围：

- Windows 端通过 ADB 与 Android 端建立通信链路
- Windows 端向 Android 端传输音频流
- Android 端向 Windows 端发送控制命令
- Windows 端向 Android 端回传音量目录、应用名称、图标与状态变化
- 双端对音频格式进行协商
- 双端处理连接中断、参数变更与重连

## 2. 通信总体设计

### 2.1 设计目标

通信设计需满足以下要求：

- 基于 USB + ADB 实现稳定传输
- 首版优先低复杂度，快速打通链路
- 支持 PCM 直传
- 为后续 Opus 压缩、状态回传、心跳检测预留扩展能力
- 便于调试与抓包分析

### 2.2 通信链路

整体链路如下：

1. Windows 端检测 Android 设备与 ADB 可用性
2. Windows 端执行 ADB 端口转发
3. Android 端启动 `ServerSocket` 监听固定端口
4. Windows 端作为客户端连接本机转发端口
5. Windows 端发送会话初始化信息
6. Windows 端持续发送音频帧
7. Android 端接收后根据参数初始化 `AudioTrack` 并播放
8. Android 端可在同一连接上发送音量目录请求与控制命令
9. Windows 端回传主音量、应用会话信息、图标与控制回执

### 2.3 拓扑说明

逻辑拓扑：

- Windows 应用：TCP Client
- ADB：端口转发通道
- Android 应用：TCP Server

推荐端口：

- 默认端口：`5000`

ADB 转发命令：

- `adb forward tcp:5000 tcp:5000`

含义：

- Windows 连接 `127.0.0.1:5000`
- 数据通过 ADB 转发到 Android 设备的 `5000` 端口

## 3. 连接流程

### 3.1 Windows 端流程

1. 检查 `adb.exe` 是否存在
2. 执行 `adb devices`
3. 校验是否存在已授权设备
4. 执行端口转发命令
5. 建立 `TcpClient(127.0.0.1, 5000)`
6. 发送会话头
7. 启动音频采集并发送音频帧

### 3.2 Android 端流程

1. 启动服务
2. 创建 `ServerSocket(5000)`
3. 阻塞等待连接
4. 接收会话头
5. 解析音频参数
6. 初始化 `AudioTrack`
7. 循环接收音频帧并播放
8. 连接断开后释放资源并继续监听

### 3.3 连接时序

#### 初始化阶段

- Android 端先进入监听状态
- Windows 端建立 ADB 转发后发起连接
- 连接成功后立即发送协议头

#### 传输阶段

- Windows 端按固定 Buffer 大小切帧
- 逐帧发送音频数据
- Android 端按帧接收并写入播放缓冲区

#### 断开阶段

- Windows 端停止推流时主动关闭 Socket
- Android 端检测到流结束后停止播放并等待下次连接

## 4. 通信协议设计

### 4.1 协议原则

协议采用“固定头 + 可变负载”的轻量结构，要求：

- 简单明确
- 易于跨语言实现
- 字段足够支撑音频播放初始化
- 兼容后续协议升级

统一约定：

- 字节序：`Little Endian`
- 传输层：TCP
- 编码控制：由会话头声明

### 4.2 消息类型

建议定义以下消息类型：

- `0x01`：会话初始化消息 `SessionInit`
- `0x02`：音频帧消息 `AudioFrame`
- `0x03`：心跳消息 `Heartbeat`（预留）
- `0x04`：状态消息 `Status`（预留）
- `0x05`：停止消息 `Stop`（预留）
- `0x10`：音量目录请求 `VolumeCatalogRequest`
- `0x11`：音量目录快照 `VolumeCatalogSnapshot`
- `0x12`：主音量设置请求 `VolumeSetMasterRequest`
- `0x13`：应用音量设置请求 `VolumeSetSessionRequest`
- `0x14`：音量增量更新 `VolumeSessionDelta`
- `0x15`：图标请求 `IconContentRequest`
- `0x16`：图标响应 `IconContentResponse`
- `0x17`：命令回执 `CommandAck`

MVP 阶段至少实现：

- `SessionInit`
- `AudioFrame`

音量控制增强阶段至少实现：

- `VolumeCatalogRequest`
- `VolumeCatalogSnapshot`
- `VolumeSetMasterRequest`
- `VolumeSetSessionRequest`
- `CommandAck`

### 4.3 通用包头

每个消息前添加统一头部：

| 字段 | 类型 | 字节数 | 说明 |
|---|---:|---:|---|
| Magic | UInt32 | 4 | 固定值，用于校验包起始，建议 `0x57414231`，表示 `WAB1` |
| Version | UInt16 | 2 | 协议版本，首版为 `1` |
| MessageType | UInt16 | 2 | 消息类型 |
| PayloadLength | UInt32 | 4 | 负载长度 |

固定头长度：`12` 字节。

### 4.4 会话初始化消息 `SessionInit`

在连接建立后，Windows 端必须首先发送 `SessionInit`。

#### 负载字段

| 字段 | 类型 | 字节数 | 说明 |
|---|---:|---:|---|
| Encoding | UInt16 | 2 | `1=PCM16`，`2=Float32`，`3=Opus` |
| SampleRate | UInt32 | 4 | 采样率，如 `44100`、`48000` |
| Channels | UInt16 | 2 | `1` 或 `2` |
| BitsPerSample | UInt16 | 2 | PCM16 为 `16`，Float32 为 `32` |
| BufferMilliseconds | UInt32 | 4 | 单帧目标时长，单位毫秒 |
| Reserved | UInt32 | 4 | 保留字段，当前填 `0` |

负载长度：`18` 字节。

#### 作用

Android 端收到后需要：

- 校验参数合法性
- 选择对应 `AudioTrack` 输出格式
- 计算每帧期望大小
- 初始化播放缓冲区

### 4.5 音频帧消息 `AudioFrame`

音频帧由固定头 + 音频帧负载组成。

#### 负载字段

| 字段 | 类型 | 字节数 | 说明 |
|---|---:|---:|---|
| Sequence | UInt32 | 4 | 递增序号，用于检测丢帧与日志定位 |
| Timestamp | Int64 | 8 | 发送时间戳，单位毫秒 |
| AudioData | Byte[] | N | 音频数据 |

音频帧负载长度为：`12 + AudioData.Length`

#### 说明

- `Sequence` 从 `1` 开始递增
- `Timestamp` 推荐使用 Unix 毫秒时间戳
- `AudioData` 为当前帧的编码后数据
- MVP 阶段一般直接存放 PCM 原始字节流

### 4.6 心跳消息 `Heartbeat`（预留）

当后续增加连接保活与状态监控时，可增加心跳包。

建议字段：

| 字段 | 类型 | 字节数 | 说明 |
|---|---:|---:|---|
| Timestamp | Int64 | 8 | 当前发送时间 |

用途：

- 检测长时间无数据但连接未关闭的异常场景
- 统计往返延迟（若后续支持回包）

### 4.7 音量目录请求 `VolumeCatalogRequest`

Android 端进入音量控制页后，可发送该消息请求 Windows 当前完整音量目录。

建议负载字段：

| 字段 | 类型 | 字节数 | 说明 |
|---|---:|---:|---|
| RequestId | UInt32 | 4 | 请求标识 |
| IncludeIconsInline | UInt16 | 2 | `0=否`，`1=是` |
| Reserved | UInt16 | 2 | 保留 |

### 4.8 音量目录快照 `VolumeCatalogSnapshot`

Windows 端返回完整主音量与应用会话列表。

建议负载为结构化二进制或 JSON 负载，首版建议优先使用 JSON，降低跨端调试成本。

建议包含：

- 主音量状态
- 默认输出设备信息
- 应用会话数组
- 每个会话的 `SessionId`、名称、进程信息、音量、静音、图标键、图标摘要

### 4.9 主音量设置请求 `VolumeSetMasterRequest`

Android 端用于设置 Windows 主音量和主静音。

建议字段：

| 字段 | 类型 | 字节数 | 说明 |
|---|---:|---:|---|
| RequestId | UInt32 | 4 | 请求标识 |
| Volume | Float32 | 4 | `0.0 ~ 1.0` |
| HasMute | UInt16 | 2 | 是否包含静音字段 |
| Mute | UInt16 | 2 | `0=否`，`1=是` |

### 4.10 应用音量设置请求 `VolumeSetSessionRequest`

Android 端用于设置指定应用会话的音量或静音。

建议负载包含：

- `RequestId`
- `SessionId`
- `Volume`
- `HasMute`
- `Mute`

建议 `SessionId` 使用长度前缀字符串编码，便于跨语言实现。

### 4.11 音量增量更新 `VolumeSessionDelta`

当 Windows 本地主音量或应用会话音量发生变化时，Windows 端主动推送增量更新。

建议包含：

- 更新类型：主音量 / 会话音量 / 会话新增 / 会话移除
- 目标 `SessionId`（如适用）
- 最新音量状态

### 4.12 图标请求与图标响应

若音量目录快照未内联图标，Android 可按需请求：

- `IconContentRequest`：携带 `IconKey`
- `IconContentResponse`：返回 PNG 二进制或 Base64 数据

首版如采用 JSON 快照，也可将图标响应设计为 Base64 字符串，后续再优化为二进制负载。

### 4.13 命令回执 `CommandAck`

所有音量控制命令建议都返回回执。

建议字段：

| 字段 | 类型 | 字节数 | 说明 |
|---|---:|---:|---|
| RequestId | UInt32 | 4 | 请求标识 |
| Success | UInt16 | 2 | 是否成功 |
| ErrorCode | UInt16 | 2 | 错误码 |
| PayloadLength | UInt32 | 4 | 附加状态长度 |

附加状态中建议携带服务端最新主音量或会话状态，便于 Android 立即纠正本地 UI。

## 5. 音频参数约定

### 5.1 推荐默认值

为兼顾通用性与实现复杂度，建议默认参数如下：

- 编码：`PCM16`
- 采样率：`48000`
- 声道数：`2`
- Buffer 大小：`20ms` 或 `40ms`

### 5.2 帧大小计算

PCM 情况下，每帧字节数计算如下：

$$
FrameBytes = SampleRate \times Channels \times (BitsPerSample / 8) \times BufferMilliseconds / 1000
$$

例如：

- 采样率：`48000`
- 声道：`2`
- 位深：`16`
- Buffer：`20ms`

则：

$$
48000 \times 2 \times 2 \times 20 / 1000 = 3840\ 
bytes
$$

### 5.3 Android 端格式映射

建议映射规则如下：

- `PCM16` -> `AudioFormat.ENCODING_PCM_16BIT`
- `Float32` -> `AudioFormat.ENCODING_PCM_FLOAT`
- 声道 `1` -> `CHANNEL_OUT_MONO`
- 声道 `2` -> `CHANNEL_OUT_STEREO`

若设备不支持 `PCM_FLOAT`，Android 端可直接拒绝建立播放链路，或要求 Windows 端改为 `PCM16`。

## 6. Windows 端实现要求

### 6.1 模块划分

建议拆分如下：

- `AdbService`
- `SocketClientService`
- `ProtocolWriter`
- `AudioCaptureService`
- `StreamingCoordinator`
- `SettingsService`

其中 `AdbService` 建议优先基于 **AdvancedSharpAdbClient** 实现以下能力：

- 启动或连接本机 ADB Server
- 枚举当前已连接 Android 设备
- 检测目标 Android 音频应用是否正在运行
- 执行 Forward / Reverse Forward
- 执行必要的 Shell 命令

音量控制增强阶段建议补充：

- `WindowsVolumeService`
- `VolumeSessionTracker`
- `VolumeIconService`
- `ControlProtocolService`

### 6.2 发送逻辑要求

Windows 端发送时需要满足：

- 所有协议字段使用小端写入
- 先发 `SessionInit`，后发 `AudioFrame`
- 单线程或串行队列发送，避免乱序
- 音频采集线程与网络发送线程解耦
- 当发送阻塞时，避免阻塞 WASAPI 回调线程过久

若复用同一连接承载控制消息，还需要满足：

- 控制消息发送不得被大音频帧长期饿死
- 回执消息应优先发送
- 音量快照与增量更新要有顺序保证

### 6.3 推荐发送流程

1. 加载设置
2. 启动 ADB 转发
3. 建立 TCP 连接
4. 写入 `SessionInit`
5. 启动采集
6. 将音频块放入发送队列
7. 后台发送线程封包并写入 `NetworkStream`

### 6.4 参数变更处理

当用户修改以下参数时，需要重建通信会话：

- 编码
- 采样率
- 声道数
- Buffer 大小

建议流程：

1. 停止采集
2. 发送停止消息或直接关闭连接
3. 断开当前 Socket
4. 更新配置
5. 重新建立连接
6. 重新发送 `SessionInit`
7. 重新开始推流

## 7. Android 端实现要求

### 7.1 模块划分

建议拆分如下：

- `SocketServerService`
- `ProtocolReader`
- `AudioPlayerService`
- `ConnectionCoordinator`
- `PlaybackBufferManager`
- `VolumeControlRepository`
- `IconCacheManager`
- `VolumeControlViewModel`

### 7.2 接收逻辑要求

Android 端接收时需要满足：

- 能够循环读取指定长度字节，不能假设一次 `read()` 返回完整包
- 先读取固定头，再读取负载
- 校验 `Magic`、`Version`、`PayloadLength`
- 严格按 `MessageType` 分发处理
- 在 `SessionInit` 成功前拒绝处理 `AudioFrame`

对于音量控制增强阶段，还需要满足：

- 能处理 Windows 主动推送的目录快照与增量更新
- 能根据 `RequestId` 对齐命令回执
- 图标解码失败时不影响其他会话显示

### 7.3 播放逻辑要求

- 收到 `SessionInit` 后创建或重建 `AudioTrack`
- 收到 `AudioFrame` 后提取 `AudioData`
- 按顺序写入 `AudioTrack`
- 若发现 `Sequence` 不连续，记录日志但不中断播放
- 若 `AudioTrack.write()` 返回异常，立即释放并终止当前连接

## 8. 异常处理

### 8.1 Windows 端异常

#### ADB 异常

- `adb` 不存在
- 设备未连接
- 设备未授权
- 端口转发失败

处理建议：

- UI 明确提示错误
- 不启动采集
- 允许用户手动重试

#### Socket 异常

- 连接失败
- 写入失败
- 连接中断

处理建议：

- 停止采集
- 清空发送队列
- 自动或手动重连

### 8.2 Android 端异常

- `ServerSocket` 启动失败
- 收到非法协议包
- `AudioTrack` 初始化失败
- 播放过程中连接断开

处理建议：

- 关闭当前连接
- 释放播放器资源
- 回到监听状态
- 输出可诊断日志

## 9. 超时与重连策略

### 9.1 超时建议

建议设置以下超时：

- Windows 端连接超时：`3s ~ 5s`
- Socket 读取超时：可选
- 长时间无音频数据：`5s` 以上记录告警

### 9.2 重连策略

Windows 端建议：

- 首次失败立即提示
- 推流中断后可自动尝试重连 `1~3` 次
- 超过次数后进入人工恢复模式

Android 端建议：

- 单次连接结束后自动回到监听状态
- 不因上次异常退出整个服务

## 10. 日志与调试建议

### 10.1 Windows 端日志

建议记录：

- ADB 命令执行结果
- Socket 连接建立与断开
- `SessionInit` 关键参数
- 音频帧发送速率
- 重连次数
- 异常堆栈

### 10.2 Android 端日志

建议记录：

- 监听端口启动成功
- 客户端连接来源
- 会话参数
- `AudioTrack` 初始化结果
- 连续丢帧或乱序情况
- 连接中断原因

## 11. 安全与边界约束

当前通信设计基于本地 ADB 转发，默认具备以下边界：

- 通信链路不直接暴露在局域网
- 数据仅在本机与 USB 连接设备之间流动
- 不涉及公网鉴权

但仍需注意：

- Android 端监听的是设备本地端口，不应额外暴露无关服务
- 对协议头与负载长度必须进行校验，防止异常数据导致崩溃
- 对超大 `PayloadLength` 必须设置上限

建议限制：

- 单帧最大负载不超过 `1MB`

## 12. 后续扩展方向

### 12.1 状态回传

未来可增加 Android -> Windows 反向状态消息，用于回传：

- 当前播放状态
- 播放缓冲深度
- 初始化失败原因
- 设备实际支持格式

### 12.2 Opus 压缩

扩展方式：

- `SessionInit.Encoding = 3`
- `AudioFrame.AudioData` 改为 Opus 帧
- Android 端在写入 `AudioTrack` 前先解码

### 12.3 双向控制

未来可支持：

- Windows 端远程控制 Android 播放启停
- Android 端请求 Windows 端重发会话参数
- 动态切换 Buffer 配置

## 13. MVP 最小实现要求

首版联调最小能力建议如下：

### Windows 端

- 支持执行 `adb forward`
- 支持建立 TCP Client
- 支持发送 `SessionInit`
- 支持发送 `PCM16` 音频帧

### Android 端

- 支持 `ServerSocket(5000)`
- 支持解析 `SessionInit`
- 支持解析 `AudioFrame`
- 支持 `AudioTrack` 播放 `PCM16 / 48000 / Stereo`

### 联调验收标准

- Windows 成功连接 Android
- Android 成功收到并解析会话头
- Android 成功播放来自 Windows 的系统音频
- 连续播放 `30` 分钟无明显断流

## 14. 结论

WinAudioBridge 的 Windows 与 Android 通信建议采用“**ADB 端口转发 + TCP 自定义轻量协议**”方案。

该方案具备以下优点：

- 实现简单
- 调试方便
- 不需要自研 USB 通信协议
- 可平滑扩展到 Opus、心跳、状态回传等能力

因此，推荐以本文协议作为 Windows 与 Android 端联调基线，并在 MVP 完成后逐步扩展压缩、保活与状态同步能力。
