using WpfApp1.Models;
using WpfApp1.Services;
using Xunit;

namespace AudioBridge.Tests;

public sealed class DeviceMonitorServiceTests
{
    [Fact]
    public void ShouldTriggerReconnect_ReturnsTrue_WhenDeviceNewlyAppearsOnlineWithAppRunning()
    {
        var previous = new List<AndroidDeviceInfo>
        {
            new() { Serial = "device-a", Model = "A", State = "Online", IsAudioAppRunning = true }
        };

        var current = new List<AndroidDeviceInfo>
        {
            new() { Serial = "device-a", Model = "A", State = "Online", IsAudioAppRunning = true },
            new() { Serial = "device-b", Model = "B", State = "Online", IsAudioAppRunning = true }
        };

        var shouldTrigger = DeviceMonitorService.ShouldTriggerReconnect(previous, current, out var triggeredDevice);

        Assert.True(shouldTrigger);
        Assert.NotNull(triggeredDevice);
        Assert.Equal("device-b", triggeredDevice!.Serial);
    }

    [Fact]
    public void ShouldTriggerReconnect_ReturnsTrue_WhenDeviceTransitionsFromOfflineToOnlineWithAppRunning()
    {
        var previous = new List<AndroidDeviceInfo>
        {
            new() { Serial = "device-a", Model = "A", State = "Offline", IsAudioAppRunning = false }
        };

        var current = new List<AndroidDeviceInfo>
        {
            new() { Serial = "device-a", Model = "A", State = "Online", IsAudioAppRunning = true }
        };

        var shouldTrigger = DeviceMonitorService.ShouldTriggerReconnect(previous, current, out var triggeredDevice);

        Assert.True(shouldTrigger);
        Assert.NotNull(triggeredDevice);
        Assert.Equal("device-a", triggeredDevice!.Serial);
    }

    [Fact]
    public void ShouldTriggerReconnect_ReturnsTrue_WhenAppStartsRunningOnPreviouslyOnlineDevice()
    {
        var previous = new List<AndroidDeviceInfo>
        {
            new() { Serial = "device-a", Model = "A", State = "Online", IsAudioAppRunning = false }
        };

        var current = new List<AndroidDeviceInfo>
        {
            new() { Serial = "device-a", Model = "A", State = "Online", IsAudioAppRunning = true }
        };

        var shouldTrigger = DeviceMonitorService.ShouldTriggerReconnect(previous, current, out var triggeredDevice);

        Assert.True(shouldTrigger);
        Assert.NotNull(triggeredDevice);
        Assert.Equal("device-a", triggeredDevice!.Serial);
    }

    [Fact]
    public void ShouldTriggerReconnect_ReturnsFalse_WhenNoChangeInDeviceStatus()
    {
        var previous = new List<AndroidDeviceInfo>
        {
            new() { Serial = "device-a", Model = "A", State = "Online", IsAudioAppRunning = true }
        };

        var current = new List<AndroidDeviceInfo>
        {
            new() { Serial = "device-a", Model = "A", State = "Online", IsAudioAppRunning = true }
        };

        var shouldTrigger = DeviceMonitorService.ShouldTriggerReconnect(previous, current, out var triggeredDevice);

        Assert.False(shouldTrigger);
        Assert.Null(triggeredDevice);
    }

    [Fact]
    public void ShouldTriggerReconnect_ReturnsFalse_WhenDeviceGoesOffline()
    {
        var previous = new List<AndroidDeviceInfo>
        {
            new() { Serial = "device-a", Model = "A", State = "Online", IsAudioAppRunning = true }
        };

        var current = new List<AndroidDeviceInfo>
        {
            new() { Serial = "device-a", Model = "A", State = "Offline", IsAudioAppRunning = false }
        };

        var shouldTrigger = DeviceMonitorService.ShouldTriggerReconnect(previous, current, out var triggeredDevice);

        Assert.False(shouldTrigger);
        Assert.Null(triggeredDevice);
    }

    [Fact]
    public void ShouldTriggerReconnect_ReturnsFalse_WhenDeviceAppearsButAppNotRunning()
    {
        var previous = new List<AndroidDeviceInfo>();

        var current = new List<AndroidDeviceInfo>
        {
            new() { Serial = "device-a", Model = "A", State = "Online", IsAudioAppRunning = false }
        };

        var shouldTrigger = DeviceMonitorService.ShouldTriggerReconnect(previous, current, out var triggeredDevice);

        Assert.False(shouldTrigger);
        Assert.Null(triggeredDevice);
    }

    [Fact]
    public void ShouldTriggerReconnect_ReturnsFalse_WhenNoDevices()
    {
        var previous = new List<AndroidDeviceInfo>();
        var current = new List<AndroidDeviceInfo>();

        var shouldTrigger = DeviceMonitorService.ShouldTriggerReconnect(previous, current, out var triggeredDevice);

        Assert.False(shouldTrigger);
        Assert.Null(triggeredDevice);
    }

    [Theory]
    [InlineData(StreamingState.Idle, false)]
    [InlineData(StreamingState.Faulted, false)]
    [InlineData(StreamingState.Preparing, true)]
    [InlineData(StreamingState.Ready, true)]
    [InlineData(StreamingState.Streaming, true)]
    public void ShouldSkipTriggerDueToState_ReturnsExpected(StreamingState state, bool expectedSkip)
    {
        var shouldSkip = DeviceMonitorService.ShouldSkipTriggerDueToState(state);

        Assert.Equal(expectedSkip, shouldSkip);
    }
}
