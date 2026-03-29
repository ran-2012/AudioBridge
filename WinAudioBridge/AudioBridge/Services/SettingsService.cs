using System.IO;
using System.Text.Json;
using WpfApp1.Models;

namespace WpfApp1.Services;

public sealed class SettingsService
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true
    };

    private readonly string _settingsFilePath = Path.Combine(AppContext.BaseDirectory, "settings.json");

    public AppSettings Current { get; private set; } = AppSettings.CreateDefault();

    public event EventHandler? SettingsChanged;

    public void Load()
    {
        if (!File.Exists(_settingsFilePath))
        {
            Current = AppSettings.CreateDefault();
            Save(Current);
            return;
        }

        try
        {
            var json = File.ReadAllText(_settingsFilePath);
            Current = JsonSerializer.Deserialize<AppSettings>(json, JsonOptions) ?? AppSettings.CreateDefault();
            Current = Normalize(Current);
        }
        catch
        {
            Current = AppSettings.CreateDefault();
            Save(Current);
        }
    }

    public void Save(AppSettings settings)
    {
        Current = Normalize(settings);
        var json = JsonSerializer.Serialize(Current, JsonOptions);
        File.WriteAllText(_settingsFilePath, json);
        SettingsChanged?.Invoke(this, EventArgs.Empty);
    }

    public AppSettings GetCopy() => new()
    {
        Encoding = Current.Encoding,
        SampleRate = Current.SampleRate,
        Channels = Current.Channels,
        BufferMilliseconds = Current.BufferMilliseconds,
        AndroidAppPackageName = Current.AndroidAppPackageName,
        PreferredDeviceSerial = Current.PreferredDeviceSerial,
        EnableAutoReconnect = Current.EnableAutoReconnect
    };

    internal static AppSettings Normalize(AppSettings settings)
    {
        var normalized = settings ?? AppSettings.CreateDefault();

        if (normalized.Encoding is not ("PCM16" or "Float32" or "Opus"))
        {
            normalized.Encoding = "PCM16";
        }

        if (normalized.SampleRate is not (44100 or 48000))
        {
            normalized.SampleRate = 48000;
        }

        if (normalized.Channels is not (1 or 2))
        {
            normalized.Channels = 2;
        }

        if (normalized.BufferMilliseconds is not (20 or 40 or 60 or 100))
        {
            normalized.BufferMilliseconds = 20;
        }

        if (string.IsNullOrWhiteSpace(normalized.AndroidAppPackageName))
        {
            normalized.AndroidAppPackageName = "dev.ran.audiobridge";
        }

        normalized.PreferredDeviceSerial ??= string.Empty;

        normalized.EnableAutoReconnect = normalized.EnableAutoReconnect;

        return normalized;
    }
}
