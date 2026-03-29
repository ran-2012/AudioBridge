using System.Diagnostics;
using System.IO;
using NAudio.CoreAudioApi;
using WpfApp1.Models;

namespace WpfApp1.Services;

public sealed class WindowsVolumeService : IDisposable
{
    private readonly AppLogService _logService;
    private readonly VolumeIconService _iconService;
    private readonly object _syncRoot = new();
    private CancellationTokenSource? _monitorCancellationTokenSource;
    private Task? _monitorTask;
    private string _lastSnapshotSignature = string.Empty;

    public WindowsVolumeService(AppLogService logService, VolumeIconService iconService)
    {
        _logService = logService;
        _iconService = iconService;
        TryRefreshSnapshot();
    }

    public WindowsVolumeSnapshot Current { get; private set; } = WindowsVolumeSnapshot.Empty;

    public bool IsMonitoring => _monitorTask is { IsCompleted: false };

    public event EventHandler? SnapshotChanged;

    public bool TryRefreshSnapshot()
    {
        try
        {
            var snapshot = CaptureSnapshot();
            UpdateSnapshot(snapshot, "已刷新 Windows 音量快照。", logIfUnchanged: false);
            return true;
        }
        catch (Exception ex)
        {
            _logService.Error("Volume", $"刷新 Windows 音量快照失败：{ex.Message}");
            return false;
        }
    }

    public void StartMonitoring(TimeSpan? interval = null)
    {
        lock (_syncRoot)
        {
            if (IsMonitoring)
            {
                return;
            }

            _monitorCancellationTokenSource = new CancellationTokenSource();
            _monitorTask = MonitorLoopAsync(interval ?? TimeSpan.FromSeconds(1.5), _monitorCancellationTokenSource.Token);
            _logService.Info("Volume", "Windows 音量监控已启动。 ");
        }
    }

    public async Task StopMonitoringAsync()
    {
        CancellationTokenSource? cancellationTokenSource;
        Task? monitorTask;

        lock (_syncRoot)
        {
            cancellationTokenSource = _monitorCancellationTokenSource;
            monitorTask = _monitorTask;
            _monitorCancellationTokenSource = null;
            _monitorTask = null;
        }

        if (cancellationTokenSource is null)
        {
            return;
        }

        cancellationTokenSource.Cancel();

        if (monitorTask is not null)
        {
            try
            {
                await monitorTask;
            }
            catch (OperationCanceledException)
            {
                // ignore
            }
        }

        cancellationTokenSource.Dispose();
        _logService.Info("Volume", "Windows 音量监控已停止。 ");
    }

    public bool TrySetMasterVolume(float volume, out string errorMessage)
    {
        try
        {
            using var enumerator = new MMDeviceEnumerator();
            using var device = enumerator.GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia);
            device.AudioEndpointVolume.MasterVolumeLevelScalar = ClampVolume(volume);
            UpdateSnapshot(CaptureSnapshot(), "主音量已更新。", logIfUnchanged: true);
            errorMessage = string.Empty;
            return true;
        }
        catch (Exception ex)
        {
            errorMessage = $"设置主音量失败：{ex.Message}";
            _logService.Error("Volume", errorMessage);
            return false;
        }
    }

    public bool TrySetMasterMute(bool isMuted, out string errorMessage)
    {
        try
        {
            using var enumerator = new MMDeviceEnumerator();
            using var device = enumerator.GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia);
            device.AudioEndpointVolume.Mute = isMuted;
            UpdateSnapshot(CaptureSnapshot(), "主静音状态已更新。", logIfUnchanged: true);
            errorMessage = string.Empty;
            return true;
        }
        catch (Exception ex)
        {
            errorMessage = $"设置主静音失败：{ex.Message}";
            _logService.Error("Volume", errorMessage);
            return false;
        }
    }

    public bool TrySetSessionVolume(string sessionId, float volume, out string errorMessage)
    {
        return TryUpdateSession(
            sessionId,
            session => session.SimpleAudioVolume.Volume = ClampVolume(volume),
            "应用音量已更新。",
            out errorMessage);
    }

    public bool TrySetSessionMute(string sessionId, bool isMuted, out string errorMessage)
    {
        return TryUpdateSession(
            sessionId,
            session => session.SimpleAudioVolume.Mute = isMuted,
            "应用静音状态已更新。",
            out errorMessage);
    }

    public void Dispose()
    {
        StopMonitoringAsync().GetAwaiter().GetResult();
    }

    private async Task MonitorLoopAsync(TimeSpan interval, CancellationToken cancellationToken)
    {
        using var timer = new PeriodicTimer(interval);
        while (await timer.WaitForNextTickAsync(cancellationToken))
        {
            try
            {
                var snapshot = CaptureSnapshot();
                UpdateSnapshot(snapshot, "检测到 Windows 音量变化。", logIfUnchanged: false);
            }
            catch (OperationCanceledException)
            {
                throw;
            }
            catch (Exception ex)
            {
                _logService.Warning("Volume", $"轮询 Windows 音量状态失败：{ex.Message}");
            }
        }
    }

    private WindowsVolumeSnapshot CaptureSnapshot()
    {
        using var enumerator = new MMDeviceEnumerator();
        using var device = enumerator.GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia);
        device.AudioSessionManager.RefreshSessions();

        var capturedAt = DateTimeOffset.UtcNow;
        var master = new WindowsMasterVolumeState
        {
            DeviceId = device.ID,
            DeviceName = device.FriendlyName,
            Volume = ClampVolume(device.AudioEndpointVolume.MasterVolumeLevelScalar),
            IsMuted = device.AudioEndpointVolume.Mute,
            CapturedAtUtc = capturedAt
        };

        var sessions = CaptureSessions(device)
            .OrderByDescending(x => string.Equals(x.State, "Active", StringComparison.OrdinalIgnoreCase))
            .ThenBy(x => x.DisplayName, StringComparer.CurrentCultureIgnoreCase)
            .ToArray();

        return new WindowsVolumeSnapshot
        {
            MasterVolume = master,
            Sessions = sessions,
            CapturedAtUtc = capturedAt
        };
    }

    private IEnumerable<WindowsAppVolumeSession> CaptureSessions(MMDevice device)
    {
        var collection = device.AudioSessionManager.Sessions;

        for (var index = 0; index < collection.Count; index++)
        {
            using var session = collection[index];
            if (!TryBuildSession(session, out var item) || item is null)
            {
                continue;
            }

            yield return item;
        }
    }

    private bool TryBuildSession(AudioSessionControl session, out WindowsAppVolumeSession? item)
    {
        item = null;

        try
        {
            if (session.IsSystemSoundsSession)
            {
                return false;
            }

            var sessionState = MapSessionState(session.State.ToString());
            if (string.Equals(sessionState, "Expired", StringComparison.OrdinalIgnoreCase))
            {
                return false;
            }

            var processId = unchecked((int)session.GetProcessID);
            var processName = ResolveProcessName(processId);
            var executablePath = ResolveProcessPath(processId);
            var sessionId = ResolveSessionId(session, processId, processName);
            var displayName = ResolveDisplayName(session, processId, processName, executablePath);
            var iconPath = string.IsNullOrWhiteSpace(session.IconPath) ? null : session.IconPath;
            var iconKey = _iconService.CreateIconKey(iconPath, executablePath, processId, sessionId);
            var iconHash = _iconService.GetIconHash(iconPath, executablePath);

            item = new WindowsAppVolumeSession
            {
                SessionId = sessionId,
                ProcessId = processId,
                ProcessName = processName,
                DisplayName = displayName,
                State = sessionState,
                Volume = ClampVolume(session.SimpleAudioVolume.Volume),
                IsMuted = session.SimpleAudioVolume.Mute,
                IconPath = iconPath,
                ExecutablePath = executablePath,
                IconKey = iconKey,
                IconHash = iconHash
            };

            return true;
        }
        catch (Exception ex)
        {
            _logService.Warning("Volume", $"读取应用音量会话失败：{ex.Message}");
            return false;
        }
    }

    private bool TryUpdateSession(string sessionId, Action<AudioSessionControl> apply, string successMessage, out string errorMessage)
    {
        try
        {
            using var enumerator = new MMDeviceEnumerator();
            using var device = enumerator.GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia);
            device.AudioSessionManager.RefreshSessions();

            var collection = device.AudioSessionManager.Sessions;
            for (var index = 0; index < collection.Count; index++)
            {
                using var session = collection[index];
                if (!string.Equals(ResolveSessionId(session, unchecked((int)session.GetProcessID), ResolveProcessName(unchecked((int)session.GetProcessID))), sessionId, StringComparison.Ordinal))
                {
                    continue;
                }

                apply(session);
                UpdateSnapshot(CaptureSnapshot(), successMessage, logIfUnchanged: true);
                errorMessage = string.Empty;
                return true;
            }

            errorMessage = $"未找到目标应用音量会话：{sessionId}";
            _logService.Warning("Volume", errorMessage);
            return false;
        }
        catch (Exception ex)
        {
            errorMessage = $"更新应用音量会话失败：{ex.Message}";
            _logService.Error("Volume", errorMessage);
            return false;
        }
    }

    private void UpdateSnapshot(WindowsVolumeSnapshot snapshot, string changedMessage, bool logIfUnchanged)
    {
        var signature = BuildSnapshotSignature(snapshot);
        var changed = !string.Equals(signature, _lastSnapshotSignature, StringComparison.Ordinal);

        Current = snapshot;
        _lastSnapshotSignature = signature;

        if (changed)
        {
            _logService.Info(
                "Volume",
                $"{changedMessage} 设备={snapshot.MasterVolume.DeviceName}，主音量={(int)Math.Round(snapshot.MasterVolume.Volume * 100)}%，静音={snapshot.MasterVolume.IsMuted}，会话数={snapshot.Sessions.Count}。"
            );
            SnapshotChanged?.Invoke(this, EventArgs.Empty);
            return;
        }

        if (logIfUnchanged)
        {
            _logService.Info("Volume", changedMessage);
        }
    }

    private static string BuildSnapshotSignature(WindowsVolumeSnapshot snapshot)
    {
        var sessionSignature = string.Join(
            ';',
            snapshot.Sessions.Select(session => string.Join(
                ',',
                session.SessionId,
                session.DisplayName,
                session.State,
                session.Volume.ToString("F4", System.Globalization.CultureInfo.InvariantCulture),
                session.IsMuted,
                session.IconHash)));

        return string.Join(
            '|',
            snapshot.MasterVolume.DeviceId,
            snapshot.MasterVolume.Volume.ToString("F4", System.Globalization.CultureInfo.InvariantCulture),
            snapshot.MasterVolume.IsMuted,
            sessionSignature);
    }

    private static float ClampVolume(float volume) => Math.Clamp(volume, 0f, 1f);

    private static string ResolveSessionId(AudioSessionControl session, int processId, string processName)
    {
        if (!string.IsNullOrWhiteSpace(session.GetSessionInstanceIdentifier))
        {
            return session.GetSessionInstanceIdentifier;
        }

        if (!string.IsNullOrWhiteSpace(session.GetSessionIdentifier))
        {
            return session.GetSessionIdentifier;
        }

        return $"pid:{processId}:{processName}";
    }

    private static string ResolveDisplayName(AudioSessionControl session, int processId, string processName, string? executablePath)
    {
        if (!string.IsNullOrWhiteSpace(session.DisplayName))
        {
            return session.DisplayName.Trim();
        }

        if (TryGetMainWindowTitle(processId, out var windowTitle))
        {
            return windowTitle;
        }

        if (!string.IsNullOrWhiteSpace(executablePath))
        {
            return Path.GetFileNameWithoutExtension(executablePath);
        }

        if (!string.IsNullOrWhiteSpace(processName))
        {
            return processName;
        }

        return $"Process {processId}";
    }

    private static string ResolveProcessName(int processId)
    {
        if (processId <= 0)
        {
            return "System";
        }

        try
        {
            using var process = Process.GetProcessById(processId);
            return process.ProcessName;
        }
        catch
        {
            return $"Process-{processId}";
        }
    }

    private static string? ResolveProcessPath(int processId)
    {
        if (processId <= 0)
        {
            return null;
        }

        try
        {
            using var process = Process.GetProcessById(processId);
            return process.MainModule?.FileName;
        }
        catch
        {
            return null;
        }
    }

    private static bool TryGetMainWindowTitle(int processId, out string title)
    {
        title = string.Empty;
        if (processId <= 0)
        {
            return false;
        }

        try
        {
            using var process = Process.GetProcessById(processId);
            if (string.IsNullOrWhiteSpace(process.MainWindowTitle))
            {
                return false;
            }

            title = process.MainWindowTitle.Trim();
            return true;
        }
        catch
        {
            return false;
        }
    }

    private static string MapSessionState(string rawState)
    {
        return rawState switch
        {
            "AudioSessionStateActive" => "Active",
            "AudioSessionStateInactive" => "Inactive",
            "AudioSessionStateExpired" => "Expired",
            _ => rawState
        };
    }
}