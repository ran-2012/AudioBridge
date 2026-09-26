## Why

Android 接收端只能展示连接、播放和音量状态，无法直观看到正在播放音频的声道能量、左右声道相关性和短时电平。新增实时可视化页签并统一为深色主题，可在不改变传输协议的前提下提供播放反馈和声道诊断能力。

## What Changes

- 在 Android 顶部操作栏中央增加主页面与音频可视化页面的页签切换控件。
- 新增音频可视化页面：上方中央显示 L/R Lissajous 音频图像，上方右侧显示短时 RMS `dBFS` 电平，下方显示左右双声道频谱。
- 从 Android 播放链路提取有界、降采样的实时分析数据，同时保持音频播放线程不被 UI 分析阻塞。
- 为无连接、等待音频、播放中和断线状态提供稳定的可视化反馈。
- 将 Android 应用切换为统一的深色主题，并保证现有主页、详情页和系统栏的内容对比度。
- 为频谱计算、响度计算、立体声音频图像映射和数据节流增加单元测试。
- 不修改 Windows 端、TCP 包格式、PCM 音频格式或现有音量控制行为。

## Capabilities

### New Capabilities

- `android-audio-visualizer`: 定义音频可视化页签、顶部导航、双声道频谱、立体声音频图像、响度、实时数据流和空闲状态。
- `android-dark-theme`: 定义 Android 应用级深色主题、系统栏外观和现有页面的对比度要求。

### Modified Capabilities

无。

## Impact

- 影响 `AudioBridge/app/src/main/java/dev/ran/audiobridge/ui/` 下的页面、顶部操作栏和新增可视化 Compose 组件。
- 影响 `AudioBridge/app/src/main/java/dev/ran/audiobridge/ui/theme/` 下的主题和颜色配置。
- 影响 Android 音频播放链路、状态仓库和 UI 状态模型，增加 PCM 分析数据的有界发布通道。
- 增加频谱分析所需的本地计算代码；优先使用 Android/Kotlin 现有能力，除非实现阶段验证后确认需要引入固定版本的 FFT 依赖。
- 增加 Android 本地单元测试和关键布局的 Compose UI 测试。
- 不影响 Windows 端、ADB/TCP 连接方式和跨端协议。