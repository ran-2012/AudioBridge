using WpfApp1.Models;
using Xunit;

namespace AudioBridge.Tests;

public sealed class PowerResumeHandlerTests
{
    [Theory]
    [InlineData(StreamingState.Idle, false)]
    [InlineData(StreamingState.Faulted, false)]
    [InlineData(StreamingState.Ready, false)]
    [InlineData(StreamingState.Preparing, true)]
    [InlineData(StreamingState.Streaming, true)]
    public void ShouldSkipResumeReconnect_ReturnsExpected(StreamingState state, bool expectedSkip)
    {
        var shouldSkip = WpfApp1.App.ShouldSkipResumeReconnect(state);

        Assert.Equal(expectedSkip, shouldSkip);
    }
}
