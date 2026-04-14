using WpfApp1.Models;
using WpfApp1.Services;
using Xunit;

namespace AudioBridge.Tests;

public sealed class StreamingCoordinatorTests
{
    [Fact]
    public void ShouldSendHeartbeat_ShouldReturnFalse_WhenIdleThresholdNotReached()
    {
        var now = new DateTime(2026, 4, 12, 10, 0, 4, DateTimeKind.Utc);
        var lastActivity = new DateTime(2026, 4, 12, 10, 0, 0, DateTimeKind.Utc);

        var shouldSend = StreamingCoordinator.ShouldSendHeartbeat(now, lastActivity, TimeSpan.FromSeconds(5));

        Assert.False(shouldSend);
    }

    [Fact]
    public void ShouldSendHeartbeat_ShouldReturnTrue_WhenIdleThresholdReached()
    {
        var now = new DateTime(2026, 4, 12, 10, 0, 5, DateTimeKind.Utc);
        var lastActivity = new DateTime(2026, 4, 12, 10, 0, 0, DateTimeKind.Utc);

        var shouldSend = StreamingCoordinator.ShouldSendHeartbeat(now, lastActivity, TimeSpan.FromSeconds(5));

        Assert.True(shouldSend);
    }

    [Fact]
    public void ShouldSendHeartbeat_ShouldReturnFalse_WhenNoPreviousActivityExists()
    {
        var shouldSend = StreamingCoordinator.ShouldSendHeartbeat(
            new DateTime(2026, 4, 12, 10, 0, 5, DateTimeKind.Utc),
            default,
            TimeSpan.FromSeconds(5));

        Assert.False(shouldSend);
    }

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
