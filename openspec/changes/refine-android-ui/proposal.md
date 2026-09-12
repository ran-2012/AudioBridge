## Why

Android 端现有界面接近默认 Material 3 样式，在平板横屏下存在信息集中、控件过宽、空间利用率低和状态层级不清的问题。现有响应式 HTML 设计稿和真机截图已提供可执行的视觉目标，需要将其转化为 Jetpack Compose 的界面规范和实施任务。

## What Changes

- 重组 Android 主页面的信息层级，使连接状态、Windows 主音量、本机播放设置和应用音量会话更易扫描。
- 为手机竖屏和平板横屏分别定义响应式布局，避免在宽屏上简单拉伸手机控件。
- 优化应用音量会话列表，使不定数量的会话保持紧凑、可滚动和可操作。
- 重构“当前会话与日志”页面，将连接、运行状态、会话参数、日志和应用过滤按低频设置与诊断职责组织。
- 增加连接中、已连接、播放中、断线、空会话、加载和错误等状态的明确视觉反馈。
- 建立适用于 Compose 的颜色、间距、圆角、边框和组件层级规则。
- 保留现有 Android/Material 字体体系，不引入自定义字体，不采用 HTML 设计稿中的衬线或等宽字体方案。
- 保留现有连接、音频播放、音量控制、缓存设置和应用过滤行为，不修改通信协议或业务状态模型的语义。

## Capabilities

### New Capabilities

- `android-responsive-ui`: 定义 Android 主页面与设置/诊断页面的响应式布局、组件层级、交互状态、可访问性和视觉约束。

### Modified Capabilities

无。

## Impact

- 主要影响 `AudioBridge/app/src/main/java/dev/ran/audiobridge/ui/` 下的 Compose 页面和可复用组件。
- 影响 `AudioBridge/app/src/main/java/dev/ran/audiobridge/ui/theme/` 下的颜色、形状和主题配置，但不替换字体。
- 可能增加 Compose UI 测试或预览，用于验证手机竖屏、平板横屏和关键状态。
- 不修改 Windows 端、TCP 协议、音频播放内核、Service 行为或持久化数据格式。
- 设计依据为 `doc/app-ui-refine/audiobridge-responsive.html`、`audiobridge-responsive.png` 和同目录真机基线截图。