namespace WpfApp1.Models;

public sealed class StreamingStatusSnapshot
{
    public StreamingState State { get; init; } = StreamingState.Idle;

    public string StatusMessage { get; init; } = "未启动";

    public string? TargetDeviceSerial { get; init; }

    public string? TargetDeviceName { get; init; }

    public bool IsTransportConnected { get; init; }

    public bool IsCapturing { get; init; }

    /// <summary>连接模式："Adb" 或 "Lan"。</summary>
    public string ConnectionMode { get; init; } = "Adb";

    /// <summary>估算的单向传输延迟（毫秒），无样本时为 null。</summary>
    public long? EstimatedLatencyMillis { get; init; }
}
