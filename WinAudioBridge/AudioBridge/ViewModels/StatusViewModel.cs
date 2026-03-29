using System.ComponentModel;
using System.Collections.ObjectModel;
using WpfApp1.Models;
using WpfApp1.Services;

namespace WpfApp1.ViewModels;

public sealed class StatusViewModel : INotifyPropertyChanged
{
    private readonly SettingsService _settingsService;
    private readonly StreamingCoordinator _streamingCoordinator;
    private readonly WindowsVolumeService _windowsVolumeService;
    private readonly AppLogService _logService;
    private bool _isBusy;

    public StatusViewModel(SettingsService settingsService, StreamingCoordinator streamingCoordinator, WindowsVolumeService windowsVolumeService, AppLogService logService)
    {
        _settingsService = settingsService;
        _streamingCoordinator = streamingCoordinator;
        _windowsVolumeService = windowsVolumeService;
        _logService = logService;
        _settingsService.SettingsChanged += OnSettingsChanged;
        _streamingCoordinator.StatusChanged += OnStreamingStatusChanged;
        _windowsVolumeService.SnapshotChanged += OnWindowsVolumeSnapshotChanged;
    }

    public string Encoding => _settingsService.Current.Encoding;

    public string SampleRateText => $"{_settingsService.Current.SampleRate} Hz";

    public string ChannelsText => _settingsService.Current.Channels == 1 ? "单声道" : "立体声";

    public string BufferText => $"{_settingsService.Current.BufferMilliseconds} ms";

    public string AndroidPackageName => _settingsService.Current.AndroidAppPackageName;

    public string PreferredDeviceText => string.IsNullOrWhiteSpace(_settingsService.Current.PreferredDeviceSerial)
        ? "自动选择"
        : _settingsService.Current.PreferredDeviceSerial;

    public string CoordinatorStateText => GetStateText(_streamingCoordinator.Status.State);

    public string CoordinatorStatusMessage => _streamingCoordinator.Status.StatusMessage;

    public string TargetDeviceText => string.IsNullOrWhiteSpace(_streamingCoordinator.Status.TargetDeviceSerial)
        ? "未选择"
        : $"{_streamingCoordinator.Status.TargetDeviceName} ({_streamingCoordinator.Status.TargetDeviceSerial})";

    public string TransportStatusText => _streamingCoordinator.Status.IsTransportConnected ? "已连接" : "未连接";

    public string CaptureStatusText => _streamingCoordinator.Status.IsCapturing ? "采集中" : "未采集";

    public string WindowsVolumeMonitorText => _windowsVolumeService.IsMonitoring ? "监控中" : "未启动";

    public string WindowsMasterVolumeText => $"{_windowsVolumeService.Current.MasterVolume.DeviceName} / {(int)Math.Round(_windowsVolumeService.Current.MasterVolume.Volume * 100)}%";

    public string WindowsMasterMuteText => _windowsVolumeService.Current.MasterVolume.IsMuted ? "静音" : "未静音";

    public string WindowsVolumeSessionCountText => $"{_windowsVolumeService.Current.Sessions.Count} 个会话";

    public string WindowsVolumeLastSyncText => _windowsVolumeService.Current.CapturedAtUtc == default
        ? "未同步"
        : _windowsVolumeService.Current.CapturedAtUtc.ToLocalTime().ToString("HH:mm:ss");

    public ReadOnlyObservableCollection<AppLogEntry> RecentLogs => _logService.Entries;

    public bool IsBusy
    {
        get => _isBusy;
        private set
        {
            if (_isBusy == value)
            {
                return;
            }

            _isBusy = value;
            RaisePropertyChanged(nameof(IsBusy));
            RaisePropertyChanged(nameof(CanPrepare));
            RaisePropertyChanged(nameof(CanStart));
            RaisePropertyChanged(nameof(CanStop));
        }
    }

    public bool CanPrepare => !IsBusy && _streamingCoordinator.Status.State is StreamingState.Idle or StreamingState.Faulted;

    public bool CanStart => !IsBusy && _streamingCoordinator.Status.State is StreamingState.Idle or StreamingState.Faulted or StreamingState.Ready;

    public bool CanStop => !IsBusy && _streamingCoordinator.Status.State is StreamingState.Streaming or StreamingState.Preparing or StreamingState.Ready;

    public event PropertyChangedEventHandler? PropertyChanged;

    private void OnSettingsChanged(object? sender, EventArgs e)
    {
        RaisePropertyChanged(nameof(Encoding));
        RaisePropertyChanged(nameof(SampleRateText));
        RaisePropertyChanged(nameof(ChannelsText));
        RaisePropertyChanged(nameof(BufferText));
        RaisePropertyChanged(nameof(AndroidPackageName));
        RaisePropertyChanged(nameof(PreferredDeviceText));
    }

    private void OnStreamingStatusChanged(object? sender, EventArgs e)
    {
        RaisePropertyChanged(nameof(CoordinatorStateText));
        RaisePropertyChanged(nameof(CoordinatorStatusMessage));
        RaisePropertyChanged(nameof(TargetDeviceText));
        RaisePropertyChanged(nameof(TransportStatusText));
        RaisePropertyChanged(nameof(CaptureStatusText));
        RaisePropertyChanged(nameof(CanPrepare));
        RaisePropertyChanged(nameof(CanStart));
        RaisePropertyChanged(nameof(CanStop));
    }

    private void OnWindowsVolumeSnapshotChanged(object? sender, EventArgs e)
    {
        RaisePropertyChanged(nameof(WindowsVolumeMonitorText));
        RaisePropertyChanged(nameof(WindowsMasterVolumeText));
        RaisePropertyChanged(nameof(WindowsMasterMuteText));
        RaisePropertyChanged(nameof(WindowsVolumeSessionCountText));
        RaisePropertyChanged(nameof(WindowsVolumeLastSyncText));
    }

    public async Task PrepareAsync()
    {
        await ExecuteBusyAsync(() => _streamingCoordinator.PrepareAsync());
    }

    public async Task StartAsync()
    {
        await ExecuteBusyAsync(() => _streamingCoordinator.StartStreamingAsync());
    }

    public async Task StopAsync()
    {
        await ExecuteBusyAsync(() => _streamingCoordinator.StopStreamingAsync());
    }

    private async Task ExecuteBusyAsync(Func<Task> action)
    {
        if (IsBusy)
        {
            return;
        }

        try
        {
            IsBusy = true;
            await action();
        }
        finally
        {
            IsBusy = false;
        }
    }

    private void RaisePropertyChanged(string propertyName)
    {
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
    }

    private static string GetStateText(StreamingState state) => state switch
    {
        StreamingState.Preparing => "准备中",
        StreamingState.Ready => "已就绪",
        StreamingState.Streaming => "推流中",
        StreamingState.Faulted => "异常",
        _ => "空闲"
    };
}
