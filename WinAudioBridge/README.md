# Windows 端架构介绍

## 1. 文档目的

这份文档面向后续参与 Windows 端开发的 agent 或开发者，目标是快速回答以下问题：

- Windows 端当前由哪些核心模块组成
- 音频推流、ADB 建链、Windows 音量同步分别由谁负责
- 关键状态从哪里产生、如何传播到 UI 和 Android 端
- 后续新增功能时，优先改哪些层，避免把逻辑加错位置

当前 Windows 端是一个基于 **WPF + .NET** 的桌面发送端，主要职责有两类：

1. 通过 ADB + TCP 把 Windows 系统音频发送给 Android
2. 暴露并控制 Windows 主音量和应用会话音量

Windows 主工程路径：

- `WinAudioBridge/AudioBridge/`

测试工程路径：

- `WinAudioBridge/AudioBridge.Tests/`

> 注意：源码命名空间仍然是 `WpfApp1`，这是历史遗留，后续开发时需要注意搜索范围。

---

## 2. 总体分层

可以把 Windows 端粗略看成 4 层：

### 2.1 Shell / UI 层

负责桌面应用生命周期、窗口展示、托盘入口、状态绑定。

主要文件：

- `App.xaml.cs`
- `MainWindow.xaml`
- `MainWindow.xaml.cs`
- `SettingsWindow.xaml`
- `SettingsWindow.xaml.cs`
- `ViewModels/StatusViewModel.cs`
- `Services/TrayService.cs`

### 2.2 协调层

负责把 ADB、音频采集、TCP 传输、Windows 音量服务串成完整业务流程。

核心文件：

- `Services/StreamingCoordinator.cs`

这是 Windows 端最重要的业务编排入口。大多数跨模块功能，都应该优先考虑是否放在这里汇总，而不是直接写进 UI 或底层服务。

### 2.3 基础服务层

负责和外部系统或平台 API 直接交互。

主要文件：

- `Services/SettingsService.cs`
- `Services/AdbService.cs`
- `Services/AudioCaptureService.cs`
- `Services/AudioTransportService.cs`
- `Services/WindowsVolumeService.cs`
- `Services/VolumeIconService.cs`
- `Services/AppLogService.cs`

### 2.4 数据模型层

负责承载配置、状态、协议类型、音量快照等数据。

主要目录：

- `Models/`

---

## 3. 启动装配关系

应用启动入口在 `App.xaml.cs`。

当前启动顺序大致是：

1. 创建单实例互斥锁，避免重复启动
2. 加载 `SettingsService`
3. 创建日志服务 `AppLogService`
4. 创建 ADB、音频采集、传输、图标、Windows 音量服务
5. 启动 `WindowsVolumeService` 的轮询监控
6. 创建 `StreamingCoordinator`
7. 创建主窗口和托盘服务
8. 异步尝试自动连接 Android 端

也就是说，**`App.xaml.cs` 目前承担了轻量级手工依赖注入容器的角色**。

项目还没有引入正式 DI 框架，因此：

- 新服务通常在 `App.xaml.cs` 中实例化
- 如果某个新能力需要全局共享，通常也要在这里装配
- 不建议在窗口或 ViewModel 内部直接 new 底层服务，除非是纯 UI 层对象

---

## 4. 核心模块职责

## 4.1 `StreamingCoordinator`

这是核心编排器，负责把“准备链路 -> 建立 TCP -> 发送初始化 -> 启动采集 -> 转发音频 -> 接收控制消息 -> 自动重连”串起来。

它直接依赖：

- `SettingsService`
- `AdbService`
- `AudioCaptureService`
- `AudioTransportService`
- `WindowsVolumeService`
- `VolumeIconService`
- `AppLogService`

它还负责订阅三个关键事件：

- `AudioCaptureService.AudioFrameCaptured`
- `AudioTransportService.MessageReceived`
- `WindowsVolumeService.SnapshotChanged`

主要职责：

- 根据配置生成 `StreamingSessionOptions`
- 查询 Android 设备并选择目标设备
- 建立 ADB 端口转发
- 建立本地 `127.0.0.1:5000` TCP 连接
- 发送 `SessionInit`
- 发送初始 Windows 音量目录快照
- 接收采集到的音频帧并转发
- 处理 Android 发来的音量控制命令
- 在链路异常时触发自动重连
- 维护 `StreamingStatusSnapshot`

### 为什么它重要

如果未来要增加以下能力，通常先看 `StreamingCoordinator`：

- 新的控制消息类型
- 新的连接前置检查
- 重连策略调整
- 会话初始化参数扩展
- 音频帧前处理或限流
- 音量目录增量推送策略

如果一个功能跨越多个服务，**优先放在协调器，而不是某个底层服务里硬耦合**。

---

## 4.2 `AdbService`

负责所有 ADB 相关工作，使用 `AdvancedSharpAdbClient`。

主要职责：

- 查找 `adb.exe`
- 启动 ADB Server
- 枚举已连接设备
- 判断目标 Android 包是否正在运行
- 建立端口转发 `tcp:5000 -> tcp:5000`

它不负责：

- TCP 数据传输
- UI 选择逻辑
- 自动重连策略

设备选择策略不在 `AdbService` 中，而在 `StreamingCoordinator.SelectTargetDevice(...)` 中实现。这样测试更容易，也便于以后调整策略。

---

## 4.3 `AudioCaptureService`

负责通过 `NAudio.Wave.WasapiLoopbackCapture` 采集 Windows 默认输出设备的系统回环音频。

主要职责：

- 探测默认回环格式
- 启动/停止系统回环采集
- 在 `DataAvailable` 回调中抛出 `AudioFrameCaptured` 事件
- 复制缓冲区，避免上游持有底层临时数组

设计特点：

- 只负责“采集”，不负责协议编码
- 不关心 Android 是否在线
- 不关心 TCP 是否已连接
- 通过事件把音频帧交给协调器

### 当前格式约束

当前项目实际上面向 Android 侧发送 `PCM16`。

如果默认输出设备回环格式是：

- `PCM16`：直接发送
- `Float32`：由 `StreamingCoordinator` 转成 `PCM16`

因此，“采集格式”和“传输格式”是分开的概念。

---

## 4.4 `AudioTransportService`

负责和 Android 端之间的 TCP 二进制协议收发。

主要职责：

- 建立和断开 `TcpClient`
- 写入统一协议头 + payload
- 发送音频帧
- 发送 JSON 控制消息
- 启动后台接收循环，读取 Android 发来的控制消息
- 通过 `MessageReceived` 事件向上抛出控制包

### 协议头结构

每个包都有固定 12 字节头：

- `magic`：4 字节，固定 `0x57414231`
- `version`：2 字节，当前为 `1`
- `messageType`：2 字节
- `payloadLength`：4 字节

### 设计边界

`AudioTransportService` 只知道“如何收发包”，不知道：

- 哪些业务状态应该触发发送
- 某个控制消息应如何改变 Windows 音量
- 失败后是否自动重连

这些都留给 `StreamingCoordinator`。

---

## 4.5 `WindowsVolumeService`

负责读取和修改 Windows 主音量及应用会话音量，底层使用 `NAudio.CoreAudioApi`。

主要职责：

- 抓取当前主音量状态
- 枚举音频会话
- 构造 `WindowsVolumeSnapshot`
- 定时轮询 Windows 音量变化
- 修改主音量/主静音
- 修改指定应用会话音量/静音
- 在快照变化时触发 `SnapshotChanged`

### 当前实现特点

- 采用 **轮询** 检测变化，而不是系统事件回调
- 会为每个应用会话生成稳定标识 `SessionId`
- 会计算 `iconKey` 和 `iconHash`，供 Android 侧缓存图标
- 变更成功后会立即刷新快照

### 为什么它是独立服务

Windows 音量相关逻辑容易变复杂，比如：

- 会话过滤规则
- 展示名推断
- 进程路径解析
- 图标策略
- 快照去重

这些都不应该塞进协调器或 ViewModel。

---

## 4.6 `VolumeIconService`

负责应用图标相关工作。

主要职责：

- 生成稳定 `iconKey`
- 计算图标源文件摘要 `iconHash`
- 提取 EXE 或图标资源，并转成 64x64 PNG 字节数组

它本质上是 `WindowsVolumeService` 和协议层之间的辅助服务。

---

## 4.7 `SettingsService`

负责 `settings.json` 的读取、保存和归一化。

主要职责：

- 启动时加载配置
- 配置不存在时写入默认值
- 保存配置并触发 `SettingsChanged`
- 统一校验合法值范围

当前主要配置包括：

- 编码
- 采样率
- 声道数
- 缓冲时长
- Android 包名
- 首选设备序列号
- 自动重连开关

### 重要说明

虽然配置里包含音频参数，但 Windows 端发送时会以**实际回环格式**为准，并在必要时做转换，不会机械相信配置值。

---

## 4.8 `AppLogService`

负责应用内日志缓冲。

主要职责：

- 将日志记录为 `ObservableCollection`
- 自动截断，只保留最近 300 条
- 在需要时切回 UI Dispatcher，保证线程安全更新 UI

它是当前问题排查最直接的观察窗口。

---

## 4.9 `TrayService`

负责系统托盘图标和菜单。

主要职责：

- 初始化托盘图标
- 提供“主界面 / 设置 / 退出”入口
- 双击托盘恢复主界面

这是纯桌面外壳能力，不参与推流业务。

---

## 4.10 `StatusViewModel`

这是当前主窗口的展示型 ViewModel。

主要职责：

- 把 `SettingsService`、`StreamingCoordinator`、`WindowsVolumeService` 的状态投影为 UI 字符串
- 处理主窗口按钮点击对应的异步操作
- 暴露最近日志列表

当前它比较薄，**业务判断仍主要在服务层**。

这是正确方向。后续不要把协议处理、ADB 逻辑、音量控制逻辑塞进 ViewModel。

---

## 5. 主流程说明

## 5.1 启动后自动连接流程

1. `App` 启动并装配全部服务
2. `WindowsVolumeService.StartMonitoring()` 启动
3. `StreamingCoordinator.AutoConnectIfPossibleAsync("启动后自动连接", false)` 被调起
4. 协调器读取设置并调用 `PrepareAsync(...)`
5. `AdbService` 查询设备与应用运行状态
6. 协调器选择目标设备
7. `AdbService` 建立端口转发
8. 协调器调用 `AudioTransportService.ConnectAsync(...)`
9. 发送 `SessionInit`
10. 发送一次完整音量目录快照
11. 启动 `AudioCaptureService`
12. 进入 `Streaming` 状态

---

## 5.2 音频发送流程

1. `AudioCaptureService` 从 WASAPI 回环拿到数据
2. 触发 `AudioFrameCaptured`
3. `StreamingCoordinator.OnAudioFrameCaptured(...)` 收到数据
4. 若当前采集格式是 `Float32`，先转成 `PCM16`
5. 生成递增 `sequence`
6. 调用 `AudioTransportService.SendAudioFrameAsync(...)`
7. 传输服务按统一协议头打包写入 TCP

### 关键点

- 音频帧发送失败后，会停止采集、断开传输，并进入故障/重连逻辑
- 转码逻辑当前在协调器，不在采集服务

---

## 5.3 Android 控制 Windows 音量流程

1. Android 通过同一条 TCP 连接发送控制消息
2. `AudioTransportService.ReceiveLoopAsync(...)` 收到消息
3. 通过 `MessageReceived` 事件抛给 `StreamingCoordinator`
4. 协调器根据 `BridgeMessageType` 分发处理：
   - `VolumeCatalogRequest`
   - `VolumeSetMasterRequest`
   - `VolumeSetSessionRequest`
5. `WindowsVolumeService` 修改系统音量或会话音量
6. 协调器发送 `CommandAck` 或新的音量目录快照

### 当前特性

- 音量目录是 JSON 控制消息
- 图标可以按需内联为 Base64 PNG
- 命令应答会带 `requestId`、成功状态、错误码和必要数据

---

## 5.4 Windows 音量变化推送流程

1. `WindowsVolumeService` 定时轮询系统状态
2. 若快照签名发生变化，则更新 `Current`
3. 触发 `SnapshotChanged`
4. `StreamingCoordinator.OnWindowsVolumeSnapshotChanged(...)` 被调用
5. 若当前传输已连接且不在停止流程中，则向 Android 推送新的音量目录快照

也就是说，**Android 既可以主动拉取目录，也会收到 Windows 侧的被动更新**。

---

## 5.5 自动重连流程

自动重连由 `StreamingCoordinator` 独占控制。

触发时机主要是：

- 音频帧发送失败
- 配置修改后需要重建连接
- 启动时满足自动连接条件

当前策略：

- 失败后等待 3 秒
- 要求 Android 端应用已经运行
- 如果用户关闭自动重连，则不重试
- 若已有重连循环在跑，则不重复启动

这部分是后续很适合补单元测试的区域。

---

## 6. 协议与消息边界

当前 Windows 端把**音频数据**和**控制消息**复用在同一条 TCP 连接上。

主要消息类型定义在 `Models/BridgeMessageType.cs`，包括：

- `SessionInit`
- `AudioFrame`
- `Heartbeat`
- `Status`
- `Stop`
- `VolumeCatalogRequest`
- `VolumeCatalogSnapshot`
- `VolumeSetMasterRequest`
- `VolumeSetSessionRequest`
- `VolumeSessionDelta`
- `IconContentRequest`
- `IconContentResponse`
- `CommandAck`

### 当前实际已落地的重点

Windows 端当前已明确使用的重点消息是：

- 向 Android：`SessionInit`、`AudioFrame`、`VolumeCatalogSnapshot`、`CommandAck`
- 从 Android：`VolumeCatalogRequest`、`VolumeSetMasterRequest`、`VolumeSetSessionRequest`

如果以后新增协议字段或消息类型，必须同步考虑：

1. `BridgeMessageType`
2. Windows 发送/接收逻辑
3. Android 解析/处理逻辑
4. 单元测试
5. 文档

---

## 7. 并发与线程模型

当前 Windows 端是典型的“事件驱动 + 少量后台任务”结构。

### 7.1 UI 线程

- WPF 窗口和 ViewModel 更新发生在 UI 线程
- `AppLogService` 会在必要时切回 Dispatcher

### 7.2 采集线程/回调线程

- `WasapiLoopbackCapture.DataAvailable` 不保证在 UI 线程
- 采集事件到来后直接进入协调器转发链路

### 7.3 TCP 接收后台任务

- `AudioTransportService` 在后台 `ReceiveLoopAsync(...)` 中持续读包
- 收到控制消息后通过事件通知协调器

### 7.4 音量监控后台任务

- `WindowsVolumeService` 用 `PeriodicTimer` 定时轮询

### 7.5 重连后台任务

- `StreamingCoordinator` 用单独任务维护重连循环

### 当前需要注意的问题

- 协调器里存在若干 `async void` 事件处理器，这是当前设计的一部分，修改时要特别谨慎
- 状态更新依赖多个服务的当前值组合，避免在新功能里引入相互等待或死锁
- 新增长耗时操作时，优先放后台，不要阻塞 WPF UI 线程

---

## 8. 目录建议与改动落点

后续 agent 开发时，可以按下面的规则判断改动位置。

### 8.1 新增设置项

优先改：

- `Models/AppSettings.cs`
- `Services/SettingsService.cs`
- `SettingsWindow.xaml` / `SettingsWindow.xaml.cs`
- 如果影响连接参数，再改 `StreamingCoordinator.cs`

### 8.2 新增协议消息

优先改：

- `Models/BridgeMessageType.cs`
- `Services/AudioTransportService.cs`（如果需要新的编码/收包方式）
- `Services/StreamingCoordinator.cs`
- Android 端对应解析与处理
- 双端测试

### 8.3 新增 Windows 音量字段

优先改：

- `Models/WindowsMasterVolumeState.cs`
- `Models/WindowsAppVolumeSession.cs`
- `Models/WindowsVolumeSnapshot.cs`
- `Services/WindowsVolumeService.cs`
- `StreamingCoordinator` 中 DTO 构造逻辑
- Android 端展示模型

### 8.4 新增推流前检查

优先改：

- `StreamingCoordinator.PrepareAsync(...)`
- 视情况补充 `AdbService` 或设置项

### 8.5 新增纯平台能力

例如：

- 热键
- 开机自启
- Windows 通知
- 额外托盘菜单

优先放在新的独立服务中，再在 `App.xaml.cs` 装配，不要塞进协调器。

---

## 9. 已有测试覆盖与推荐补点

当前 Windows 测试工程已经覆盖了一些纯逻辑部分，例如：

- 设置归一化
- 设备选择逻辑
- 图标辅助逻辑

测试工程路径：

- `WinAudioBridge/AudioBridge.Tests/`

推荐优先继续补的测试：

1. `AudioTransportService` 包头/包体编码测试
2. `StreamingCoordinator` 自动重连状态流转测试
3. `StreamingCoordinator` 控制消息处理测试
4. `WindowsVolumeService` 快照签名与去重逻辑测试
5. DTO 构造与协议样例测试

原则上优先补：

- 纯函数
- 可隔离逻辑
- 状态机/重连策略
- 协议转换

尽量少做高成本的端到端桌面 UI 自动化测试。

---

## 10. 当前架构的几个事实

后续 agent 开发时，建议默认接受以下事实，而不是重复重构：

1. 当前没有正式 DI 容器，使用 `App.xaml.cs` 手工装配
2. 当前没有复杂领域层，业务编排集中在 `StreamingCoordinator`
3. 当前 Windows 音量变化通过轮询获得，不是事件订阅
4. 当前协议中音频与控制消息共用一条 TCP 连接
5. 当前音频传输目标格式实际上是 `PCM16`
6. 当前 UI 层较薄，主要用于展示和触发命令
7. 当前源码命名空间仍是 `WpfApp1`

这些都不是问题本身，但在改代码时必须心里有数。

---

## 11. 给后续 agent 的直接建议

### 建议 1：跨模块功能先看协调器

如果一个功能同时涉及：

- ADB
- TCP
- 音量快照
- 推流状态
- 自动重连

优先从 `StreamingCoordinator` 下手。

### 建议 2：不要把业务逻辑塞进 ViewModel

ViewModel 应继续保持：

- 状态投影
- 命令入口
- 少量 UI 相关状态

### 建议 3：不要让底层服务互相知道太多

例如：

- `AudioCaptureService` 不应知道 Android 端协议
- `AdbService` 不应知道 UI 展示规则
- `AudioTransportService` 不应知道 Windows 音量语义

### 建议 4：协议变更必须双端同步

任何协议字段变更都要同步检查 Android 端，否则很容易出现“Windows 端看起来没问题，但 Android 端 silently break”的情况。

### 建议 5：优先增加可测试的纯逻辑

新增功能时，如果能先抽成纯函数或独立 helper，再接入协调器，后续维护成本会低很多。

---

## 12. 常用构建与测试命令

在 `WinAudioBridge/` 目录下：

- 构建：`dotnet build .\WinAudioBridge.sln /p:UseAppHost=false`
- 测试：`dotnet test .\WinAudioBridge.sln /p:UseAppHost=false`

---

## 13. 一句话总结

当前 Windows 端的核心结构可以概括为：

- **`App` 负责装配**
- **`StreamingCoordinator` 负责业务编排**
- **各类 `Service` 负责平台能力与 IO**
- **`ViewModel` 负责状态展示**
- **`Models` 负责数据承载**

后续开发时，只要持续保持这个边界，整体复杂度就还能控制住。
