namespace WpfApp1.Models;

public sealed class StreamingStatusSnapshot
{
    public StreamingState State { get; init; } = StreamingState.Idle;

    public string StatusMessage { get; init; } = "未启动";

    public string? TargetDeviceSerial { get; init; }

    public string? TargetDeviceName { get; init; }

    public bool IsTransportConnected { get; init; }

    public bool IsCapturing { get; init; }
}
