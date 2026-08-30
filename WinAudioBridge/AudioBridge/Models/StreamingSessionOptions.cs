namespace WpfApp1.Models;

public sealed class StreamingSessionOptions
{
    public string Encoding { get; init; } = "PCM16";

    public int SampleRate { get; init; }

    public int Channels { get; init; }

    public int BufferMilliseconds { get; init; }

    public string AndroidAppPackageName { get; init; } = string.Empty;

    public string PreferredDeviceSerial { get; init; } = string.Empty;

    /// <summary>连接模式："Adb"（USB + adb reverse）或 "Lan"（局域网直连）。</summary>
    public string ConnectionMode { get; init; } = "Adb";

    public int LocalPort { get; init; } = 5000;

    public int RemotePort { get; init; } = 5000;

    public int BitsPerSample => Encoding switch
    {
        "Float32" => 32,
        _ => 16
    };
}
