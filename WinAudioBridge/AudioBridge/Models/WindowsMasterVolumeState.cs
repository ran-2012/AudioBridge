namespace WpfApp1.Models;

public sealed class WindowsMasterVolumeState
{
    public string DeviceId { get; init; } = string.Empty;

    public string DeviceName { get; init; } = "未知输出设备";

    public float Volume { get; init; }

    public bool IsMuted { get; init; }

    public DateTimeOffset CapturedAtUtc { get; init; } = DateTimeOffset.UtcNow;
}