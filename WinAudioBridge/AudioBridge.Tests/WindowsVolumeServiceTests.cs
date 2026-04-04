using WpfApp1.Services;
using Xunit;

namespace AudioBridge.Tests;

public sealed class WindowsVolumeServiceTests
{
    [Fact]
    public void BuildSessionCommandId_ShouldFallbackToLegacyProcessId_WhenRawIdsMissing()
    {
        var sessionId = WindowsVolumeService.BuildSessionCommandId(null, null, 1234, "chrome");

        Assert.Equal("pid:1234:chrome", sessionId);
    }

    [Fact]
    public void SessionIdsMatch_ShouldMatchNewCompositeIdByInstanceIdentifier()
    {
        const string sessionIdentifier = "group-1";
        const string sessionInstanceIdentifier = "instance-1";
        var targetSessionId = WindowsVolumeService.BuildSessionCommandId(sessionIdentifier, sessionInstanceIdentifier, 4321, "spotify");

        var matched = WindowsVolumeService.SessionIdsMatch(targetSessionId, sessionIdentifier, sessionInstanceIdentifier, 4321, "spotify");

        Assert.True(matched);
    }

    [Fact]
    public void SessionIdsMatch_ShouldMatchLegacyInstanceIdentifier()
    {
        var matched = WindowsVolumeService.SessionIdsMatch("instance-legacy", "group-1", "instance-legacy", 4321, "spotify");

        Assert.True(matched);
    }

    [Fact]
    public void SessionIdsMatch_ShouldMatchCompositeIdAgainstStableSessionIdentifier()
    {
        var oldCompositeId = WindowsVolumeService.BuildSessionCommandId("group-stable", "instance-old", 2222, "chrome");

        var matched = WindowsVolumeService.SessionIdsMatch(oldCompositeId, "group-stable", "instance-new", 2222, "chrome");

        Assert.True(matched);
    }

    [Fact]
    public void SessionIdsMatch_ShouldReturnFalse_WhenIdentifiersDoNotMatch()
    {
        var matched = WindowsVolumeService.SessionIdsMatch("wab-session|sid=group-a|iid=instance-a|pid=1|pn=chrome", "group-b", "instance-b", 2, "edge");

        Assert.False(matched);
    }
}