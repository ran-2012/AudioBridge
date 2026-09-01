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

整体链路（**统一拓扑：Windows 作 TCP 服务器，Android 作 TCP 客户端**）如下：

1. Windows 端启动 TCP 服务器监听端口（**同时监听 `0.0.0.0:5000` 与 `0.0.0.0:<LanListenPort>`，与连接模式无关**，任一端口接入即建立连接）
2. ADB 模式下 Windows 端执行 `adb reverse`，将设备端口转发到本机；LAN 模式下 Android 端通过发现或手动输入获得 Windows 地址
3. Android 端作为 TCP 客户端主动连接 Windows
4. 连接成功后 Windows 端发送会话初始化信息
5. Windows 端持续发送音频帧
6. Android 端接收后根据参数初始化 `AudioTrack` 并播放
7. Android 端可在同一连接上发送音量目录请求与控制命令
8. Windows 端回传主音量、应用会话信息、图标与控制回执

### 2.3 拓扑说明

逻辑拓扑（统一架构）：

- Windows 应用：TCP Server（监听端口）
- ADB：reverse 转发通道（仅 ADB 模式）
- Android 应用：TCP Client（主动连接）

推荐端口：

- 无论 ADB / LAN 模式，Windows 均同时监听 `0.0.0.0:5000` 与 `0.0.0.0:<LanListenPort>`（默认 `6000`）
- ADB 模式：额外执行 `adb reverse tcp:5000 tcp:5000`，Android 经 USB 连接设备本地 `5000`
- LAN 模式：Android 经 Wi-Fi 连接 `WindowsIP:6000`（发现或手动输入）
- 发现端口（UDP）：`9000`

ADB reverse 命令（ADB 模式）：

- `adb reverse tcp:5000 tcp:5000`

含义：

- 设备上的 `127.0.0.1:5000` 被转发到 Windows 本机的 `5000` 端口
- Android 应用连接设备本地 `127.0.0.1:5000`，数据经 USB 转发到 Windows 服务器

注意：`adb reverse` 在设备拔插、休眠后可能失效，Windows 端需在设备上线或电源恢复时重建（见第 9 章）。

## 3. 连接流程

### 3.1 Windows 端流程

ADB 模式：

1. 检查 `adb.exe` 是否存在
2. 执行 `adb devices` 并选择目标设备
3. 执行 `adb reverse tcp:5000 tcp:5000`
4. 启动 TCP 服务器监听 `0.0.0.0:5000` 与 `0.0.0.0:<LanListenPort>`
5. 等待 Android 客户端接入
6. 客户端接入后发送会话头
7. 启动音频采集并发送音频帧

LAN 模式：

1. 启动 TCP 服务器监听 `0.0.0.0:5000` 与 `0.0.0.0:<LanListenPort>`
2. 启动 UDP 发现应答服务（端口 `9000`，响应 Android 探测广播）
3. 等待 Android 客户端接入
4. 客户端接入后发送会话头
5. 启动音频采集并发送音频帧

说明：无论 ADB / LAN 模式，Windows 均同时监听 `5000` 与 `<LanListenPort>` 两个端口，Android 无论经 USB reverse、局域网发现还是手动输入均可接入；断开后服务器自动恢复监听等待新接入（单活跃客户端，新连接替换旧连接）。

### 3.2 Android 端流程

1. 启动前台服务
2. 解析连接目标（ADB 模式默认 `127.0.0.1:5000`；LAN 模式为发现列表或手动输入的 `WindowsIP:6000`）
3. 以 TCP 客户端身份主动连接 Windows 服务器
4. 连接成功后接收会话头
5. 解析音频参数
6. 初始化 `AudioTrack`
7. 循环接收音频帧并播放
8. 连接断开后按重连策略自动重连

### 3.3 连接时序

#### 初始化阶段

- Windows 端先进入监听状态
- Android 端建立连接后等待 Windows 发送协议头
- Windows 端接受连接后立即发送会话头（`SessionInit`）

#### 传输阶段

- Windows 端按固定 Buffer 大小切帧
- 逐帧发送音频数据
- Android 端按帧接收并写入播放缓冲区

#### 断开阶段

- 任一方主动关闭 Socket
- Windows 端回到监听状态等待新客户端接入（单活跃客户端，新连接替换旧连接）
- Android 端停止播放并按重连策略自动重连

### 3.4 LAN 模式与设备发现

LAN 模式采用 **Android 主动探测（pull）+ Windows 被动应答**：

- **探测（Android → 广播 `255.255.255.255:9000`）**：
  ```json
  {"t":"winAudioBridgeProbe","app":"dev.ran.audiobridge","ver":1}
  ```
- **应答（Windows → 单播到探测来源）**：
  ```json
  {"t":"winAudioBridgeAnnounce","name":"<主机名>","host":"<本机IPv4>","port":6000,"ver":1}
  ```

行为约定：

- Windows 端绑定 `0.0.0.0:9000` 监听 UDP，收到 `winAudioBridgeProbe` 后向来源单播回送公告
- Android 端发送探测广播，接收应答并按「名称 + IP」去重维护服务器列表；`15s` 未收到应答则标记离线
- 广播在 AP 隔离或跨网段时不可达，Android 端保留手动输入 IP 与端口的回退入口

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
- `0x03`：心跳消息 `Heartbeat`（Windows → Android 保活探测）
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
- `0x18`：心跳回执 `HeartbeatAck`（Android → Windows 存活确认）
- `0x19`：Android 播放状态 `AndroidPlaybackStatus`（Android → Windows 播放端存活与延迟状态）
- `0x1A`：Android 播放状态回执 `AndroidPlaybackStatusAck`（Windows → Android 状态接收确认）
- `0x1B`：延迟探测 `LatencyProbe`（双向，payload 为 8 字节毫秒时间戳）
- `0x1C`：延迟探测回执 `LatencyProbeAck`（回显收到的毫秒时间戳）

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

### 4.6 心跳消息 `Heartbeat` 与心跳回执 `HeartbeatAck`

用于应用层链路存活检测，解决 `adb forward` 转发链路的“假存活”问题：当 Android 接收端断开时，手机侧 socket 关闭，但 Windows ↔ 本地 adb 进程之间的 TCP 段仍存活，Windows 的写入持续“成功”、`TcpClient.Connected` 仍为 true，导致 Windows 无法感知对端已断开。

机制：

- Windows 端在推流期间每 `5` 秒发送一次 `Heartbeat`（`0x03`，空负载）。
- Android 端每收到一次 `Heartbeat`，立即回送一条 `HeartbeatAck`（`0x18`，空负载）。
- Windows 端维护“最近一次收到对端任意消息的时间”（含 `HeartbeatAck` 与音量控制消息）。若超过存活超时阈值（默认 `15` 秒，约 3× 心跳周期）仍未收到任何对端消息，则判定链路死亡，停止采集、断开传输，并按 `EnableAutoReconnect` 设置触发自动重连。

`Heartbeat` 与 `HeartbeatAck` 负载均为空（`PayloadLength = 0`），仅依赖 12 字节通用包头。

用途：

- 检测长时间无数据但连接未关闭、或 adb 转发假存活的异常场景
- 后续如需统计往返延迟，可在负载中扩展 `Timestamp` 字段（当前不需要）

### 4.7 Android 播放状态 `AndroidPlaybackStatus` 与状态回执 `AndroidPlaybackStatusAck`

用于判断 Android 播放端是否真实存活，而不是只判断 Windows ↔ adb 本地 TCP 段是否可写。Android 在活动连接存在时每 `3` 秒主动上报一次播放状态，Windows 收到后立即回送状态回执。

`AndroidPlaybackStatus` 方向为 Android → Windows，JSON 负载字段如下：

| 字段 | 类型 | 说明 |
|---|---:|---|
| `sequence` | UInt32 | 播放状态包递增序号 |
| `isPlaying` | Boolean | Android 当前 `AudioTrack` 是否处于播放态 |
| `lastSequence` | UInt32 | 最近处理的音频帧序号，未收到音频时为 `0` |
| `lastAudioFrameAgeMillis` | Int64? | 距离最近音频帧的时间，单位毫秒；未知时为 `null` |
| `bufferedLatencyMillis` | Int64? | 估算播放缓冲/延迟，单位毫秒；无法可靠估算时为 `null` |
| `timestampElapsedRealtimeMillis` | Int64 | Android 单调时钟时间戳，用于日志排障 |

`AndroidPlaybackStatusAck` 方向为 Windows → Android，JSON 负载字段如下：

| 字段 | 类型 | 说明 |
|---|---:|---|
| `sequence` | UInt32 | 被确认的播放状态包序号 |
| `accepted` | Boolean | Windows 是否接受该状态包 |
| `receivedAtMillis` | Int64 | Windows 收到状态包并回执的 Unix 毫秒时间戳 |
| `echoedTimestampElapsedRealtimeMillis` | Int64? | 原样带回 Android 状态包中的 `timestampElapsedRealtimeMillis` |

Windows 端维护独立的“最近一次 Android 播放状态时间”。该时间只由 `AndroidPlaybackStatus` 刷新，不由 Windows 发送音频帧、发送心跳或发送 ACK 刷新。若当前音频链路仍处于连接/推流相关状态，且超过默认 `10` 秒未收到 Android 播放状态，Windows 判定播放端链路不健康，停止采集、断开传输，并按 `EnableAutoReconnect` 设置重建音频链路。

当 Android 连续上报 `isPlaying=false` 或 `bufferedLatencyMillis` 超过阈值时，Windows 记录异常；连续达到阈值后按链路不健康处理。单次异常不立即重连，避免因短暂调度抖动产生误判。

旧版 Android 不会上报 `AndroidPlaybackStatus`。新版 Windows 与新版 Android 应配套发布，否则新版 Windows 会在超时后按不健康链路重连。

### 4.8 音量目录请求 `VolumeCatalogRequest`

Android 端进入音量控制页后，可发送该消息请求 Windows 当前完整音量目录。

建议负载字段：

| 字段 | 类型 | 字节数 | 说明 |
|---|---:|---:|---|
| RequestId | UInt32 | 4 | 请求标识 |
| IncludeIconsInline | UInt16 | 2 | `0=否`，`1=是` |
| Reserved | UInt16 | 2 | 保留 |

### 4.9 音量目录快照 `VolumeCatalogSnapshot`

Windows 端返回完整主音量与应用会话列表。

建议负载为结构化二进制或 JSON 负载，首版建议优先使用 JSON，降低跨端调试成本。

建议包含：

- 主音量状态
- 默认输出设备信息
- 应用会话数组
- 每个会话的 `SessionId`、名称、进程信息、音量、静音、图标键、图标摘要

### 4.10 主音量设置请求 `VolumeSetMasterRequest`

Android 端用于设置 Windows 主音量和主静音。

建议字段：

| 字段 | 类型 | 字节数 | 说明 |
|---|---:|---:|---|
| RequestId | UInt32 | 4 | 请求标识 |
| Volume | Float32 | 4 | `0.0 ~ 1.0` |
| HasMute | UInt16 | 2 | 是否包含静音字段 |
| Mute | UInt16 | 2 | `0=否`，`1=是` |

### 4.11 应用音量设置请求 `VolumeSetSessionRequest`

Android 端用于设置指定应用会话的音量或静音。

建议负载包含：

- `RequestId`
- `SessionId`
- `Volume`
- `HasMute`
- `Mute`

建议 `SessionId` 使用长度前缀字符串编码，便于跨语言实现。

### 4.12 音量增量更新 `VolumeSessionDelta`

当 Windows 本地主音量或应用会话音量发生变化时，Windows 端主动推送增量更新。

建议包含：

- 更新类型：主音量 / 会话音量 / 会话新增 / 会话移除
- 目标 `SessionId`（如适用）
- 最新音量状态

### 4.13 图标请求与图标响应

若音量目录快照未内联图标，Android 可按需请求：

- `IconContentRequest`：携带 `IconKey`
- `IconContentResponse`：返回 PNG 二进制或 Base64 数据

首版如采用 JSON 快照，也可将图标响应设计为 Base64 字符串，后续再优化为二进制负载。

### 4.14 命令回执 `CommandAck`

所有音量控制命令建议都返回回执。

建议字段：

| 字段 | 类型 | 字节数 | 说明 |
|---|---:|---:|---|
| RequestId | UInt32 | 4 | 请求标识 |
| Success | UInt16 | 2 | 是否成功 |
| ErrorCode | UInt16 | 2 | 错误码 |
| PayloadLength | UInt32 | 4 | 附加状态长度 |

附加状态中建议携带服务端最新主音量或会话状态，便于 Android 立即纠正本地 UI。

### 4.15 延迟探测 `LatencyProbe` 与回执 `LatencyProbeAck`

用于测量链路往返延迟（RTT）并估算单向延迟，帮助诊断卡顿。

消息定义：

- `LatencyProbe`（`0x1B`）：payload 为 **8 字节毫秒时间戳（UInt64，Little Endian）**，发送方记录发出时刻
- `LatencyProbeAck`（`0x1C`）：payload **回显收到的 8 字节时间戳**

测量方式：

- Windows 端在推流期间每 `3s` 发送一次 `LatencyProbe`；Android 端收到后立即回送 `LatencyProbeAck`
- Windows 端计算 `RTT = 当前时间 − 探测时间戳`，单向延迟 ≈ `RTT / 2`，对最近样本做滑动平均并在界面展示「估算延迟」
- Android 端同样可发送 `LatencyProbe`，Windows 回送 `LatencyProbeAck`，Android 据此展示连接 RTT

兼容性说明：

- `LatencyProbe` 仅在 LAN 模式由 Windows 端发送（LAN 模式要求双端均为支持该消息的版本）
- Android 端 `ProtocolReader` 对未知消息类型会报错，故新增消息需双端同步升级

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

- 连接 Windows 服务器失败
- 收到非法协议包
- `AudioTrack` 初始化失败
- 播放过程中连接断开

处理建议：

- 关闭当前连接
- 释放播放器资源
- 按重连策略自动重连
- 输出可诊断日志

## 9. 超时与重连策略

### 9.1 超时建议

建议设置以下超时：

- Windows 端连接超时：`3s ~ 5s`
- Socket 读取超时：可选
- 长时间无音频数据：`5s` 以上记录告警

### 9.2 重连策略

统一拓扑下，Windows 作为服务器无法主动连接设备，因此重连责任在 Android 端，Windows 负责「服务器 + reverse 就绪」：

Windows 端建议：

- 服务器始终监听并等待客户端接入（单活跃客户端，新连接替换旧连接）
- ADB 模式下设备上线或电源恢复时重建 `adb reverse`
- 客户端断开后回到监听状态，无需主动重连

Android 端建议：

- 启动即尝试连接，失败按固定间隔（如 `3s`）自动重连
- 断线后自动重连直至成功或用户停止
- 支持连接目标切换（USB reverse / 发现列表 / 手动输入）

## 10. 日志与调试建议

### 10.1 Windows 端日志

建议记录：

- ADB reverse 命令执行结果
- 服务器监听启动与客户端接入
- `SessionInit` 关键参数
- 音频帧发送速率
- 估算延迟（RTT/2）样本
- 连接断开与客户端替换
- 异常堆栈

### 10.2 Android 端日志

建议记录：

- 连接目标与连接尝试结果
- 重连次数与间隔
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

- 支持执行 `adb reverse`
- 支持建立 TCP Server（监听 `5000` 与 `<LanListenPort>`）
- 支持发送 `SessionInit`
- 支持发送 `PCM16` 音频帧

### Android 端

- 支持 TCP Client 主动连接（USB reverse / 局域网发现 / 手动输入）
- 支持解析 `SessionInit`
- 支持解析 `AudioFrame`
- 支持 `AudioTrack` 播放 `PCM16 / 48000 / Stereo`

### 联调验收标准

- Android 成功连接 Windows 并建立会话
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
