using WpfApp1.Models;
using WpfApp1.Services;
using Xunit;

namespace AudioBridge.Tests;

public sealed class StreamingCoordinatorTests
{
    [Fact]
    public void SelectTargetDevice_ShouldReturnPreferredOnlineDevice_WhenNotRequiringRunningApp()
    {
        var devices = new[]
        {
            new AndroidDeviceInfo { Serial = "device-a", Model = "A", State = "Online", IsAudioAppRunning = false },
            new AndroidDeviceInfo { Serial = "device-b", Model = "B", State = "Online", IsAudioAppRunning = true }
        };

        var selected = StreamingCoordinator.SelectTargetDevice(devices, "device-a", requireAudioAppRunning: false);

        Assert.NotNull(selected);
        Assert.Equal("device-a", selected!.Serial);
    }

    [Fact]
    public void SelectTargetDevice_ShouldIgnorePreferredDevice_WhenAppMustBeRunningButPreferredIsNotReady()
    {
        var devices = new[]
        {
            new AndroidDeviceInfo { Serial = "device-a", Model = "A", State = "Online", IsAudioAppRunning = false },
            new AndroidDeviceInfo { Serial = "device-b", Model = "B", State = "Online", IsAudioAppRunning = true }
        };

        var selected = StreamingCoordinator.SelectTargetDevice(devices, "device-a", requireAudioAppRunning: true);

        Assert.NotNull(selected);
        Assert.Equal("device-b", selected!.Serial);
    }

    [Fact]
    public void SelectTargetDevice_ShouldPreferRunningAppDevice_WhenNoPreferredDeviceProvided()
    {
        var devices = new[]
        {
            new AndroidDeviceInfo { Serial = "device-a", Model = "A", State = "Online", IsAudioAppRunning = false },
            new AndroidDeviceInfo { Serial = "device-b", Model = "B", State = "Online", IsAudioAppRunning = true }
        };

        var selected = StreamingCoordinator.SelectTargetDevice(devices, null, requireAudioAppRunning: false);

        Assert.NotNull(selected);
        Assert.Equal("device-b", selected!.Serial);
    }

    [Fact]
    public void SelectTargetDevice_ShouldReturnNull_WhenNoOnlineDeviceMatches()
    {
        var devices = new[]
        {
            new AndroidDeviceInfo { Serial = "device-a", Model = "A", State = "Offline", IsAudioAppRunning = true },
            new AndroidDeviceInfo { Serial = "device-b", Model = "B", State = "Unauthorized", IsAudioAppRunning = true }
        };

        var selected = StreamingCoordinator.SelectTargetDevice(devices, "device-a", requireAudioAppRunning: false);

        Assert.Null(selected);
    }
}
