using WpfApp1.Models;
using WpfApp1.Services;
using Xunit;

namespace AudioBridge.Tests;

public sealed class SettingsServiceTests
{
    [Fact]
    public void Normalize_ShouldFallbackToDefaults_WhenValuesAreInvalid()
    {
        var settings = new AppSettings
        {
            Encoding = "AAC",
            SampleRate = 32000,
            Channels = 8,
            BufferMilliseconds = 15,
            AndroidAppPackageName = " ",
            PreferredDeviceSerial = null!,
            EnableAutoReconnect = false
        };

        var normalized = SettingsService.Normalize(settings);

        Assert.Equal("PCM16", normalized.Encoding);
        Assert.Equal(48000, normalized.SampleRate);
        Assert.Equal(2, normalized.Channels);
        Assert.Equal(20, normalized.BufferMilliseconds);
        Assert.Equal("dev.ran.audiobridge", normalized.AndroidAppPackageName);
        Assert.Equal(string.Empty, normalized.PreferredDeviceSerial);
        Assert.False(normalized.EnableAutoReconnect);
    }

    [Fact]
    public void Normalize_ShouldKeepValidValues()
    {
        var settings = new AppSettings
        {
            Encoding = "Float32",
            SampleRate = 44100,
            Channels = 1,
            BufferMilliseconds = 100,
            AndroidAppPackageName = "dev.custom.app",
            PreferredDeviceSerial = "device-1",
            EnableAutoReconnect = true
        };

        var normalized = SettingsService.Normalize(settings);

        Assert.Same(settings, normalized);
        Assert.Equal("Float32", normalized.Encoding);
        Assert.Equal(44100, normalized.SampleRate);
        Assert.Equal(1, normalized.Channels);
        Assert.Equal(100, normalized.BufferMilliseconds);
        Assert.Equal("dev.custom.app", normalized.AndroidAppPackageName);
        Assert.Equal("device-1", normalized.PreferredDeviceSerial);
        Assert.True(normalized.EnableAutoReconnect);
    }
}
