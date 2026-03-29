namespace WpfApp1.Models;

public sealed class AdbPortForwardResult
{
    public bool IsSuccess { get; init; }

    public string StatusMessage { get; init; } = string.Empty;

    public string DeviceSerial { get; init; } = string.Empty;

    public int LocalPort { get; init; }

    public int RemotePort { get; init; }
}
