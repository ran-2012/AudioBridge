using System.Drawing;
using System.IO;
using System.Windows.Forms;

namespace WpfApp1.Services;

public sealed class TrayService : IDisposable
{
	private readonly Action _showMainWindow;
    private readonly Action _showSettings;
    private readonly Action _exitApplication;
    private NotifyIcon? _notifyIcon;

    public TrayService(Action showMainWindow, Action showSettings, Action exitApplication)
    {
		_showMainWindow = showMainWindow;
        _showSettings = showSettings;
        _exitApplication = exitApplication;
    }

    public void Initialize()
    {
        if (_notifyIcon is not null)
        {
            return;
        }

        var menu = new ContextMenuStrip();
        menu.Items.Add("主界面", null, (_, _) => _showMainWindow());
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add("设置", null, (_, _) => _showSettings());
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add("退出", null, (_, _) => _exitApplication());

        _notifyIcon = new NotifyIcon
        {
            Icon = LoadTrayIcon(),
            Text = "WinAudioBridge",
            Visible = true,
            ContextMenuStrip = menu
        };

        _notifyIcon.DoubleClick += (_, _) => _showMainWindow();
    }

    public void Dispose()
    {
        if (_notifyIcon is null)
        {
            return;
        }

        _notifyIcon.Visible = false;
        _notifyIcon.Dispose();
        _notifyIcon = null;
    }

    private static Icon LoadTrayIcon()
    {
        var iconPath = Path.Combine(AppContext.BaseDirectory, "Assets", "app.ico");
        return File.Exists(iconPath) ? new Icon(iconPath) : SystemIcons.Application;
    }
}
