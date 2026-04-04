# AudioBridge Android 端架构介绍

本文面向后续参与本仓库开发的 agent / 开发者，重点说明 Android 端当前的模块划分、主流程、状态流转和改动注意事项。

## 1. 目标与边界

Android 端当前承担两类职责：

1. 作为接收端监听 TCP 连接，接收 Windows 端推送的音频流并播放。
2. 作为控制端展示 Windows 主音量与应用会话音量，并向 Windows 发送控制命令。

它**不负责**：

- ADB 转发建立
- Windows 音频采集
- Windows 侧音量会话枚举与实际音量修改执行

这些能力都在 Windows 端完成，Android 端只负责协议接收、状态维护、UI 展示与控制命令回传。

---

## 2. 当前技术栈

- 语言：Kotlin
- UI：Jetpack Compose + Material 3
- 状态管理：`StateFlow`
- 后台执行：Android 前台 `Service` + Kotlin Coroutines
- 本地持久化：DataStore Preferences
- 音频播放：`AudioTrack`
- 网络：原生 `ServerSocket` / `InputStream`
- JSON：`org.json`

Android 配置入口见 [app/build.gradle.kts](app/build.gradle.kts)。应用清单位于 [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml)。

---

## 3. 目录结构与职责

Android 端核心代码位于 [app/src/main/java/dev/ran/audiobridge](app/src/main/java/dev/ran/audiobridge)。

### 3.1 顶层入口

- [app/src/main/java/dev/ran/audiobridge/MainActivity.kt](app/src/main/java/dev/ran/audiobridge/MainActivity.kt)
  - Compose UI 入口
  - 持有 `MainViewModel`
  - 启动时自动尝试拉起后台服务

### 3.2 UI 层

- [app/src/main/java/dev/ran/audiobridge/ui/AudioBridgeScreen.kt](app/src/main/java/dev/ran/audiobridge/ui/AudioBridgeScreen.kt)
  - 顶层页面容器
  - 管理主页面 / 详情页面切换
  - 组装运行状态卡片、播放音量卡片、Windows 音量控制卡片

- [app/src/main/java/dev/ran/audiobridge/ui/MainPageScreen.kt](app/src/main/java/dev/ran/audiobridge/ui/MainPageScreen.kt)
  - 主界面
  - 区分手机布局与平板横屏布局
  - Windows 主音量与应用会话音量控制主要在这里展示

- [app/src/main/java/dev/ran/audiobridge/ui/DetailsPageScreen.kt](app/src/main/java/dev/ran/audiobridge/ui/DetailsPageScreen.kt)
  - 展示会话参数、运行状态、联调日志
  - 提供启动/停止服务操作

### 3.3 ViewModel 层

- [app/src/main/java/dev/ran/audiobridge/viewmodel/MainViewModel.kt](app/src/main/java/dev/ran/audiobridge/viewmodel/MainViewModel.kt)
  - UI 对外动作入口
  - 订阅 `PlaybackStateRepository.state`
  - 将 UI 操作转成 `AudioBridgeService` 的 `Intent`
  - 同步播放音量到 DataStore

当前 ViewModel 很薄，主要负责“转发动作 + 暴露状态”，业务状态仍主要收敛在 Repository 对象中。

### 3.4 Service / 运行时核心

- [app/src/main/java/dev/ran/audiobridge/service/AudioBridgeService.kt](app/src/main/java/dev/ran/audiobridge/service/AudioBridgeService.kt)
  - Android 端最核心的运行时组件
  - 以前台服务形式常驻
  - 在 `5000` 端口监听来自 Windows 的连接
  - 读取协议包并分发处理
  - 驱动 `AudioPlaybackManager` 播放音频
  - 发送 Windows 音量控制命令
  - 更新全局状态仓库 `PlaybackStateRepository`

可以把它理解为当前 Android 端的“应用服务层 + 协议调度层”。

### 3.5 Repository / 状态中心

- [app/src/main/java/dev/ran/audiobridge/repository/PlaybackStateRepository.kt](app/src/main/java/dev/ran/audiobridge/repository/PlaybackStateRepository.kt)
  - 当前全局单例状态中心
  - 维护 `PlaybackUiState`
  - 提供状态更新函数，例如：
    - 服务状态更新
    - 连接状态更新
    - 播放状态更新
    - Windows 音量目录更新
    - 命令回执处理
    - 日志追加

这是当前最重要的“单一 UI 状态出口”。UI 和 ViewModel 都依赖这里暴露的 `StateFlow`。

### 3.6 音频层

- [app/src/main/java/dev/ran/audiobridge/audio/AudioPlaybackManager.kt](app/src/main/java/dev/ran/audiobridge/audio/AudioPlaybackManager.kt)
  - 根据 `SessionInit` 配置 `AudioTrack`
  - 写入音频帧数据
  - 控制本地播放音量
  - 连接断开时释放播放资源

### 3.7 协议 / 网络层

- [app/src/main/java/dev/ran/audiobridge/network/ProtocolReader.kt](app/src/main/java/dev/ran/audiobridge/network/ProtocolReader.kt)
  - 负责读取统一二进制包头与负载
  - 将原始字节流解析成 `BridgePacket`

- [app/src/main/java/dev/ran/audiobridge/network/BridgeMessageType.kt](app/src/main/java/dev/ran/audiobridge/network/BridgeMessageType.kt)
  - 协议消息类型常量定义

- [app/src/main/java/dev/ran/audiobridge/network/WindowsVolumeJsonCodec.kt](app/src/main/java/dev/ran/audiobridge/network/WindowsVolumeJsonCodec.kt)
  - 负责 Windows 音量相关 JSON 的构造与解析
  - 包括：
    - 目录请求
    - 主音量设置请求
    - 会话音量设置请求
    - 音量快照解析
    - 音量增量解析
    - 命令回执解析

### 3.8 数据模型层

- [app/src/main/java/dev/ran/audiobridge/model/BridgePacket.kt](app/src/main/java/dev/ran/audiobridge/model/BridgePacket.kt)
  - 协议包抽象

- [app/src/main/java/dev/ran/audiobridge/model/PlaybackUiState.kt](app/src/main/java/dev/ran/audiobridge/model/PlaybackUiState.kt)
  - Compose UI 消费的统一状态模型

- [app/src/main/java/dev/ran/audiobridge/model/WindowsVolumeCatalog.kt](app/src/main/java/dev/ran/audiobridge/model/WindowsVolumeCatalog.kt)
  - Windows 主音量、应用会话、命令回执等模型定义

- [app/src/main/java/dev/ran/audiobridge/model/PlaybackSessionInfo.kt](app/src/main/java/dev/ran/audiobridge/model/PlaybackSessionInfo.kt)
  - 当前播放会话参数

### 3.9 本地数据与通知

- [app/src/main/java/dev/ran/audiobridge/data/VolumePreferencesRepository.kt](app/src/main/java/dev/ran/audiobridge/data/VolumePreferencesRepository.kt)
  - 持久化 Android 本地播放音量

- [app/src/main/java/dev/ran/audiobridge/notification/NotificationController.kt](app/src/main/java/dev/ran/audiobridge/notification/NotificationController.kt)
  - 管理前台服务通知

---

## 4. 运行时主链路

## 4.1 应用启动链路

1. `MainActivity` 创建。
2. `MainViewModel.autoStartServiceIfNeeded()` 被调用。
3. ViewModel 通过 `startForegroundService()` 拉起 `AudioBridgeService`。
4. Service 初始化通知通道、音量仓库、协议读取器、播放管理器。
5. Service 在后台监听 `5000` 端口，等待 Windows 接入。

对应入口代码：

- [app/src/main/java/dev/ran/audiobridge/MainActivity.kt](app/src/main/java/dev/ran/audiobridge/MainActivity.kt)
- [app/src/main/java/dev/ran/audiobridge/viewmodel/MainViewModel.kt](app/src/main/java/dev/ran/audiobridge/viewmodel/MainViewModel.kt)
- [app/src/main/java/dev/ran/audiobridge/service/AudioBridgeService.kt](app/src/main/java/dev/ran/audiobridge/service/AudioBridgeService.kt)

## 4.2 音频接收与播放链路

1. Windows 通过 ADB 转发后的 TCP 连接接入 Android。
2. `AudioBridgeService.handleClient()` 循环读取数据包。
3. `ProtocolReader.readPacket()` 根据包头解析消息类型。
4. 当收到 `SessionInit`：
   - `AudioPlaybackManager.configure()` 初始化 `AudioTrack`
   - `PlaybackStateRepository.updateSession()` 更新会话信息
5. 当收到 `AudioFrame`：
  - `AudioPlaybackManager.write()` 以非阻塞方式写入 PCM 数据
  - 若检测到播放积压超过本地缓存阈值，会主动丢弃旧音频并保留最新音频，避免延迟持续放大
   - `PlaybackStateRepository.updateSequence()` 更新最新序号
   - `PlaybackStateRepository.updatePlayback()` 更新播放状态
6. 当连接断开：
   - 状态回退到等待重连
   - `AudioTrack` 资源释放

当前 Android 端新增了本地“播放缓存”设置项，默认 `120ms`，范围 `40ms ~ 400ms`。缓存越小，恢复前台后的延迟越低；缓存越大，弱网络或短时抖动下更稳。

## 4.3 Windows 音量同步与控制链路

1. 用户在 UI 点击“刷新 Windows 音量”或拖动某个音量滑杆。
2. `MainViewModel` 将动作转为 `Intent` 发送给 `AudioBridgeService`。
3. `AudioBridgeService` 调用 `WindowsVolumeJsonCodec` 构造 JSON 负载。
4. Service 将 JSON 封装成统一协议包，经当前连接的 `OutputStream` 发送给 Windows。
5. Windows 返回以下消息之一：
   - `VOLUME_CATALOG_SNAPSHOT`
   - `VOLUME_SESSION_DELTA`
   - `COMMAND_ACK`
6. Android 解析消息后更新 `PlaybackStateRepository`。
7. Compose UI 因 `StateFlow` 更新而自动重组。

---

## 5. 当前分层关系

当前架构更接近“轻量 MVVM + Service 中心化运行时 + 单例状态仓库”。

可概括为：

- `MainActivity`
  - 只负责组装 Compose 与注入 ViewModel
- `MainViewModel`
  - 负责 UI 动作转发与状态订阅
- `AudioBridgeService`
  - 负责后台执行、Socket 生命周期、协议处理、控制命令发送
- `PlaybackStateRepository`
  - 负责统一 UI 状态
- `AudioPlaybackManager` / `ProtocolReader` / `WindowsVolumeJsonCodec`
  - 负责相对独立的专用能力

### 5.1 简化数据流

```text
Compose UI
   ↓ 用户动作
MainViewModel
   ↓ Intent
AudioBridgeService
   ↓ 更新状态 / 发送命令 / 接收协议
PlaybackStateRepository ← ProtocolReader / WindowsVolumeJsonCodec / AudioPlaybackManager
   ↓ StateFlow
Compose UI
```

### 5.2 当前架构特点

优点：

- 结构简单，调试路径短
- 后台服务职责清晰，适合当前单连接模型
- 协议解析与 JSON 编解码已独立抽出，便于补测试
- `PlaybackUiState` 单一出口，Compose 使用成本低

限制：

- `AudioBridgeService` 目前职责偏多，集成了监听、连接管理、协议调度、命令发送、状态更新
- `PlaybackStateRepository` 是全局单例，方便但测试隔离一般
- ViewModel 目前较薄，领域逻辑大多仍在 Service / Repository
- 尚未引入依赖注入框架，模块替换需要手动 new / 手动接线

---

## 6. 关键状态对象

### 6.1 UI 主状态

`PlaybackUiState` 是界面唯一核心状态，主要包含：

- 服务是否运行
- TCP 连接是否建立
- 是否正在播放
- 当前播放音量
- 最近一帧序号
- 当前播放会话参数
- Windows 音量目录
- Windows 音量加载 / 错误 / 状态信息
- 最近日志列表

定义见 [app/src/main/java/dev/ran/audiobridge/model/PlaybackUiState.kt](app/src/main/java/dev/ran/audiobridge/model/PlaybackUiState.kt)。

### 6.2 Windows 音量状态

Windows 音量信息被组织为：

- `WindowsMasterVolume`
- `WindowsAppVolumeSession`
- `WindowsVolumeCatalog`
- `WindowsCommandAck`

定义见 [app/src/main/java/dev/ran/audiobridge/model/WindowsVolumeCatalog.kt](app/src/main/java/dev/ran/audiobridge/model/WindowsVolumeCatalog.kt)。

---

## 7. 协议要点

Android 端当前使用统一包头：

- Magic：`0x57414231`
- Version：`1`
- Header 长度：12 字节
- 字节序：小端

消息类型常量见 [app/src/main/java/dev/ran/audiobridge/network/BridgeMessageType.kt](app/src/main/java/dev/ran/audiobridge/network/BridgeMessageType.kt)。

Android 当前重点处理的消息：

- `SESSION_INIT`
- `AUDIO_FRAME`
- `VOLUME_CATALOG_SNAPSHOT`
- `VOLUME_SESSION_DELTA`
- `COMMAND_ACK`

如果协议字段有新增或修改，应遵守仓库根目录 [AGENTS.md](../AGENTS.md) 的约束：**Windows 与 Android 两端需要一起更新，并补上对应测试。**

---

## 8. 未来 agent 修改时的推荐切入点

### 8.1 如果要修改 UI 展示

优先查看：

- [app/src/main/java/dev/ran/audiobridge/ui/AudioBridgeScreen.kt](app/src/main/java/dev/ran/audiobridge/ui/AudioBridgeScreen.kt)
- [app/src/main/java/dev/ran/audiobridge/ui/MainPageScreen.kt](app/src/main/java/dev/ran/audiobridge/ui/MainPageScreen.kt)
- [app/src/main/java/dev/ran/audiobridge/ui/DetailsPageScreen.kt](app/src/main/java/dev/ran/audiobridge/ui/DetailsPageScreen.kt)
- [app/src/main/java/dev/ran/audiobridge/model/PlaybackUiState.kt](app/src/main/java/dev/ran/audiobridge/model/PlaybackUiState.kt)

### 8.2 如果要修改播放逻辑

优先查看：

- [app/src/main/java/dev/ran/audiobridge/service/AudioBridgeService.kt](app/src/main/java/dev/ran/audiobridge/service/AudioBridgeService.kt)
- [app/src/main/java/dev/ran/audiobridge/audio/AudioPlaybackManager.kt](app/src/main/java/dev/ran/audiobridge/audio/AudioPlaybackManager.kt)
- [app/src/main/java/dev/ran/audiobridge/model/BridgePacket.kt](app/src/main/java/dev/ran/audiobridge/model/BridgePacket.kt)
- [app/src/main/java/dev/ran/audiobridge/network/ProtocolReader.kt](app/src/main/java/dev/ran/audiobridge/network/ProtocolReader.kt)

### 8.3 如果要修改 Windows 音量控制协议

优先查看：

- [app/src/main/java/dev/ran/audiobridge/network/WindowsVolumeJsonCodec.kt](app/src/main/java/dev/ran/audiobridge/network/WindowsVolumeJsonCodec.kt)
- [app/src/main/java/dev/ran/audiobridge/network/BridgeMessageType.kt](app/src/main/java/dev/ran/audiobridge/network/BridgeMessageType.kt)
- [app/src/main/java/dev/ran/audiobridge/service/AudioBridgeService.kt](app/src/main/java/dev/ran/audiobridge/service/AudioBridgeService.kt)
- Windows 对应协议实现

### 8.4 如果要加测试

当前已有测试主要集中在协议层：

- [app/src/test/java/dev/ran/audiobridge/network/ProtocolReaderTest.kt](app/src/test/java/dev/ran/audiobridge/network/ProtocolReaderTest.kt)
- [app/src/test/java/dev/ran/audiobridge/network/WindowsVolumeJsonCodecTest.kt](app/src/test/java/dev/ran/audiobridge/network/WindowsVolumeJsonCodecTest.kt)

高价值补强方向：

1. `PlaybackStateRepository` 的状态合并 / 增量更新测试
2. `MainViewModel` 的行为测试
3. `AudioBridgeService` 的命令发送与错误路径测试
4. 音量会话排序、图标回填、错误提示等纯逻辑测试

---

## 9. 当前代码中的几个重要约束

1. **服务是核心状态源。**
   很多真实运行状态只能由 `AudioBridgeService` 驱动，不应只在 UI 层做“假更新”。

2. **`PlaybackStateRepository` 是单例。**
   新增状态字段时，要同时考虑：
   - 默认值
   - Repository 更新入口
   - UI 展示位置
   - 测试影响

3. **协议消息是双端契约。**
   `BridgeMessageType`、JSON 字段、二进制负载结构不能只改 Android 一边。

4. **本地播放音量与 Windows 音量是两套概念。**
   Android 本地播放音量由 `VolumePreferencesRepository` 持久化；Windows 主音量 / 会话音量通过协议远程控制。

5. **当前为单客户端模型。**
   `AudioBridgeService` 使用一个 `activeClientOutputStream` 表示当前连接，默认只服务一个 Windows 端连接。

---

## 10. 建议的后续演进方向

如果后续 Android 端继续扩展，建议优先考虑：

1. 将 `AudioBridgeService` 中的连接管理、协议分发、命令发送拆到独立组件。
2. 为 `PlaybackStateRepository` 提炼可单测的纯状态 reducer。
3. 为 ViewModel 增加更明确的 UI intent / action 模型。
4. 为协议层补充更多样例包 fixture，减少双端联调成本。
5. 如果模块继续增多，再引入依赖注入框架统一管理对象生命周期。

---

## 11. 一句话总结

Android 端当前是一个**以前台 Service 为运行核心、以单例 `PlaybackStateRepository` 为状态中心、以 Compose 为展示层**的接收与控制应用：

- 音频从 Windows 通过 TCP 进入 Android
- Service 解析协议并驱动 `AudioTrack`
- Windows 音量控制命令从 UI 发起，经 Service 回传给 Windows
- 所有界面状态最终汇总到 `PlaybackUiState`

对后续 agent 来说，绝大多数修改都可以先从以下 4 个点入手：

- [app/src/main/java/dev/ran/audiobridge/service/AudioBridgeService.kt](app/src/main/java/dev/ran/audiobridge/service/AudioBridgeService.kt)
- [app/src/main/java/dev/ran/audiobridge/repository/PlaybackStateRepository.kt](app/src/main/java/dev/ran/audiobridge/repository/PlaybackStateRepository.kt)
- [app/src/main/java/dev/ran/audiobridge/network/WindowsVolumeJsonCodec.kt](app/src/main/java/dev/ran/audiobridge/network/WindowsVolumeJsonCodec.kt)
- [app/src/main/java/dev/ran/audiobridge/ui/AudioBridgeScreen.kt](app/src/main/java/dev/ran/audiobridge/ui/AudioBridgeScreen.kt)
