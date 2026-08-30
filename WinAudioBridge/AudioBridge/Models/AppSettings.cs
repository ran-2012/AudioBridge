namespace WpfApp1.Models;

public sealed class AppSettings
{
    public string Encoding { get; set; } = "PCM16";

    public int SampleRate { get; set; } = 48000;

    public int Channels { get; set; } = 2;

    public int BufferMilliseconds { get; set; } = 20;

    public string AndroidAppPackageName { get; set; } = "dev.ran.audiobridge";

    public string PreferredDeviceSerial { get; set; } = string.Empty;

    public bool EnableAutoReconnect { get; set; } = true;

    public bool EnableDeviceMonitor { get; set; } = true;

    /// <summary>连接模式："Adb"（USB + adb reverse）或 "Lan"（局域网直连）。</summary>
    public string ConnectionMode { get; set; } = "Adb";

    /// <summary>LAN 模式 Windows 服务器监听端口。</summary>
    public int LanListenPort { get; set; } = 6000;

    /// <summary>LAN 发现探测/应答使用的 UDP 端口。</summary>
    public int DiscoveryPort { get; set; } = 9000;

    /// <summary>是否启用局域网发现（响应 Android 探测）。</summary>
    public bool EnableLanDiscovery { get; set; } = true;

    /// <summary>是否启用延迟测量与显示。</summary>
    public bool EnableLatencyDisplay { get; set; } = true;

    public static AppSettings CreateDefault() => new();
}
