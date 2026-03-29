# Android 控制 Windows 音量开发文档

## 1. 文档目标

本文档用于定义新增需求“**在 Android 上控制 Windows 总音量与各个应用音量，并展示各个应用名称与图标**”的实现方案，覆盖以下内容：

- Android 端展示 Windows 当前主输出音量
- Android 端展示 Windows 当前可控应用会话列表
- Android 端展示每个应用的名称、进程标识、图标与音量状态
- Android 端调整 Windows 总音量、静音状态与单应用音量
- Windows 端实时回传音量变化，保持 Android 界面同步

本文档聚焦“**远程音量控制**”功能，不替代现有音频推流链路文档，而是在当前 WinAudioBridge 架构上增加一条“**控制与状态回传链路**”。

## 2. 需求范围

## 2.1 功能范围

本次新增需求包含：

- 查看 Windows 主音量
- 设置 Windows 主音量
- 切换 Windows 主静音状态
- 获取当前存在音频会话的应用列表
- 展示应用名称
- 展示应用图标
- 查看每个应用当前音量
- 设置每个应用音量
- 切换每个应用静音状态
- 在 Windows 本地音量发生变化时同步刷新 Android 界面

## 2.2 不纳入本期范围

以下能力建议暂不纳入首版：

- Android 端直接切换 Windows 默认播放设备
- 控制无音频会话、但已安装的所有应用
- 精细到进程级之外的标签分组与自定义排序
- 图标云端同步或跨设备共享缓存
- 多台 Android 同时控制同一台 Windows 的并发权限管理

## 2.3 首版交付目标

首版建议聚焦：

- 基于当前已存在的 Windows <-> Android 通信基础，增加双向控制消息
- Windows 端基于 Core Audio Session API 枚举主音量与应用会话
- Android 端实现“总音量 + 应用列表”页面
- 应用图标采用 Windows 端提取、压缩后下发到 Android 的方式
- 支持实时刷新与手动刷新两种同步模式

## 3. 用户场景

### 3.1 场景一：调整 Windows 总音量

用户在 Android 端打开控制页后，可以看到 Windows 当前总音量百分比与静音状态。拖动滑块后，Windows 主输出音量立即变化。

### 3.2 场景二：调整单个应用音量

用户在 Android 端列表中看到例如“Chrome”“Spotify”“游戏进程”等应用项，每个应用项展示：

- 应用图标
- 应用名称
- 进程名或进程 ID
- 当前音量
- 静音状态

用户可单独拖动某个应用滑块，不影响其他应用音量。

### 3.3 场景三：Windows 本地变化同步

当用户在 Windows 音量混合器中手动修改总音量或应用音量时，Android 端应在短时间内自动刷新，不要求用户重复进入页面。

## 4. 总体设计

## 4.1 功能链路

新增需求的总体链路如下：

1. Android 端向 Windows 端发起音量目录请求
2. Windows 端枚举主音量与音频会话列表
3. Windows 端提取应用名称、图标、会话音量与静音状态
4. Windows 端将目录快照推送给 Android
5. Android 渲染总音量卡片与应用列表
6. Android 用户拖动滑块或切换静音时，向 Windows 端发送控制命令
7. Windows 端调用 Core Audio API 更新目标音量
8. Windows 本地监听到音量变更后，再次将最新状态推送给 Android

## 4.2 架构扩展

建议在现有架构基础上新增以下模块：

### Windows 端

- `WindowsVolumeService`
  - 管理系统主音量
  - 枚举应用音频会话
  - 提供单应用音量读写
  - 监听主音量与会话音量变化

- `VolumeIconService`
  - 根据进程路径提取图标
  - 转换为 PNG 字节流
  - 生成缓存键和摘要

- `ControlProtocolService`
  - 负责接收 Android 发来的控制消息
  - 负责发送音量目录、增量更新与回执

- `VolumeSessionSnapshotBuilder`
  - 将 Windows 本地对象转换为协议模型

### Android 端

- `VolumeControlRepository`
  - 保存当前主音量状态与应用列表
  - 管理同步时间和错误状态

- `VolumeControlViewModel`
  - 页面状态管理
  - 处理滑块变更节流与命令下发

- `VolumeControlScreen`
  - 显示总音量卡片、应用列表、刷新状态

- `IconCacheManager`
  - 缓存来自 Windows 的应用图标

## 4.3 通信模式

当前音频链路虽然以 Windows -> Android 为主，但 TCP 本身支持双向通信。建议：

- 继续复用当前已建立的 Socket 连接
- 在现有协议上新增“控制消息”和“状态回传消息”
- 音频流与控制流通过 `MessageType` 区分

如后续发现音频高频传输与控制消息互相影响，也可演进为：

- 端口 `5000`：音频流
- 端口 `5001`：控制与状态流

首版建议优先采用“**单连接多消息类型**”，减少联调复杂度。

## 5. Windows 端实现设计

## 5.1 技术选型

建议继续基于当前 .NET/WPF 技术栈实现，核心能力来源如下：

- `NAudio.CoreAudioApi`
  - 访问默认播放设备
  - 读取主音量
  - 枚举 `AudioSession` 列表
  - 读写 `SimpleAudioVolume`

- `System.Diagnostics.Process`
  - 获取进程名、主模块路径、显示名称候选值

- `System.Drawing` 或 Win32 Shell API
  - 提取可执行文件图标
  - 转换为 PNG

## 5.2 主音量控制

建议通过默认渲染设备的 `AudioEndpointVolume` 实现：

- 读取主音量：`MasterVolumeLevelScalar`
- 设置主音量：`MasterVolumeLevelScalar = value`
- 读取静音：`Mute`
- 设置静音：`Mute = true/false`

Windows 端需要监听：

- `OnVolumeNotification`

监听到变化后，立即向 Android 推送最新主音量状态。

## 5.3 应用会话枚举

建议通过 `MMDeviceEnumerator` 获取默认输出设备，再从 `AudioSessionManager` 获取会话集合。

每个会话建议提取以下字段：

- 会话唯一标识 `SessionInstanceId`
- 进程 ID `ProcessId`
- 进程名称 `ProcessName`
- 显示名称 `DisplayName`
- 图标路径 `IconPath`（若系统提供）
- 当前音量 `SimpleAudioVolume.Volume`
- 当前静音 `SimpleAudioVolume.Mute`
- 状态 `AudioSessionState`

### 会话名称生成规则

建议按以下优先级生成 Android 端展示名称：

1. `AudioSessionControl.DisplayName`
2. 进程主窗口标题（若易于获取）
3. 可执行文件名（不含扩展名）
4. 进程名

## 5.4 会话筛选规则

首版建议只展示“当前存在活动或非过期音频会话”的应用，过滤规则建议如下：

- 过滤系统声音虚拟会话（若无法稳定控制）
- 过滤已退出但会话失效的进程
- 过滤无意义名称且无图标、无可调音量的会话
- 支持同一进程多个会话时的合并策略

### 首版合并建议

首版建议按“**进程 ID + 会话实例**”展示，不强行合并。

原因：

- Core Audio 中同进程可能存在多个独立会话
- 合并逻辑容易错误覆盖真实音量状态
- 先保证控制准确，再做展示优化

后续若 UI 复杂度过高，再增加“按应用聚合展示”的增强方案。

## 5.5 图标提取设计

应用图标建议采用以下优先级获取：

1. 会话提供的 `IconPath`
2. 进程主模块路径对应的可执行文件图标
3. 默认应用占位图标

提取后建议统一处理为：

- PNG 格式
- 建议尺寸 `64x64`
- Android 端按需缩放显示

### 图标传输建议

为避免每次刷新都重复发送大图，建议采用两段式策略：

- 音量目录只携带 `IconKey`、`IconHash`
- Android 若本地无缓存或摘要不一致，再请求图标内容

首版若为了简化实现，也可直接在目录快照中内联 Base64 PNG，但要控制大小，并在会话数较多时评估延迟。

## 5.6 音量变化监听

Windows 端应监听两类变化：

- 主音量变化
- 会话音量变化

建议监听来源：

- `AudioEndpointVolume` 回调
- `AudioSessionEvents` 回调

当任意目标会话音量、静音状态、显示名称、活跃状态变化时：

- 更新本地缓存
- 发送增量更新或触发一次轻量快照刷新

## 5.7 建议类设计

- `WindowsVolumeService`
- `WindowsMasterVolumeState`
- `WindowsAppVolumeSession`
- `VolumeSessionTracker`
- `VolumeIconService`
- `VolumeProtocolMessageWriter`
- `VolumeProtocolMessageReader`

## 6. Android 端实现设计

## 6.1 页面结构

建议新增“Windows 音量控制”页面，包含以下区域：

- 顶部：Windows 连接状态
- 顶部：最近同步时间
- 中部：总音量卡片
- 下部：应用音量列表
- 底部：刷新按钮、错误提示、加载状态

## 6.2 总音量卡片

建议展示：

- Windows 设备名称（可选）
- 当前总音量百分比
- 主静音状态
- 总音量滑块
- 主静音开关

## 6.3 应用列表项

每一项建议展示：

- 应用图标
- 应用名称
- 进程名或补充说明
- 当前音量百分比
- 音量滑块
- 静音开关

建议支持：

- 按名称排序
- 活跃会话优先
- 支持列表刷新

## 6.4 滑块交互策略

为了避免拖动时频繁发送消息，建议：

- UI 本地立即更新展示值
- 拖动过程中进行 `100ms ~ 200ms` 节流发送
- 手指释放时强制发送最终值

这样可以在体验与链路压力之间取得平衡。

## 6.5 图标缓存策略

Android 端建议：

- 以内存缓存 + 磁盘缓存组合保存图标
- 以 `IconKey + IconHash` 作为缓存判定依据
- 页面滚动时优先使用本地图标，避免重复解码

## 6.6 状态同步策略

Android 端建议支持三种刷新方式：

- 首次进入页面自动请求完整快照
- 用户下拉或点击按钮手动刷新
- 收到 Windows 推送的增量更新后局部刷新

## 7. 协议设计

## 7.1 新增消息类型建议

建议在现有协议中新增以下消息类型：

| 消息类型 | 值 | 方向 | 说明 |
|---|---:|---|---|
| `VolumeCatalogRequest` | `0x10` | Android -> Windows | 请求完整音量目录 |
| `VolumeCatalogSnapshot` | `0x11` | Windows -> Android | 返回完整音量快照 |
| `VolumeSetMasterRequest` | `0x12` | Android -> Windows | 设置总音量/静音 |
| `VolumeSetSessionRequest` | `0x13` | Android -> Windows | 设置单应用音量/静音 |
| `VolumeSessionDelta` | `0x14` | Windows -> Android | 推送主音量或会话增量变化 |
| `IconContentRequest` | `0x15` | Android -> Windows | 请求图标内容 |
| `IconContentResponse` | `0x16` | Windows -> Android | 返回图标内容 |
| `CommandAck` | `0x17` | Windows -> Android | 命令处理结果 |

## 7.2 主音量模型建议

`MasterVolumeState` 建议字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `DeviceId` | String | 默认输出设备 ID |
| `DeviceName` | String | 默认输出设备名称 |
| `Volume` | Float | `0.0 ~ 1.0` |
| `Muted` | Bool | 是否静音 |
| `LastUpdatedUtc` | Int64 | 时间戳 |

## 7.3 应用会话模型建议

`AppVolumeSession` 建议字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `SessionId` | String | 会话唯一标识 |
| `ProcessId` | Int32 | 进程 ID |
| `ProcessName` | String | 进程名 |
| `DisplayName` | String | 用于展示的名称 |
| `Volume` | Float | `0.0 ~ 1.0` |
| `Muted` | Bool | 是否静音 |
| `State` | String | Active / Inactive / Expired |
| `IconKey` | String | 图标缓存键 |
| `IconHash` | String | 图标摘要 |

## 7.4 控制命令设计

### 设置总音量

Android 发送：

- 目标音量值
- 可选静音状态
- 请求 ID

Windows 处理后返回：

- 请求 ID
- 是否成功
- 错误码
- 最新主音量状态

### 设置应用音量

Android 发送：

- `SessionId`
- 目标音量值
- 可选静音状态
- 请求 ID

Windows 处理后返回：

- 请求 ID
- 是否成功
- 错误码
- 最新会话状态

## 7.5 一致性要求

为避免 Android 界面与 Windows 实际状态不一致，建议：

- 所有控制命令都返回回执
- 成功后以 Windows 端最新状态覆盖 Android 本地临时值
- 若命令执行失败，Android 回退到服务端最新值并展示错误提示

## 8. 安全与边界处理

## 8.1 权限边界

当前链路基于本地 USB + ADB 连接，默认信任前提是“设备已通过开发者调试授权”。

首版可接受该模型，但建议增加以下约束：

- 仅在用户显式开启推流/控制时建立控制链路
- Android 端断开后，Windows 不保留可写控制会话
- 后续可增加会话口令或一次性握手令牌

## 8.2 异常场景

需要处理以下情况：

- 目标应用在控制前已退出
- 会话枚举时进程路径读取失败
- 某些系统会话没有可用图标
- 拖动过程中网络短暂断开
- Windows 默认输出设备发生切换

对应策略建议：

- 会话失效时返回错误并刷新列表
- 图标读取失败时显示默认图标
- 默认设备切换时重新构建主音量上下文与会话跟踪器
- 网络恢复后 Android 自动拉取完整快照

## 9. 开发拆分建议

## 9.1 第一阶段：Windows 音量服务打底

交付内容：

- 主音量读取与设置
- 应用会话枚举
- 单应用音量读取与设置
- 本地图标提取

验收标准：

- Windows 端可输出完整音量目录日志
- 手动调用服务接口可成功修改总音量与应用音量

## 9.2 第二阶段：协议扩展

交付内容：

- 新增控制消息定义
- Windows 端命令处理器
- Android 端目录请求与响应解析

验收标准：

- Android 可收到完整音量目录
- Android 发送控制命令可收到回执

## 9.3 第三阶段：Android UI 与交互

交付内容：

- 总音量卡片
- 应用音量列表
- 图标缓存与展示
- 滑块节流控制

验收标准：

- Android 端可稳定展示名称、图标、音量
- 用户拖动滑块时 Windows 端实时变化

## 9.4 第四阶段：实时同步与体验优化

交付内容：

- Windows 端事件监听
- Android 增量刷新
- 失败重试与弱网恢复

验收标准：

- Windows 本地改动后 Android 在可接受时间内同步
- 列表不会频繁闪烁或错乱

## 10. 测试建议

## 10.1 功能验证

- 获取总音量成功
- 调整总音量成功
- 主静音切换成功
- 获取应用列表成功
- 每个应用显示正确名称
- 大部分常见应用显示可识别图标
- 调整单应用音量成功
- 单应用静音切换成功

## 10.2 兼容性验证

- Chrome / Edge / Spotify / 网易云音乐 / 游戏 / 视频播放器
- 单会话应用
- 多会话应用
- 无主窗口后台应用
- 图标路径缺失应用

## 10.3 稳定性验证

- 长时间页面停留
- Windows 本地频繁调音量
- Android 快速连续拖动多个滑块
- 设备重新插拔与 ADB 重连

## 11. 风险与建议

## 11.1 主要风险

- 某些应用会暴露多个音频会话，导致 UI 数量多于用户预期
- 会话图标或名称来源不稳定，存在显示不一致
- 高速滑块拖动会产生较多控制消息
- 默认播放设备切换后旧会话引用会失效

## 11.2 建议

- 首版优先保证“控制准确、状态可刷新”，再优化聚合展示
- 先以活动会话为主，减少列表噪声
- 先使用快照 + 增量更新模型，不急于引入复杂订阅系统
- 图标先做缓存与默认兜底，不强求所有应用都能拿到品牌图标

## 12. 与现有文档的关系

- 技术总方案：见 [doc/技术方案.md](doc/%E6%8A%80%E6%9C%AF%E6%96%B9%E6%A1%88.md)
- 通信协议扩展：见 [doc/Windows-Android通信技术文档.md](doc/Windows-Android%E9%80%9A%E4%BF%A1%E6%8A%80%E6%9C%AF%E6%96%87%E6%A1%A3.md)
- 当前开发状态：见 [doc/开发状态.md](doc/%E5%BC%80%E5%8F%91%E7%8A%B6%E6%80%81.md)

本功能建议作为 WinAudioBridge 下一阶段的重要增强能力推进。