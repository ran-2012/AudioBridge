# Manual Audio Restart

## ADDED Requirements

### Requirement: Windows 托盘菜单提供重启音频

Windows 应用 SHALL 在系统托盘右键菜单中提供“重启音频”操作。该操作 SHALL 在不打开主窗口的情况下触发音频链路重建。

#### Scenario: 用户从托盘触发重启音频

- **WHEN** 用户右键 Windows 托盘图标并点击“重启音频”
- **THEN** Windows SHALL 开始重启当前音频链路，并记录手动重启日志

#### Scenario: 菜单仍保留既有入口

- **WHEN** 用户打开 Windows 托盘右键菜单
- **THEN** 菜单 SHALL 继续提供主界面、设置和退出入口

### Requirement: 手动重启复用协调器生命周期

手动重启音频 SHALL 复用 `StreamingCoordinator` 的连接生命周期控制，先干净停止当前采集与传输，再重新执行自动连接和推流流程。

#### Scenario: 推流中执行手动重启

- **WHEN** 当前处于推流状态且用户触发“重启音频”
- **THEN** Windows SHALL 停止当前音频采集、断开传输连接，并重新建立 ADB forward、TCP 连接和音频采集

#### Scenario: 非推流状态执行手动重启

- **WHEN** 当前未处于推流状态且用户触发“重启音频”
- **THEN** Windows SHALL 按自动连接条件尝试建立音频链路

#### Scenario: 手动重启与自动重连并发

- **WHEN** 手动重启和自动重连在短时间内同时触发
- **THEN** Windows SHALL 串行执行连接生命周期操作，且 SHALL NOT 同时创建多条有效传输连接

### Requirement: 手动重启失败可恢复

手动重启音频失败时，Windows SHALL 保持可恢复状态，并允许后续手动或自动连接再次尝试。

#### Scenario: 重启时设备不可用

- **WHEN** 用户触发“重启音频”但没有可用 Android 接收端
- **THEN** Windows SHALL 显示或记录失败原因，并保持后续可再次连接的状态

#### Scenario: 重启过程中发生异常

- **WHEN** 手动重启过程中建立 ADB forward、TCP 连接或音频采集失败
- **THEN** Windows SHALL 清理已创建的采集和传输资源，并允许后续重启或自动重连重新发起