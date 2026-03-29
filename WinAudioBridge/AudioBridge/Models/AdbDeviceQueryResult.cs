namespace WpfApp1.Models;

public sealed class AdbDeviceQueryResult
{
    public bool IsSuccess { get; init; }

    public string StatusMessage { get; init; } = string.Empty;

    public IReadOnlyList<AndroidDeviceInfo> Devices { get; init; } = Array.Empty<AndroidDeviceInfo>();
}
