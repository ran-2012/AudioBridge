using System.Windows.Forms;
using WpfApp1.Services;
using Xunit;

namespace AudioBridge.Tests;

public sealed class TrayServiceTests
{
    [Fact]
    public async Task PopulateContextMenu_ShouldIncludeRestartAudioAndKeepExistingEntries()
    {
        using var menu = new ContextMenuStrip();
        var restartCalled = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);

        TrayService.PopulateContextMenu(
            menu,
            showMainWindow: () => { },
            restartAudio: () =>
            {
                restartCalled.SetResult();
                return Task.CompletedTask;
            },
            showSettings: () => { },
            exitApplication: () => { });

        var texts = menu.Items.OfType<ToolStripItem>().Where(item => item is not ToolStripSeparator).Select(item => item.Text).ToArray();

        Assert.Equal(new[] { "主界面", "重启音频", "设置", "退出" }, texts);

        menu.Items.OfType<ToolStripItem>().First(item => item.Text == "重启音频").PerformClick();
        await restartCalled.Task.WaitAsync(TimeSpan.FromSeconds(1));
    }
}