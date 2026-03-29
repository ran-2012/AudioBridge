using WpfApp1.Services;
using Xunit;

namespace AudioBridge.Tests;

public sealed class VolumeIconServiceTests
{
    [Fact]
    public void CreateIconKey_ShouldBeStable_ForSameInputs()
    {
        var service = new VolumeIconService(new AppLogService());

        var first = service.CreateIconKey("C:\\icons\\app.ico", "C:\\apps\\app.exe", 1234, "session-1");
        var second = service.CreateIconKey("C:\\icons\\app.ico", "C:\\apps\\app.exe", 1234, "session-1");

        Assert.Equal(first, second);
        Assert.Equal(64, first.Length);
    }

    [Fact]
    public void GetIconHash_ShouldReturnEmpty_WhenNoFileExists()
    {
        var service = new VolumeIconService(new AppLogService());

        var hash = service.GetIconHash("Z:\\not-exists\\app.ico", null);

        Assert.Equal(string.Empty, hash);
    }

    [Fact]
    public void GetIconHash_ShouldUseNormalizedIconPath()
    {
        var service = new VolumeIconService(new AppLogService());
        var tempFile = Path.Combine(Path.GetTempPath(), $"icon-hash-{Guid.NewGuid():N}.bin");

        try
        {
            File.WriteAllText(tempFile, "icon");

            var hash = service.GetIconHash($"\"{tempFile}\",0", null);

            Assert.False(string.IsNullOrWhiteSpace(hash));
            Assert.Equal(64, hash.Length);
        }
        finally
        {
            if (File.Exists(tempFile))
            {
                File.Delete(tempFile);
            }
        }
    }

    [Fact]
    public void TryGetPngBytes_ShouldReturnNull_WhenPathDoesNotExist()
    {
        var service = new VolumeIconService(new AppLogService());

        var bytes = service.TryGetPngBytes("Z:\\not-exists\\app.ico", null);

        Assert.Null(bytes);
    }
}
