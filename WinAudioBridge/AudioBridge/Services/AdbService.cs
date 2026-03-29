using System.IO;
using AdvancedSharpAdbClient;
using AdvancedSharpAdbClient.DeviceCommands;
using AdvancedSharpAdbClient.Models;
using WpfApp1.Models;

namespace WpfApp1.Services;

public sealed class AdbService
{
    private readonly AdbClient _adbClient = new();
    private readonly IAdbServer _adbServer = AdbServer.Instance;
    private readonly AppLogService _logService;

    public AdbService(AppLogService logService)
    {
        _logService = logService;
    }

    public async Task<AdbPortForwardResult> EnsurePortForwardAsync(string deviceSerial, int localPort, int remotePort, CancellationToken cancellationToken = default)
    {
        return await Task.Run(() => EnsurePortForward(deviceSerial, localPort, remotePort, cancellationToken), cancellationToken);
    }

    public Task<AdbDeviceQueryResult> QueryConnectedDevicesAsync(string androidAppPackageName, CancellationToken cancellationToken = default)
    {
        return Task.Run(() => QueryConnectedDevices(androidAppPackageName, cancellationToken), cancellationToken);
    }

    private AdbPortForwardResult EnsurePortForward(string deviceSerial, int localPort, int remotePort, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(deviceSerial))
        {
            return new AdbPortForwardResult
            {
                IsSuccess = false,
                StatusMessage = "未提供目标设备序列号。",
                LocalPort = localPort,
                RemotePort = remotePort
            };
        }

        var adbPath = ResolveAdbExecutablePath();
        if (adbPath is null)
        {
            return new AdbPortForwardResult
            {
                IsSuccess = false,
                StatusMessage = "未找到 adb.exe，无法建立端口转发。",
                DeviceSerial = deviceSerial,
                LocalPort = localPort,
                RemotePort = remotePort
            };
        }

        try
        {
            cancellationToken.ThrowIfCancellationRequested();

            _logService.Info("ADB", $"准备为设备 {deviceSerial} 建立 tcp:{localPort} -> tcp:{remotePort} 端口转发。");
            _adbServer.StartServer(adbPath, restartServerIfNewer: false);

            var device = _adbClient.GetDevices().FirstOrDefault(x => string.Equals(x.Serial, deviceSerial, StringComparison.OrdinalIgnoreCase));
            if (device is null)
            {
                return new AdbPortForwardResult
                {
                    IsSuccess = false,
                    StatusMessage = $"未找到序列号为 {deviceSerial} 的 Android 设备。",
                    DeviceSerial = deviceSerial,
                    LocalPort = localPort,
                    RemotePort = remotePort
                };
            }

            _adbClient.CreateForward(device, $"tcp:{localPort}", $"tcp:{remotePort}", true);
            _logService.Info("ADB", $"端口转发成功：设备={deviceSerial}，tcp:{localPort} -> tcp:{remotePort}。");

            return new AdbPortForwardResult
            {
                IsSuccess = true,
                StatusMessage = $"已建立端口转发 tcp:{localPort} -> tcp:{remotePort}。",
                DeviceSerial = deviceSerial,
                LocalPort = localPort,
                RemotePort = remotePort
            };
        }
        catch (Exception ex)
        {
            _logService.Error("ADB", $"端口转发失败：{ex.Message}");
            return new AdbPortForwardResult
            {
                IsSuccess = false,
                StatusMessage = $"端口转发失败：{ex.Message}",
                DeviceSerial = deviceSerial,
                LocalPort = localPort,
                RemotePort = remotePort
            };
        }
    }

    private AdbDeviceQueryResult QueryConnectedDevices(string androidAppPackageName, CancellationToken cancellationToken)
    {
        var packageName = androidAppPackageName?.Trim();
        if (string.IsNullOrWhiteSpace(packageName))
        {
            return new AdbDeviceQueryResult
            {
                IsSuccess = false,
                StatusMessage = "请先配置 Android 音频应用包名。"
            };
        }

        var adbPath = ResolveAdbExecutablePath();
        if (adbPath is null)
        {
            return new AdbDeviceQueryResult
            {
                IsSuccess = false,
                StatusMessage = "未找到 adb.exe，请安装 Android Platform Tools 或将 adb 加入 PATH。"
            };
        }

        try
        {
            _logService.Info("ADB", $"启动 adb server，并查询包名 {packageName} 的运行状态。");
            _adbServer.StartServer(adbPath, restartServerIfNewer: false);
            var devices = _adbClient.GetDevices().ToList();
            _logService.Info("ADB", $"检测到 {devices.Count} 台设备，开始读取状态。");

            var items = new List<AndroidDeviceInfo>(devices.Count);
            foreach (var device in devices)
            {
                cancellationToken.ThrowIfCancellationRequested();
                items.Add(CreateDeviceInfo(device, packageName));
            }

            return new AdbDeviceQueryResult
            {
                IsSuccess = true,
                StatusMessage = items.Count == 0
                    ? "当前未检测到已连接的 Android 设备。"
                    : $"已检测到 {items.Count} 台 Android 设备。",
                Devices = items
            };
        }
        catch (Exception ex)
        {
            _logService.Error("ADB", $"设备查询失败：{ex.Message}");
            return new AdbDeviceQueryResult
            {
                IsSuccess = false,
                StatusMessage = $"ADB 查询失败：{ex.Message}"
            };
        }
    }

    private AndroidDeviceInfo CreateDeviceInfo(DeviceData device, string packageName)
    {
        var stateText = device.State.ToString();
        var model = string.IsNullOrWhiteSpace(device.Model) ? "未知设备" : device.Model;
        var isRunning = false;

        try
        {
            var isOffline = string.Equals(stateText, "Offline", StringComparison.OrdinalIgnoreCase);
            if (!isOffline)
            {
                isRunning = new DeviceClient(_adbClient, device).IsAppRunning(packageName);
            }
        }
        catch
        {
            isRunning = false;
        }

        _logService.Info("ADB", $"设备={device.Serial}，型号={model}，状态={stateText}，应用运行={isRunning}。");

        return new AndroidDeviceInfo
        {
            Serial = device.Serial,
            Model = model,
            State = stateText,
            IsAudioAppRunning = isRunning
        };
    }

    private static string? ResolveAdbExecutablePath()
    {
        var candidates = new List<string>();

        AddIfNotEmpty(candidates, Environment.GetEnvironmentVariable("ADB_PATH"));

        var androidSdkRoot = Environment.GetEnvironmentVariable("ANDROID_SDK_ROOT");
        if (!string.IsNullOrWhiteSpace(androidSdkRoot))
        {
            candidates.Add(Path.Combine(androidSdkRoot, "platform-tools", "adb.exe"));
        }

        var androidHome = Environment.GetEnvironmentVariable("ANDROID_HOME");
        if (!string.IsNullOrWhiteSpace(androidHome))
        {
            candidates.Add(Path.Combine(androidHome, "platform-tools", "adb.exe"));
        }

        var pathValue = Environment.GetEnvironmentVariable("PATH");
        if (!string.IsNullOrWhiteSpace(pathValue))
        {
            foreach (var path in pathValue.Split(Path.PathSeparator, StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
            {
                candidates.Add(Path.Combine(path, "adb.exe"));
            }
        }

        return candidates.FirstOrDefault(File.Exists);
    }

    private static void AddIfNotEmpty(ICollection<string> candidates, string? value)
    {
        if (!string.IsNullOrWhiteSpace(value))
        {
            candidates.Add(value);
        }
    }
}
