namespace WpfApp1.Models;

public sealed class AndroidDeviceInfo
{
    public string Serial { get; init; } = string.Empty;

    public string Model { get; init; } = "未知设备";

    public string State { get; init; } = string.Empty;

    public bool IsAudioAppRunning { get; init; }

    public string AppStatusText => IsAudioAppRunning ? "应用运行中" : "未运行";
}
