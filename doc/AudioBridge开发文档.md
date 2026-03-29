# AudioBridge Android 端开发文档

## 1. 文档目标

本文档用于指导 Android 项目 AudioBridge 的开发实现，重点覆盖以下目标：

- 作为 WinAudioBridge 的 Android 接收端，通过 ADB 转发接收来自 Windows 端的音频流
- 解析 Windows 端发送的 `SessionInit` 与 `AudioFrame` 协议消息
- 使用 `AudioTrack` 在 Android 端播放 PCM 音频
- 支持音量调节
- 支持后台音频播放
- 为后续状态回传、Opus 解码、自动重连等能力预留结构

当前 Android 项目实际包名为：`dev.ran.audiobridge`

该包名需要与 Windows 端设置中的 `AndroidAppPackageName` 保持一致，用于 Windows 端识别目标应用是否正在运行。

## 2. 项目现状

当前 Android 工程为标准 Compose Application 模板，关键信息如下：

- 项目目录：`AudioBridge/`
- `applicationId`：`dev.ran.audiobridge`
- `namespace`：`dev.ran.audiobridge`
- `minSdk`：`31`
- `targetSdk`：`36`
- UI 技术栈：Jetpack Compose

当前状态适合在此基础上继续补充：

- 后台播放 Service
- Socket 接收模块
- 协议解析模块
- `AudioTrack` 播放模块
- 音量控制模块

## 3. 核心功能范围

本阶段 Android 端的主要功能为：

### 3.1 音频接收播放

- 启动本地 `ServerSocket`
- 接收 Windows 端通过 ADB 转发过来的 TCP 连接
- 解析协议头与消息体
- 根据 `SessionInit` 初始化 `AudioTrack`
- 根据 `AudioFrame` 持续播放音频

### 3.2 音量调节

应用内需要支持音量调节，建议分为两层：

- **应用内播放增益**：控制 `AudioTrack` 输出音量
- **系统媒体音量联动**：可选，调用 `AudioManager` 调整媒体流音量

首版建议优先实现：

- 应用内独立音量滑块
- 将滑块值映射到 `AudioTrack.setVolume()` 或等效方式
- 音量值持久化保存

### 3.3 后台音频播放

由于该应用核心能力是持续接收并播放外部音频流，因此必须支持后台运行。

建议采用：

- **Foreground Service（前台服务）** 承载接收与播放逻辑
- 常驻通知显示当前连接状态、播放状态
- Activity 仅负责配置、控制和状态展示

## 4. 与 Windows 端的通信约定

Android 端必须遵循现有通信文档：

- 文档参考：[doc/Windows-Android通信技术文档.md](doc/Windows-Android%E9%80%9A%E4%BF%A1%E6%8A%80%E6%9C%AF%E6%96%87%E6%A1%A3.md)

### 4.1 链路模型

- Android：`ServerSocket` 监听 `5000`
- Windows：通过 `adb forward tcp:5000 tcp:5000` 后连接 `127.0.0.1:5000`
- Android 收到连接后解析协议并播放

### 4.2 协议消息

MVP 阶段必须支持：

- `SessionInit` (`0x01`)
- `AudioFrame` (`0x02`)

### 4.3 协议固定头

每个包固定头长度为 `12` 字节：

| 字段 | 类型 | 字节数 | 说明 |
|---|---:|---:|---|
| Magic | UInt32 | 4 | 固定值 `0x57414231` |
| Version | UInt16 | 2 | 协议版本，当前 `1` |
| MessageType | UInt16 | 2 | 消息类型 |
| PayloadLength | UInt32 | 4 | 负载长度 |

Android 端必须校验：

- `Magic` 是否正确
- `Version` 是否支持
- `PayloadLength` 是否超限

建议单帧负载上限：

- `1 MB`

## 5. Android 端架构设计

建议采用“**UI 层 + 前台服务层 + 播放内核层**”的结构。

### 5.1 模块划分

#### 1）UI 层

负责：

- 展示连接状态
- 展示当前播放参数
- 控制启动/停止接收服务
- 控制音量滑块
- 展示后台播放状态

建议组件：

- `MainActivity`
- `MainViewModel`
- Compose 页面

#### 2）前台服务层

负责：

- 生命周期管理
- 启动 `ServerSocket`
- 接收 Socket 连接
- 持有播放控制器
- 持续运行于后台
- 通知栏状态更新

建议组件：

- `AudioBridgeService`
- `NotificationController`

#### 3）通信层

负责：

- 接收 TCP 数据
- 按协议读取完整包
- 解析 `SessionInit`
- 解析 `AudioFrame`
- 把解析结果交给播放层

建议组件：

- `SocketServerManager`
- `ProtocolReader`
- `PacketModels`

#### 4）播放层

负责：

- 创建和销毁 `AudioTrack`
- 维护当前会话参数
- 写入音频帧
- 控制音量
- 缓冲管理
- 异常恢复

建议组件：

- `AudioPlaybackManager`
- `PlaybackVolumeController`

#### 5）状态层

负责：

- 维护服务状态、连接状态、播放状态
- 向 UI 暴露状态流
- 向通知栏暴露摘要状态

建议组件：

- `PlaybackStateRepository`
- `StateFlow` / `MutableStateFlow`

## 6. 后台播放设计

### 6.1 为什么必须使用前台服务

如果只在 `Activity` 中接收音频：

- 应用退到后台后可能被系统挂起
- 网络接收和播放可能被回收
- 长时间播放体验不稳定

因此建议：

- 将接收与播放逻辑放入 `ForegroundService`
- 在服务启动后立刻调用 `startForeground()`
- 持续显示媒体播放状态通知

### 6.2 通知栏要求

通知建议包含：

- 当前状态：未连接 / 已连接 / 播放中 / 异常
- 当前设备来源：Windows ADB 音频桥
- 当前采样率、声道、编码（可选）
- 快捷动作：停止播放

### 6.3 Manifest 要求

建议在 `AndroidManifest.xml` 中补充：

- `FOREGROUND_SERVICE`
- Android 14+ 场景下所需的前台服务类型声明
- 服务注册

建议为 Service 设置：

- `android:exported="false"`
- 若服务仅供应用内部使用，不暴露给外部应用

### 6.4 Activity 与 Service 的职责边界

- `MainActivity`：只负责显示与控制
- `AudioBridgeService`：负责真正的网络接收与音频播放
- UI 退出后，Service 仍可继续后台播放

## 7. 音量调节设计

### 7.1 功能目标

需要支持：

- 用户在应用内调整播放音量
- 音量修改即时生效
- 音量值持久化
- 后台播放时继续保持当前音量

### 7.2 推荐方案

建议采用应用内音量倍率：

- UI 提供 `0.0 ~ 1.0` 或 `0 ~ 100` 的滑块
- 转换为 `Float` 音量值
- 写入播放层，由播放层控制 `AudioTrack` 音量

优先建议：

- 首版实现应用内音量控制
- 不直接改系统媒体音量，避免影响其他应用

### 7.3 音量控制位置

建议将音量控制统一收敛在：

- `AudioPlaybackManager`

由其负责：

- 维护当前音量值
- 在 `AudioTrack` 初始化后立即应用音量
- 音量变更时实时调用设置方法

### 7.4 状态持久化

建议使用：

- `DataStore`

建议保存：

- `playback_volume`
- `auto_start_service`（后续可选）
- `last_buffer_size`（后续可选）

## 8. 播放实现设计

### 8.1 `SessionInit` 处理

收到 `SessionInit` 后，需要：

1. 解析编码类型
2. 解析采样率
3. 解析声道数
4. 解析位深
5. 解析 Buffer 大小
6. 根据参数创建或重建 `AudioTrack`

### 8.2 编码支持范围

首版建议优先支持：

- `PCM16`

可预留：

- `Float32`
- `Opus`

如果当前收到的编码 Android 端暂不支持：

- 记录日志
- 拒绝当前会话或停止当前播放

### 8.3 `AudioTrack` 映射关系

| 协议编码 | Android 编码 |
|---|---|
| `PCM16` | `AudioFormat.ENCODING_PCM_16BIT` |
| `Float32` | `AudioFormat.ENCODING_PCM_FLOAT` |

| 声道数 | Android 声道配置 |
|---|---|
| `1` | `CHANNEL_OUT_MONO` |
| `2` | `CHANNEL_OUT_STEREO` |

### 8.4 Buffer 建议

建议：

- `AudioTrack` 最小缓冲区基于 `getMinBufferSize()` 计算
- 最终缓冲区大小取：
  - 协议帧大小
  - 系统最小缓冲区
  - 额外安全余量

### 8.5 音频写入策略

建议：

- 在接收线程外单独维护播放写入逻辑
- 保证 `AudioTrack.write()` 的调用串行
- 若检测到连接中断，立即停止写入并释放播放器

## 9. 通信与线程模型

### 9.1 建议线程划分

- `Service` 主线程：生命周期管理、状态更新
- Socket 接收线程：阻塞读取 `ServerSocket` / `InputStream`
- 播放线程：处理 `AudioTrack.write()`
- UI 线程：Compose 状态更新

### 9.2 接收循环要求

读取包时必须：

- 先读取固定 `12` 字节头部
- 再读取指定长度负载
- 不得假设一次 `read()` 就能读满
- 必须实现 `readFully()` 风格逻辑

### 9.3 断线处理

当 Windows 端断开连接时：

- 停止当前会话
- 停止或重置 `AudioTrack`
- 更新状态为“等待连接”
- `ServerSocket` 回到继续监听状态

## 10. UI 建议

建议主界面至少包含：

- 服务状态
- Socket 连接状态
- 当前播放状态
- 当前采样率/声道/编码
- 当前音量
- 音量滑块
- 启动服务按钮
- 停止服务按钮
- 后台播放说明

### 10.1 推荐页面结构

- 顶部：连接状态卡片
- 中部：当前会话参数
- 中部：音量控制滑块
- 底部：启动 / 停止按钮
- 底部：调试日志或最近错误信息

## 11. Android 端建议类设计

建议至少包含以下类：

- `MainActivity`
- `MainViewModel`
- `AudioBridgeService`
- `SocketServerManager`
- `ProtocolReader`
- `AudioPlaybackManager`
- `PlaybackStateRepository`
- `NotificationController`
- `VolumePreferencesRepository`

## 12. 开发顺序建议

### 第 1 步：补齐 Service 基础设施

- 注册前台服务
- 实现通知渠道
- 实现启动/停止服务能力

### 第 2 步：实现 Socket 接收

- 创建 `ServerSocket(5000)`
- 接收 TCP 连接
- 读取固定头
- 校验协议包

### 第 3 步：实现 `SessionInit` 解析

- 解析参数
- 初始化 `AudioTrack`
- 更新状态到 UI

### 第 4 步：实现 `AudioFrame` 播放

- 解析音频帧
- 连续写入 `AudioTrack`
- 打通最小闭环

### 第 5 步：实现音量控制

- 增加 UI 滑块
- 接入 `AudioPlaybackManager`
- 音量持久化

### 第 6 步：完善后台播放

- 应用退后台后继续播放
- 通知栏状态更新
- 停止动作可从通知栏触发

## 13. 异常处理要求

### 13.1 协议异常

- `Magic` 不匹配
- `Version` 不支持
- `PayloadLength` 非法
- `MessageType` 未实现

处理建议：

- 记录日志
- 关闭当前连接
- 保持服务继续监听

### 13.2 播放异常

- `AudioTrack` 初始化失败
- `write()` 返回错误
- 编码与设备能力不兼容

处理建议：

- 释放播放器
- 更新 UI 状态
- 等待下一次连接

### 13.3 后台服务异常

- 服务被系统回收
- 通知创建失败
- 后台限制导致启动失败

处理建议：

- 优先确保前台服务流程正确
- 将关键错误暴露到 UI 与日志

## 14. 联调注意事项

### 14.1 与 Windows 端对齐项

必须对齐：

- 包名：`dev.ran.audiobridge`
- 端口：`5000`
- 协议版本：`1`
- 编码：首版优先 `PCM16`
- 默认采样率：`48000`
- 默认声道：`2`

### 14.2 音量与后台播放联调点

联调时重点验证：

- 前台播放正常
- 退到后台后播放不中断
- 调节音量后立即生效
- Windows 断开连接后 Android 端回到等待状态
- 再次连接后可继续播放

### 14.3 首版验收标准

首版 Android 端可验收条件：

- 能启动前台服务
- 能在后台继续运行
- 能监听 `5000` 端口
- 能解析 `SessionInit`
- 能播放 `AudioFrame` 中的 PCM 数据
- 能通过 UI 调整音量
- 能在通知栏显示播放状态

## 15. 结论

AudioBridge Android 端建议以“**前台服务 + Socket 接收 + 协议解析 + AudioTrack 播放 + 应用内音量控制**”作为首版实现路线。

该路线具备以下优点：

- 与现有 Windows 端通信协议直接兼容
- 易于实现后台播放
- 易于扩展音量控制和状态显示
- 后续可平滑扩展到 Opus、状态回传与自动重连

因此，建议 Android 端按“**前台服务 -> 协议解析 -> 音频播放 -> 音量控制 -> 后台稳定性增强**”的顺序推进开发。
