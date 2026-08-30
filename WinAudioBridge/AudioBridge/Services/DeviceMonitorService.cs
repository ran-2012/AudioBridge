using WpfApp1.Models;

namespace WpfApp1.Services;

/// <summary>
/// 周期性轮询 ADB 设备列表，检测设备插拔和状态变化，自动触发重连。
/// </summary>
public sealed class DeviceMonitorService : IDisposable
{
    private static readonly TimeSpan PollingInterval = TimeSpan.FromSeconds(5);
    private readonly SettingsService _settingsService;
    private readonly AdbService _adbService;
    private readonly StreamingCoordinator _streamingCoordinator;
    private readonly AppLogService _logService;
    private CancellationTokenSource? _cts;
    private Task? _pollingTask;
    private List<AndroidDeviceInfo>? _lastDevices;

    public DeviceMonitorService(
        SettingsService settingsService,
        AdbService adbService,
        StreamingCoordinator streamingCoordinator,
        AppLogService logService)
    {
        _settingsService = settingsService;
        _adbService = adbService;
        _streamingCoordinator = streamingCoordinator;
        _logService = logService;
        _settingsService.SettingsChanged += OnSettingsChanged;
    }

    /// <summary>
    /// 启动设备监控。若当前设置中启用，则开始轮询。
    /// </summary>
    public void Start()
    {
        if (_settingsService.Current.EnableDeviceMonitor)
        {
            StartPolling();
        }
    }

    /// <summary>
    /// 停止设备监控。
    /// </summary>
    public void Stop()
    {
        StopPolling();
    }

    private void OnSettingsChanged(object? sender, EventArgs e)
    {
        if (_settingsService.Current.EnableDeviceMonitor)
        {
            StartPolling();
        }
        else
        {
            _logService.Info("DeviceMonitor", "用户关闭设备监控，停止轮询。");
            StopPolling();
        }
    }

    private void StartPolling()
    {
        if (_pollingTask is { IsCompleted: false })
        {
            return;
        }

        _logService.Info("DeviceMonitor", "启动设备状态轮询监控。");
        _cts?.Dispose();
        _cts = new CancellationTokenSource();
        _lastDevices = null;
        _pollingTask = Task.Run(() => PollingLoopAsync(_cts.Token));
    }

    private void StopPolling()
    {
        _cts?.Cancel();
        _cts?.Dispose();
        _cts = null;
        _pollingTask = null;
        _lastDevices = null;
    }

    private async Task PollingLoopAsync(CancellationToken cancellationToken)
    {
        using var timer = new PeriodicTimer(PollingInterval);

        try
        {
            while (await timer.WaitForNextTickAsync(cancellationToken))
            {
                await PollOnceAsync(cancellationToken);
            }
        }
        catch (OperationCanceledException)
        {
            // 正常取消，忽略
        }
    }

    private async Task PollOnceAsync(CancellationToken cancellationToken)
    {
        try
        {
            var packageName = _settingsService.Current.AndroidAppPackageName;
            if (string.IsNullOrWhiteSpace(packageName))
            {
                return;
            }

            var result = await _adbService.QueryConnectedDevicesAsync(packageName, cancellationToken);
            if (!result.IsSuccess)
            {
                return;
            }

            var currentDevices = result.Devices.ToList();

            if (_lastDevices is { Count: > 0 })
            {
                DetectAndHandleChanges(_lastDevices, currentDevices);
            }

            _lastDevices = currentDevices;
        }
        catch (OperationCanceledException)
        {
            // 正常取消，忽略
        }
        catch (Exception ex)
        {
            _logService.Warning("DeviceMonitor", $"设备轮询异常：{ex.Message}");
        }
    }

    private void DetectAndHandleChanges(
        IReadOnlyList<AndroidDeviceInfo> previous,
        IReadOnlyList<AndroidDeviceInfo> current)
    {
        if (ShouldSkipTriggerDueToState(_streamingCoordinator.Status.State))
        {
            return;
        }

        if (!ShouldTriggerReconnect(previous, current, out var triggeredDevice))
        {
            return;
        }

        _logService.Info("DeviceMonitor",
            $"检测到设备 {triggeredDevice!.Model} ({triggeredDevice.Serial}) 已上线且应用运行中，准备确保服务器与 reverse 就绪。");
        _ = _streamingCoordinator.EnsureServerReadyAsync(
            "设备状态监控检测到目标设备上线，确保服务器与 reverse 就绪",
            restartIfRunning: false);
    }

    /// <summary>
    /// 判断给定推流状态是否应跳过重连触发。
    /// 仅在 Idle 或 Faulted 状态时允许触发。
    /// </summary>
    internal static bool ShouldSkipTriggerDueToState(StreamingState state)
    {
        return state is not StreamingState.Idle and not StreamingState.Faulted;
    }

    /// <summary>
    /// 对比前后两次设备列表，判断是否应触发重连。
    /// 当存在一个设备：之前不在线/不存在，而现在在线且应用运行时，返回 true。
    /// </summary>
    internal static bool ShouldTriggerReconnect(
        IReadOnlyList<AndroidDeviceInfo> previous,
        IReadOnlyList<AndroidDeviceInfo> current,
        out AndroidDeviceInfo? triggeredDevice)
    {
        triggeredDevice = null;

        foreach (var device in current)
        {
            if (!string.Equals(device.State, "Online", StringComparison.OrdinalIgnoreCase))
            {
                continue;
            }

            if (!device.IsAudioAppRunning)
            {
                continue;
            }

            var previousDevice = previous.FirstOrDefault(d =>
                string.Equals(d.Serial, device.Serial, StringComparison.OrdinalIgnoreCase));

            // 设备是新出现的，或者之前不在线，或者之前应用未运行
            if (previousDevice is null
                || !string.Equals(previousDevice.State, "Online", StringComparison.OrdinalIgnoreCase)
                || !previousDevice.IsAudioAppRunning)
            {
                triggeredDevice = device;
                return true;
            }
        }

        return false;
    }

    public void Dispose()
    {
        _settingsService.SettingsChanged -= OnSettingsChanged;
        Stop();
    }
}
