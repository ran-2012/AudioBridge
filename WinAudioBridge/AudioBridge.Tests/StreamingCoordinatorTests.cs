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
    public void IsInboundLinkDead_ShouldReturnFalse_WhenTimeoutNotReached()
    {
        var now = new DateTime(2026, 4, 12, 10, 0, 14, DateTimeKind.Utc);
        var lastInbound = new DateTime(2026, 4, 12, 10, 0, 0, DateTimeKind.Utc);

        var dead = StreamingCoordinator.IsInboundLinkDead(now, lastInbound, TimeSpan.FromSeconds(15));

        Assert.False(dead);
    }

    [Fact]
    public void IsInboundLinkDead_ShouldReturnTrue_WhenTimeoutReached()
    {
        var now = new DateTime(2026, 4, 12, 10, 0, 15, DateTimeKind.Utc);
        var lastInbound = new DateTime(2026, 4, 12, 10, 0, 0, DateTimeKind.Utc);

        var dead = StreamingCoordinator.IsInboundLinkDead(now, lastInbound, TimeSpan.FromSeconds(15));

        Assert.True(dead);
    }

    [Fact]
    public void IsInboundLinkDead_ShouldReturnFalse_WhenNoInboundActivityRecorded()
    {
        var dead = StreamingCoordinator.IsInboundLinkDead(
            new DateTime(2026, 4, 12, 10, 0, 30, DateTimeKind.Utc),
            default,
            TimeSpan.FromSeconds(15));

        Assert.False(dead);
    }

    [Fact]
    public void IsAndroidPlaybackStatusExpired_ShouldReturnFalse_WhenNoStatusRecorded()
    {
        var expired = StreamingCoordinator.IsAndroidPlaybackStatusExpired(
            new DateTime(2026, 7, 8, 10, 0, 30, DateTimeKind.Utc),
            default,
            TimeSpan.FromSeconds(10));

        Assert.False(expired);
    }

    [Fact]
    public void IsAndroidPlaybackStatusExpired_ShouldReturnFalse_WhenTimeoutNotReached()
    {
        var now = new DateTime(2026, 7, 8, 10, 0, 9, DateTimeKind.Utc);
        var lastStatus = new DateTime(2026, 7, 8, 10, 0, 0, DateTimeKind.Utc);

        var expired = StreamingCoordinator.IsAndroidPlaybackStatusExpired(now, lastStatus, TimeSpan.FromSeconds(10));

        Assert.False(expired);
    }

    [Fact]
    public void IsAndroidPlaybackStatusExpired_ShouldReturnTrue_WhenTimeoutReachedEvenIfOutboundActivityWouldExist()
    {
        var now = new DateTime(2026, 7, 8, 10, 0, 10, DateTimeKind.Utc);
        var lastStatus = new DateTime(2026, 7, 8, 10, 0, 0, DateTimeKind.Utc);

        var expired = StreamingCoordinator.IsAndroidPlaybackStatusExpired(now, lastStatus, TimeSpan.FromSeconds(10));

        Assert.True(expired);
    }

    [Fact]
    public void ShouldMonitorAndroidPlaybackStatus_ShouldRequireActiveTransport()
    {
        Assert.False(StreamingCoordinator.ShouldMonitorAndroidPlaybackStatus(StreamingState.Streaming, isTransportConnected: false, targetDeviceSerial: "device-a"));
        Assert.True(StreamingCoordinator.ShouldMonitorAndroidPlaybackStatus(StreamingState.Streaming, isTransportConnected: true, targetDeviceSerial: "device-a"));
    }

    [Theory]
    [InlineData(false, null, true)]
    [InlineData(true, 2500L, true)]
    [InlineData(true, 1000L, false)]
    [InlineData(true, null, false)]
    public void IsAndroidPlaybackStatusAbnormal_ShouldDetectPlaybackAndLatencyIssues(bool isPlaying, long? latencyMillis, bool expected)
    {
        var abnormal = StreamingCoordinator.IsAndroidPlaybackStatusAbnormal(
            isPlaying,
            latencyMillis,
            TimeSpan.FromSeconds(2));

        Assert.Equal(expected, abnormal);
    }

    [Theory]
    [InlineData(0, 3, false)]
    [InlineData(2, 3, false)]
    [InlineData(3, 3, true)]
    [InlineData(4, 3, true)]
    public void ShouldRestartForAbnormalPlayback_ShouldRequireConsecutiveThreshold(int count, int threshold, bool expected)
    {
        Assert.Equal(expected, StreamingCoordinator.ShouldRestartForAbnormalPlayback(count, threshold));
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

    [Fact]
    public void IsLanMode_ShouldReturnTrue_ForLanValues()
    {
        Assert.True(StreamingCoordinator.IsLanMode("Lan"));
        Assert.True(StreamingCoordinator.IsLanMode("lan"));
    }

    [Fact]
    public void IsLanMode_ShouldReturnFalse_ForAdbOrNullOrEmpty()
    {
        Assert.False(StreamingCoordinator.IsLanMode("Adb"));
        Assert.False(StreamingCoordinator.IsLanMode("adb"));
        Assert.False(StreamingCoordinator.IsLanMode(null));
        Assert.False(StreamingCoordinator.IsLanMode(string.Empty));
    }
}
