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

    public static AppSettings CreateDefault() => new();
}
