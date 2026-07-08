using System.Buffers.Binary;
using System.IO;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using WpfApp1.Models;

namespace WpfApp1.Services;

public sealed class StreamingCoordinator : IDisposable, IAsyncDisposable
{
    private static readonly TimeSpan ReconnectDelay = TimeSpan.FromSeconds(3);
    private static readonly TimeSpan HeartbeatIdleThreshold = TimeSpan.FromSeconds(5);
    private static readonly TimeSpan LivenessTimeout = TimeSpan.FromSeconds(15);
    private static readonly TimeSpan AndroidPlaybackStatusTimeout = TimeSpan.FromSeconds(10);
    private static readonly TimeSpan AndroidPlaybackLatencyThreshold = TimeSpan.FromSeconds(2);
    private const int AndroidPlaybackAbnormalRestartThreshold = 3;
    private readonly SettingsService _settingsService;
    private readonly AdbService _adbService;
    private readonly AudioCaptureService _audioCaptureService;
    private readonly AudioTransportService _audioTransportService;
    private readonly WindowsVolumeService _windowsVolumeService;
    private readonly VolumeIconService _volumeIconService;
    private readonly AppLogService _logService;
    private readonly JsonSerializerOptions _jsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
    };
    private NAudio.Wave.WaveFormat? _activeCaptureFormat;
    private StreamingSessionOptions? _activeSessionOptions;
    private CancellationTokenSource? _reconnectCancellationTokenSource;
    private Task? _reconnectTask;
    private CancellationTokenSource? _heartbeatCancellationTokenSource;
    private Task? _heartbeatTask;
    private readonly SemaphoreSlim _lifecycleLock = new(1, 1);
    private uint _sequence;
    private bool _frameSendFaulted;
    private bool _isStopping;
    private long _lastTransportActivityUtcTicks;
    private long _lastInboundActivityUtcTicks;
    private long _lastAndroidPlaybackStatusUtcTicks;
    private int _androidPlaybackStatusCount;
    private int _consecutiveAbnormalPlaybackStatusCount;
    private int _heartbeatCount;

    public StreamingCoordinator(
        SettingsService settingsService,
        AdbService adbService,
        AudioCaptureService audioCaptureService,
        AudioTransportService audioTransportService,
        WindowsVolumeService windowsVolumeService,
        VolumeIconService volumeIconService,
        AppLogService logService)
    {
        _settingsService = settingsService;
        _adbService = adbService;
        _audioCaptureService = audioCaptureService;
        _audioTransportService = audioTransportService;
        _windowsVolumeService = windowsVolumeService;
        _volumeIconService = volumeIconService;
        _logService = logService;

        _audioCaptureService.AudioFrameCaptured += OnAudioFrameCaptured;
        _audioTransportService.MessageReceived += OnTransportMessageReceived;
        _audioTransportService.ConnectionLost += OnTransportConnectionLost;
        _windowsVolumeService.SnapshotChanged += OnWindowsVolumeSnapshotChanged;
        UpdateStatus(StreamingState.Idle, "推流协调器已初始化，等待准备链路。", null, null);
    }

    public StreamingStatusSnapshot Status { get; private set; } = new();

    public event EventHandler? StatusChanged;

    public async Task PrepareAsync(CancellationToken cancellationToken = default, bool requireAudioAppRunning = false)
    {
        try
        {
            var options = BuildOptions();
            _logService.Info("Coordinator", $"开始准备链路：包名={options.AndroidAppPackageName}，优先设备={GetPreferredDeviceText(options.PreferredDeviceSerial)}。 ");
            UpdateStatus(StreamingState.Preparing, "正在检查设备并建立 ADB 端口转发...", null, null);

            var deviceResult = await _adbService.QueryConnectedDevicesAsync(options.AndroidAppPackageName, cancellationToken);
            if (!deviceResult.IsSuccess)
            {
                UpdateStatus(StreamingState.Faulted, deviceResult.StatusMessage, null, null);
                return;
            }

            var targetDevice = SelectTargetDevice(deviceResult.Devices, options.PreferredDeviceSerial, requireAudioAppRunning);

            if (targetDevice is null)
            {
                var message = requireAudioAppRunning
                    ? "未检测到已启动接收端应用的 Android 设备，已跳过自动连接。"
                    : "未找到可用于推流的 Android 设备。";
                _logService.Warning("Coordinator", message + " ");
                UpdateStatus(requireAudioAppRunning ? StreamingState.Idle : StreamingState.Faulted, message, null, null);
                return;
            }

            _logService.Info("Coordinator", $"已选择目标设备：{targetDevice.Model} ({targetDevice.Serial})。应用运行={targetDevice.IsAudioAppRunning}。 ");

            var forwardResult = await _adbService.EnsurePortForwardAsync(targetDevice.Serial, options.LocalPort, options.RemotePort, cancellationToken);
            if (!forwardResult.IsSuccess)
            {
                UpdateStatus(StreamingState.Faulted, forwardResult.StatusMessage, targetDevice.Serial, targetDevice.Model);
                return;
            }

            UpdateStatus(StreamingState.Ready, forwardResult.StatusMessage, targetDevice.Serial, targetDevice.Model);
        }
        catch (OperationCanceledException)
        {
            _logService.Warning("Coordinator", "准备链路已取消。 ");
            UpdateStatus(StreamingState.Idle, "准备链路已取消。", Status.TargetDeviceSerial, Status.TargetDeviceName);
        }
        catch (Exception ex)
        {
            _logService.Error("Coordinator", $"准备链路失败：{ex.Message}");
            UpdateStatus(StreamingState.Faulted, $"准备链路失败：{ex.Message}", Status.TargetDeviceSerial, Status.TargetDeviceName);
        }
    }

    public async Task StartStreamingAsync(CancellationToken cancellationToken = default, bool requireAudioAppRunning = false)
    {
        await _lifecycleLock.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            await StartStreamingCoreAsync(cancellationToken, requireAudioAppRunning).ConfigureAwait(false);
        }
        finally
        {
            _lifecycleLock.Release();
        }
    }

    private async Task StartStreamingCoreAsync(CancellationToken cancellationToken, bool requireAudioAppRunning)
    {
        try
        {
            if (Status.State is StreamingState.Idle or StreamingState.Faulted)
            {
                await PrepareAsync(cancellationToken, requireAudioAppRunning);
            }

            if (Status.State != StreamingState.Ready)
            {
                return;
            }

            var options = BuildOptions();
            _logService.Info("Coordinator", "开始启动推流。 ");
            UpdateStatus(StreamingState.Preparing, "正在建立 TCP 连接并发送初始化消息...", Status.TargetDeviceSerial, Status.TargetDeviceName);
            await _audioTransportService.ConnectAsync("127.0.0.1", options.LocalPort, cancellationToken);
            await _audioTransportService.SendSessionHeaderAsync(options, cancellationToken);
            await SendVolumeCatalogSnapshotAsync(0, includeIconsInline: true, cancellationToken);
            _sequence = 0;
            _isStopping = false;
            _frameSendFaulted = false;
            MarkTransportActivity();
            MarkInboundActivity();
            MarkAndroidPlaybackStatusReceived();
            _androidPlaybackStatusCount = 0;
            _consecutiveAbnormalPlaybackStatusCount = 0;
            _heartbeatCount = 0;
            CancelReconnectLoop();
            await CancelHeartbeatLoopAsync();
            StartHeartbeatLoop();
            _audioCaptureService.Start();
            UpdateStatus(StreamingState.Streaming, "推流链路已建立，正在发送音频帧。", Status.TargetDeviceSerial, Status.TargetDeviceName);
        }
        catch (OperationCanceledException)
        {
            _logService.Warning("Coordinator", "启动推流已取消。 ");
            UpdateStatus(StreamingState.Idle, "启动推流已取消。", Status.TargetDeviceSerial, Status.TargetDeviceName);
        }
        catch (Exception ex)
        {
            await CancelHeartbeatLoopAsync();
            await _audioTransportService.DisconnectAsync();
            _audioCaptureService.Stop();
            _logService.Error("Coordinator", $"启动推流失败：{ex.Message}");
            UpdateStatus(StreamingState.Faulted, $"启动推流失败：{ex.Message}", Status.TargetDeviceSerial, Status.TargetDeviceName);
        }
    }

    public async Task StopStreamingAsync()
    {
        await _lifecycleLock.WaitAsync().ConfigureAwait(false);
        try
        {
            await StopStreamingCoreAsync().ConfigureAwait(false);
        }
        finally
        {
            _lifecycleLock.Release();
        }
    }

    private async Task StopStreamingCoreAsync()
    {
        if (_isStopping)
        {
            return;
        }

        try
        {
            _isStopping = true;
            CancelReconnectLoop();
            await CancelHeartbeatLoopAsync();
            _frameSendFaulted = true;
            await Task.Run(() => _audioCaptureService.Stop());
            await _audioTransportService.DisconnectAsync();
            _logService.Info("Coordinator", "推流已停止。 ");
            UpdateStatus(StreamingState.Idle, "推流已停止。", Status.TargetDeviceSerial, Status.TargetDeviceName);
        }
        catch (Exception ex)
        {
            _logService.Error("Coordinator", $"停止推流失败：{ex.Message}");
            UpdateStatus(StreamingState.Faulted, $"停止推流失败：{ex.Message}", Status.TargetDeviceSerial, Status.TargetDeviceName);
        }
        finally
        {
            _isStopping = false;
        }
    }

    public void Dispose()
    {
        _audioCaptureService.AudioFrameCaptured -= OnAudioFrameCaptured;
        _audioTransportService.MessageReceived -= OnTransportMessageReceived;
        _audioTransportService.ConnectionLost -= OnTransportConnectionLost;
        _windowsVolumeService.SnapshotChanged -= OnWindowsVolumeSnapshotChanged;
        CancelReconnectLoop();
        CancelHeartbeatLoopAsync().ConfigureAwait(false).GetAwaiter().GetResult();
        _audioCaptureService.Dispose();
        _audioTransportService.DisposeAsync().AsTask().ConfigureAwait(false).GetAwaiter().GetResult();
        _lifecycleLock.Dispose();
    }

    public async ValueTask DisposeAsync()
    {
        _audioCaptureService.AudioFrameCaptured -= OnAudioFrameCaptured;
        _audioTransportService.MessageReceived -= OnTransportMessageReceived;
        _audioTransportService.ConnectionLost -= OnTransportConnectionLost;
        _windowsVolumeService.SnapshotChanged -= OnWindowsVolumeSnapshotChanged;
        CancelReconnectLoop();
        await CancelHeartbeatLoopAsync().ConfigureAwait(false);
        _audioCaptureService.Dispose();
        await _audioTransportService.DisposeAsync().ConfigureAwait(false);
        _lifecycleLock.Dispose();
    }

    public async Task AutoConnectIfPossibleAsync(string reason, bool restartIfRunning)
    {
        await _lifecycleLock.WaitAsync().ConfigureAwait(false);
        try
        {
            if (restartIfRunning && Status.State is StreamingState.Streaming or StreamingState.Preparing or StreamingState.Ready)
            {
                _logService.Info("Coordinator", $"{reason}：检测到当前链路活动，准备重建连接。 ");
                await StopStreamingCoreAsync().ConfigureAwait(false);
            }

            if (Status.State is StreamingState.Streaming or StreamingState.Preparing)
            {
                return;
            }

            _logService.Info("Coordinator", $"{reason}：开始检查是否满足自动连接条件。 ");
            await StartStreamingCoreAsync(CancellationToken.None, requireAudioAppRunning: true).ConfigureAwait(false);
        }
        finally
        {
            _lifecycleLock.Release();
        }
    }

    public async Task RestartAudioAsync(string reason)
    {
        await _lifecycleLock.WaitAsync().ConfigureAwait(false);
        try
        {
            _logService.Info("Coordinator", $"{reason}：准备重启音频链路。 ");
            if (Status.State is StreamingState.Streaming or StreamingState.Preparing or StreamingState.Ready)
            {
                await StopStreamingCoreAsync().ConfigureAwait(false);
            }

            await StartStreamingCoreAsync(CancellationToken.None, requireAudioAppRunning: true).ConfigureAwait(false);
        }
        finally
        {
            _lifecycleLock.Release();
        }
    }

    private async void OnTransportConnectionLost(object? sender, EventArgs e)
    {
        if (_isStopping)
        {
            return;
        }

        await HandleTransportSendFailureAsync("接收链路检测到连接断开", new IOException("远端已关闭连接。"));
    }

    private async void OnTransportMessageReceived(object? sender, TransportMessageReceivedEventArgs e)
    {
        // 收到任意对端消息都视为链路存活信号（含 HeartbeatAck 与音量控制消息）。
        MarkInboundActivity();

        try
        {
            switch (e.MessageType)
            {
                case BridgeMessageType.VolumeCatalogRequest:
                    await HandleVolumeCatalogRequestAsync(e.GetPayloadAsUtf8());
                    break;
                case BridgeMessageType.VolumeSetMasterRequest:
                    await HandleSetMasterVolumeRequestAsync(e.GetPayloadAsUtf8());
                    break;
                case BridgeMessageType.VolumeSetSessionRequest:
                    await HandleSetSessionVolumeRequestAsync(e.GetPayloadAsUtf8());
                    break;
                case BridgeMessageType.AndroidPlaybackStatus:
                    await HandleAndroidPlaybackStatusAsync(e.GetPayloadAsUtf8());
                    break;
            }
        }
        catch (Exception ex)
        {
            _logService.Error("Coordinator", $"处理 Android 控制消息失败：{ex.Message}");
        }
    }

    private async void OnWindowsVolumeSnapshotChanged(object? sender, EventArgs e)
    {
        if (!_audioTransportService.IsConnected || _isStopping)
        {
            return;
        }

        try
        {
            await SendVolumeCatalogSnapshotAsync(0, includeIconsInline: true, CancellationToken.None);
        }
        catch (Exception ex)
        {
            _logService.Warning("Coordinator", $"推送 Windows 音量快照失败：{ex.Message}");
        }
    }

    private StreamingSessionOptions BuildOptions()
    {
        var settings = _settingsService.Current;
        var captureFormat = _audioCaptureService.GetDefaultLoopbackFormat();
        var detectedEncoding = DetectEncoding(captureFormat);
        var transportEncoding = "PCM16";

        if (!string.Equals(settings.Encoding, detectedEncoding, StringComparison.OrdinalIgnoreCase) ||
            settings.SampleRate != captureFormat.SampleRate ||
            settings.Channels != captureFormat.Channels)
        {
            _logService.Warning(
                "Coordinator",
                $"设置值与实际回环采集格式不一致，将按实际格式发送：设置={settings.Encoding}/{settings.SampleRate}Hz/{settings.Channels}ch，实际={detectedEncoding}/{captureFormat.SampleRate}Hz/{captureFormat.Channels}ch/{captureFormat.BitsPerSample}bit。"
            );
        }

        if (!string.Equals(detectedEncoding, transportEncoding, StringComparison.OrdinalIgnoreCase))
        {
            _logService.Info(
                "Coordinator",
                $"检测到默认输出为 {detectedEncoding}，为兼容 Android 播放链路，将在发送前实时转换为 {transportEncoding}。"
            );
        }

        var options = new StreamingSessionOptions
        {
            Encoding = transportEncoding,
            SampleRate = captureFormat.SampleRate,
            Channels = captureFormat.Channels,
            BufferMilliseconds = settings.BufferMilliseconds,
            AndroidAppPackageName = settings.AndroidAppPackageName,
            PreferredDeviceSerial = settings.PreferredDeviceSerial,
            LocalPort = 5000,
            RemotePort = 5000
        };

        _activeCaptureFormat = captureFormat;
        _activeSessionOptions = options;
        return options;
    }

    private void OnAudioFrameCaptured(object? sender, AudioFrameCapturedEventArgs e)
    {
        if (!_audioTransportService.IsConnected)
        {
            return;
        }

        if (_isStopping)
        {
            return;
        }

        if (e.BytesRecorded <= 0)
        {
            _logService.Warning("Coordinator", "检测到零长度音频帧，已跳过发送。");
            return;
        }

        var transportFrame = NormalizeAudioFrameForTransport(e.Buffer, e.BytesRecorded);
        if (transportFrame.BytesRecorded <= 0)
        {
            _logService.Warning("Coordinator", "音频帧转换后长度为 0，已跳过发送。");
            return;
        }

        _sequence++;
        _ = ForwardAudioFrameAsync(transportFrame.Buffer, transportFrame.BytesRecorded, _sequence);
    }

    private async Task ForwardAudioFrameAsync(byte[] buffer, int bytesRecorded, uint sequence)
    {
        if (_isStopping)
        {
            return;
        }

        try
        {
            await _audioTransportService.SendAudioFrameAsync(buffer, bytesRecorded, sequence);
            MarkTransportActivity();
        }
        catch (Exception ex)
        {
            await HandleTransportSendFailureAsync("发送音频帧失败", ex);
        }
    }

    internal static bool ShouldSendHeartbeat(DateTime nowUtc, DateTime lastTransportActivityUtc, TimeSpan idleThreshold)
    {
        if (lastTransportActivityUtc == default)
        {
            return false;
        }

        return nowUtc - lastTransportActivityUtc >= idleThreshold;
    }

    private void StartHeartbeatLoop()
    {
        _heartbeatCancellationTokenSource = new CancellationTokenSource();
        _heartbeatTask = Task.Run(() => HeartbeatLoopAsync(_heartbeatCancellationTokenSource.Token));
    }

    private async Task CancelHeartbeatLoopAsync()
    {
        var cancellationTokenSource = _heartbeatCancellationTokenSource;
        var heartbeatTask = _heartbeatTask;

        _heartbeatCancellationTokenSource = null;
        _heartbeatTask = null;

        if (cancellationTokenSource is null)
        {
            return;
        }

        cancellationTokenSource.Cancel();
        cancellationTokenSource.Dispose();

        if (heartbeatTask is null)
        {
            return;
        }

        if (heartbeatTask.Id == Task.CurrentId)
        {
            return;
        }

        try
        {
            await heartbeatTask.ConfigureAwait(false);
        }
        catch (OperationCanceledException)
        {
            // ignore
        }
    }

    private async Task HeartbeatLoopAsync(CancellationToken cancellationToken)
    {
        using var timer = new PeriodicTimer(HeartbeatIdleThreshold);

        try
        {
            while (await timer.WaitForNextTickAsync(cancellationToken))
            {
                if (!_audioTransportService.IsConnected || _isStopping)
                {
                    continue;
                }

                // 接收侧存活判定：即使本地 stream.WriteAsync 在 adb 假存活下持续“成功”，
                // 只要超过 LivenessTimeout 未收到任何对端消息（含 HeartbeatAck），即判定链路死亡。
                var lastInboundUtc = new DateTime(Interlocked.Read(ref _lastInboundActivityUtcTicks), DateTimeKind.Utc);
                if (IsInboundLinkDead(DateTime.UtcNow, lastInboundUtc, LivenessTimeout))
                {
                    await HandleTransportSendFailureAsync(
                        "接收侧心跳超时，判定链路断开",
                        new IOException($"超过 {LivenessTimeout.TotalSeconds:0} 秒未收到对端任何消息。"));
                    return;
                }

                var lastPlaybackStatusUtc = new DateTime(Interlocked.Read(ref _lastAndroidPlaybackStatusUtcTicks), DateTimeKind.Utc);
                if (ShouldMonitorAndroidPlaybackStatus(Status.State, _audioTransportService.IsConnected, Status.TargetDeviceSerial) &&
                    IsAndroidPlaybackStatusExpired(DateTime.UtcNow, lastPlaybackStatusUtc, AndroidPlaybackStatusTimeout))
                {
                    await HandleTransportSendFailureAsync(
                        "Android 播放状态心跳超时，准备重建音频链路",
                        new IOException($"超过 {AndroidPlaybackStatusTimeout.TotalSeconds:0} 秒未收到 Android 播放状态。"));
                    return;
                }

                try
                {
                    // 每个 tick 都发送心跳以触发对端回送 HeartbeatAck，从而维持接收侧存活信号
                    // （即使音频正在持续发送，对端在纯播放时不会主动回包）。
                    await _audioTransportService.SendHeartbeatAsync(cancellationToken);
                    MarkTransportActivity();
                    _heartbeatCount++;
                    if (_heartbeatCount == 1 || _heartbeatCount % 12 == 0)
                    {
                        _logService.Info("Coordinator", $"已发送保活心跳：count={_heartbeatCount}。 ");
                    }
                }
                catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
                {
                    return;
                }
                catch (Exception ex)
                {
                    await HandleTransportSendFailureAsync("发送保活心跳失败", ex);
                    return;
                }
            }
        }
        catch (OperationCanceledException)
        {
            // ignore
        }
    }

    private void MarkTransportActivity()
    {
        Interlocked.Exchange(ref _lastTransportActivityUtcTicks, DateTime.UtcNow.Ticks);
    }

    private void MarkInboundActivity()
    {
        Interlocked.Exchange(ref _lastInboundActivityUtcTicks, DateTime.UtcNow.Ticks);
    }

    private void MarkAndroidPlaybackStatusReceived()
    {
        Interlocked.Exchange(ref _lastAndroidPlaybackStatusUtcTicks, DateTime.UtcNow.Ticks);
    }

    internal static bool IsInboundLinkDead(DateTime nowUtc, DateTime lastInboundActivityUtc, TimeSpan timeout)
    {
        if (lastInboundActivityUtc == default)
        {
            return false;
        }

        return nowUtc - lastInboundActivityUtc >= timeout;
    }

    internal static bool IsAndroidPlaybackStatusExpired(DateTime nowUtc, DateTime lastStatusUtc, TimeSpan timeout)
    {
        if (lastStatusUtc == default)
        {
            return false;
        }

        return nowUtc - lastStatusUtc >= timeout;
    }

    internal static bool ShouldMonitorAndroidPlaybackStatus(StreamingState state, bool isTransportConnected, string? targetDeviceSerial)
    {
        if (!isTransportConnected)
        {
            return false;
        }

        return state is StreamingState.Streaming or StreamingState.Preparing or StreamingState.Ready ||
               !string.IsNullOrWhiteSpace(targetDeviceSerial);
    }

    internal static bool IsAndroidPlaybackStatusAbnormal(bool isPlaying, long? bufferedLatencyMillis, TimeSpan latencyThreshold)
    {
        if (!isPlaying)
        {
            return true;
        }

        return bufferedLatencyMillis is not null && bufferedLatencyMillis.Value >= latencyThreshold.TotalMilliseconds;
    }

    internal static bool ShouldRestartForAbnormalPlayback(int consecutiveAbnormalCount, int restartThreshold)
    {
        return restartThreshold > 0 && consecutiveAbnormalCount >= restartThreshold;
    }

    private async Task HandleTransportSendFailureAsync(string operation, Exception ex)
    {
        if (_frameSendFaulted || _isStopping)
        {
            return;
        }

        _frameSendFaulted = true;
        _logService.Error("Coordinator", $"{operation}：{ex.Message}");
        _audioCaptureService.Stop();
        await CancelHeartbeatLoopAsync();
        await _audioTransportService.DisconnectAsync();
        UpdateStatus(StreamingState.Faulted, $"{operation}：{ex.Message}", Status.TargetDeviceSerial, Status.TargetDeviceName);
        ScheduleReconnect("音频链路断开，准备自动重连。");
    }

    private void ScheduleReconnect(string reason)
    {
        if (!_settingsService.Current.EnableAutoReconnect)
        {
            _logService.Info("Coordinator", $"{reason} 但用户已关闭自动重连。 ");
            return;
        }

        if (_reconnectTask is { IsCompleted: false })
        {
            return;
        }

        _logService.Warning("Coordinator", reason);
        _reconnectCancellationTokenSource?.Dispose();
        _reconnectCancellationTokenSource = new CancellationTokenSource();
        _reconnectTask = Task.Run(() => ReconnectLoopAsync(_reconnectCancellationTokenSource.Token));
    }

    private async Task ReconnectLoopAsync(CancellationToken cancellationToken)
    {
        var attempt = 0;

        while (!cancellationToken.IsCancellationRequested)
        {
            attempt++;

            try
            {
                _logService.Info("Coordinator", $"开始第 {attempt} 次自动重连尝试。 ");
                await StartStreamingAsync(cancellationToken, requireAudioAppRunning: true);

                if (Status.State == StreamingState.Streaming)
                {
                    _logService.Info("Coordinator", "自动重连成功。 ");
                    return;
                }
            }
            catch (OperationCanceledException)
            {
                return;
            }
            catch (Exception ex)
            {
                _logService.Warning("Coordinator", $"自动重连尝试失败：{ex.Message}");
            }

            await Task.Delay(ReconnectDelay, cancellationToken);
        }
    }

    private void CancelReconnectLoop()
    {
        _reconnectCancellationTokenSource?.Cancel();
        _reconnectCancellationTokenSource?.Dispose();
        _reconnectCancellationTokenSource = null;
        _reconnectTask = null;
    }

    private async Task HandleVolumeCatalogRequestAsync(string json)
    {
        var request = JsonSerializer.Deserialize<VolumeCatalogRequestPayload>(json, _jsonOptions) ?? new VolumeCatalogRequestPayload();
        _logService.Info("Coordinator", $"收到音量目录请求：requestId={request.RequestId}，inlineIcons={request.IncludeIconsInline}。 ");
        await SendVolumeCatalogSnapshotAsync(request.RequestId, request.IncludeIconsInline, CancellationToken.None);
    }

    private async Task HandleSetMasterVolumeRequestAsync(string json)
    {
        var request = JsonSerializer.Deserialize<VolumeSetMasterRequestPayload>(json, _jsonOptions) ?? new VolumeSetMasterRequestPayload();
        var requestId = request.RequestId;

        if (request.Volume is not null)
        {
            if (!_windowsVolumeService.TrySetMasterVolume(request.Volume.Value, out var errorMessage))
            {
                await SendCommandAckAsync(requestId, false, 1, errorMessage, masterVolume: null, session: null, catalog: null, CancellationToken.None);
                return;
            }
        }

        if (request.HasMute && request.Mute is not null)
        {
            if (!_windowsVolumeService.TrySetMasterMute(request.Mute.Value, out var errorMessage))
            {
                await SendCommandAckAsync(requestId, false, 2, errorMessage, masterVolume: null, session: null, catalog: null, CancellationToken.None);
                return;
            }
        }

        await SendCommandAckAsync(
            requestId,
            true,
            0,
            "Windows 主音量已更新。",
            masterVolume: BuildMasterVolumeDto(_windowsVolumeService.Current.MasterVolume),
            session: null,
            catalog: null,
            CancellationToken.None);
    }

    private async Task HandleSetSessionVolumeRequestAsync(string json)
    {
        var request = JsonSerializer.Deserialize<VolumeSetSessionRequestPayload>(json, _jsonOptions) ?? new VolumeSetSessionRequestPayload();
        var requestId = request.RequestId;

        if (string.IsNullOrWhiteSpace(request.SessionId))
        {
            await SendCommandAckAsync(requestId, false, 3, "缺少目标应用会话标识。", null, null, null, CancellationToken.None);
            return;
        }

        if (request.Volume is not null)
        {
            if (!_windowsVolumeService.TrySetSessionVolume(request.SessionId, request.Volume.Value, out var errorMessage))
            {
                await SendCommandAckAsync(requestId, false, 4, errorMessage, null, null, null, CancellationToken.None);
                return;
            }
        }

        if (request.HasMute && request.Mute is not null)
        {
            if (!_windowsVolumeService.TrySetSessionMute(request.SessionId, request.Mute.Value, out var errorMessage))
            {
                await SendCommandAckAsync(requestId, false, 5, errorMessage, null, null, null, CancellationToken.None);
                return;
            }
        }

        var session = _windowsVolumeService.Current.Sessions.FirstOrDefault(x => string.Equals(x.SessionId, request.SessionId, StringComparison.Ordinal));
        await SendCommandAckAsync(
            requestId,
            true,
            0,
            "Windows 应用音量已更新。",
            masterVolume: null,
            session: session is null ? null : BuildSessionDto(session, includeIconsInline: true),
            catalog: null,
            CancellationToken.None);
    }

    private async Task HandleAndroidPlaybackStatusAsync(string json)
    {
        var status = JsonSerializer.Deserialize<AndroidPlaybackStatusPayload>(json, _jsonOptions) ?? new AndroidPlaybackStatusPayload();
        MarkAndroidPlaybackStatusReceived();
        _androidPlaybackStatusCount++;

        await _audioTransportService.SendAndroidPlaybackStatusAckAsync(
            status.Sequence,
            status.TimestampElapsedRealtimeMillis,
            CancellationToken.None);
        MarkTransportActivity();

        var isAbnormal = IsAndroidPlaybackStatusAbnormal(
            status.IsPlaying,
            status.BufferedLatencyMillis,
            AndroidPlaybackLatencyThreshold);
        _consecutiveAbnormalPlaybackStatusCount = isAbnormal ? _consecutiveAbnormalPlaybackStatusCount + 1 : 0;

        if (_androidPlaybackStatusCount == 1 || _androidPlaybackStatusCount % 10 == 0 || isAbnormal)
        {
            var latencyText = status.BufferedLatencyMillis?.ToString() ?? "unknown";
            var ageText = status.LastAudioFrameAgeMillis?.ToString() ?? "unknown";
            _logService.Info(
                "Coordinator",
                $"收到 Android 播放状态：sequence={status.Sequence}，playing={status.IsPlaying}，lastAudio={status.LastSequence}，age={ageText}ms，latency={latencyText}ms，abnormalCount={_consecutiveAbnormalPlaybackStatusCount}。 ");
        }

        if (ShouldRestartForAbnormalPlayback(_consecutiveAbnormalPlaybackStatusCount, AndroidPlaybackAbnormalRestartThreshold))
        {
            await HandleTransportSendFailureAsync(
                "Android 连续上报播放异常，准备重建音频链路",
                new IOException($"连续 {_consecutiveAbnormalPlaybackStatusCount} 次收到异常播放状态。"));
        }
    }

    private async Task SendVolumeCatalogSnapshotAsync(uint requestId, bool includeIconsInline, CancellationToken cancellationToken)
    {
        var snapshot = _windowsVolumeService.Current;
        var payload = new
        {
            requestId,
            catalog = BuildCatalogDto(snapshot, requestId, includeIconsInline)
        };

        await _audioTransportService.SendJsonControlMessageAsync(
            BridgeMessageType.VolumeCatalogSnapshot,
            JsonSerializer.Serialize(payload, _jsonOptions),
            cancellationToken);
        MarkTransportActivity();
    }

    private async Task SendCommandAckAsync(
        uint requestId,
        bool success,
        int errorCode,
        string message,
        object? masterVolume,
        object? session,
        object? catalog,
        CancellationToken cancellationToken)
    {
        var payload = new
        {
            requestId,
            success,
            errorCode,
            message,
            masterVolume,
            session,
            catalog
        };

        await _audioTransportService.SendJsonControlMessageAsync(
            BridgeMessageType.CommandAck,
            JsonSerializer.Serialize(payload, _jsonOptions),
            cancellationToken);
        MarkTransportActivity();
    }

    private object BuildCatalogDto(WindowsVolumeSnapshot snapshot, uint requestId, bool includeIconsInline)
    {
        return new
        {
            requestId,
            capturedAtMillis = snapshot.CapturedAtUtc.ToUnixTimeMilliseconds(),
            masterVolume = BuildMasterVolumeDto(snapshot.MasterVolume),
            sessions = snapshot.Sessions.Select(session => BuildSessionDto(session, includeIconsInline)).ToArray()
        };
    }

    private static object BuildMasterVolumeDto(WindowsMasterVolumeState masterVolume)
    {
        return new
        {
            deviceId = masterVolume.DeviceId,
            deviceName = masterVolume.DeviceName,
            volume = masterVolume.Volume,
            isMuted = masterVolume.IsMuted,
            capturedAtMillis = masterVolume.CapturedAtUtc.ToUnixTimeMilliseconds()
        };
    }

    private object BuildSessionDto(WindowsAppVolumeSession session, bool includeIconsInline)
    {
        return new
        {
            sessionId = session.SessionId,
            processId = session.ProcessId,
            processName = session.ProcessName,
            displayName = session.DisplayName,
            state = session.State,
            volume = session.Volume,
            isMuted = session.IsMuted,
            iconKey = session.IconKey,
            iconHash = session.IconHash,
            iconBase64 = includeIconsInline ? TryGetInlineIconBase64(session) : null
        };
    }

    private string? TryGetInlineIconBase64(WindowsAppVolumeSession session)
    {
        var bytes = _volumeIconService.TryGetPngBytes(session.IconPath, session.ExecutablePath);
        return bytes is null ? null : Convert.ToBase64String(bytes);
    }

    private void UpdateStatus(StreamingState state, string statusMessage, string? serial, string? name)
    {
        _logService.Info("Status", $"状态变更：{GetStateText(state)} - {statusMessage}");
        Status = new StreamingStatusSnapshot
        {
            State = state,
            StatusMessage = statusMessage,
            TargetDeviceSerial = serial,
            TargetDeviceName = name,
            IsTransportConnected = _audioTransportService.IsConnected,
            IsCapturing = _audioCaptureService.IsCapturing
        };

        StatusChanged?.Invoke(this, EventArgs.Empty);
    }

    private static string GetStateText(StreamingState state) => state switch
    {
        StreamingState.Preparing => "准备中",
        StreamingState.Ready => "已就绪",
        StreamingState.Streaming => "推流中",
        StreamingState.Faulted => "异常",
        _ => "空闲"
    };

    private static string GetPreferredDeviceText(string? preferredDeviceSerial)
    {
        return string.IsNullOrWhiteSpace(preferredDeviceSerial) ? "自动选择" : preferredDeviceSerial;
    }

    internal static AndroidDeviceInfo? SelectTargetDevice(IReadOnlyList<AndroidDeviceInfo> devices, string? preferredDeviceSerial, bool requireAudioAppRunning)
    {
        var onlineDevices = devices.Where(x => string.Equals(x.State, "Online", StringComparison.OrdinalIgnoreCase)).ToList();
        if (onlineDevices.Count == 0)
        {
            return null;
        }

        if (!string.IsNullOrWhiteSpace(preferredDeviceSerial))
        {
            var preferred = onlineDevices.FirstOrDefault(x => string.Equals(x.Serial, preferredDeviceSerial, StringComparison.OrdinalIgnoreCase));
            if (preferred is not null && (!requireAudioAppRunning || preferred.IsAudioAppRunning))
            {
                return preferred;
            }
        }

        if (requireAudioAppRunning)
        {
            return onlineDevices.FirstOrDefault(x => x.IsAudioAppRunning);
        }

        return onlineDevices.FirstOrDefault(x => x.IsAudioAppRunning)
               ?? onlineDevices.FirstOrDefault()
               ?? devices.FirstOrDefault();
    }

    private static string DetectEncoding(NAudio.Wave.WaveFormat waveFormat)
    {
        if (waveFormat.Encoding == NAudio.Wave.WaveFormatEncoding.IeeeFloat && waveFormat.BitsPerSample == 32)
        {
            return "Float32";
        }

        if (waveFormat.Encoding == NAudio.Wave.WaveFormatEncoding.Pcm && waveFormat.BitsPerSample == 16)
        {
            return "PCM16";
        }

        throw new NotSupportedException(
            $"当前默认输出设备回环格式不受支持：Encoding={waveFormat.Encoding}, SampleRate={waveFormat.SampleRate}, Channels={waveFormat.Channels}, BitsPerSample={waveFormat.BitsPerSample}。当前仅支持 PCM16 和 Float32。"
        );
    }

    private (byte[] Buffer, int BytesRecorded) NormalizeAudioFrameForTransport(byte[] buffer, int bytesRecorded)
    {
        var captureFormat = _activeCaptureFormat ?? _audioCaptureService.GetDefaultLoopbackFormat();
        var transportOptions = _activeSessionOptions;

        if (transportOptions is null)
        {
            return (buffer, bytesRecorded);
        }

        if (captureFormat.Encoding == NAudio.Wave.WaveFormatEncoding.Pcm && captureFormat.BitsPerSample == 16)
        {
            return (buffer, bytesRecorded);
        }

        if (captureFormat.Encoding == NAudio.Wave.WaveFormatEncoding.IeeeFloat && captureFormat.BitsPerSample == 32 &&
            string.Equals(transportOptions.Encoding, "PCM16", StringComparison.OrdinalIgnoreCase))
        {
            return ConvertFloat32ToPcm16(buffer, bytesRecorded);
        }

        throw new NotSupportedException(
            $"当前音频帧转换路径不受支持：Capture={captureFormat.Encoding}/{captureFormat.BitsPerSample}bit, Transport={transportOptions.Encoding}/{transportOptions.BitsPerSample}bit。"
        );
    }

    private static (byte[] Buffer, int BytesRecorded) ConvertFloat32ToPcm16(byte[] buffer, int bytesRecorded)
    {
        var sampleCount = bytesRecorded / sizeof(float);
        var pcm16Buffer = new byte[sampleCount * sizeof(short)];

        for (var i = 0; i < sampleCount; i++)
        {
            var sample = BitConverter.ToSingle(buffer, i * sizeof(float));
            sample = Math.Clamp(sample, -1.0f, 1.0f);
            var pcm16 = (short)Math.Round(sample * short.MaxValue);
            BinaryPrimitives.WriteInt16LittleEndian(pcm16Buffer.AsSpan(i * sizeof(short), sizeof(short)), pcm16);
        }

        return (pcm16Buffer, pcm16Buffer.Length);
    }

    private sealed class VolumeCatalogRequestPayload
    {
        public uint RequestId { get; init; }

        public bool IncludeIconsInline { get; init; }
    }

    private sealed class VolumeSetMasterRequestPayload
    {
        public uint RequestId { get; init; }

        public float? Volume { get; init; }

        public bool HasMute { get; init; }

        public bool? Mute { get; init; }
    }

    private sealed class VolumeSetSessionRequestPayload
    {
        public uint RequestId { get; init; }

        public string SessionId { get; init; } = string.Empty;

        public float? Volume { get; init; }

        public bool HasMute { get; init; }

        public bool? Mute { get; init; }
    }

    private sealed class AndroidPlaybackStatusPayload
    {
        public uint Sequence { get; init; }

        public bool IsPlaying { get; init; }

        public uint LastSequence { get; init; }

        public long? LastAudioFrameAgeMillis { get; init; }

        public long? BufferedLatencyMillis { get; init; }

        public long TimestampElapsedRealtimeMillis { get; init; }
    }
}
