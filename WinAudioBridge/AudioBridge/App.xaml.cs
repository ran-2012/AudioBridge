using System.Windows;
using WpfApp1.Services;

namespace WpfApp1;

/// <summary>
/// Interaction logic for App.xaml
/// </summary>
public partial class App : System.Windows.Application
{
	private SettingsService? _settingsService;
	private AppLogService? _logService;
	private TrayService? _trayService;
	private AdbService? _adbService;
	private AudioCaptureService? _audioCaptureService;
	private AudioTransportService? _audioTransportService;
	private VolumeIconService? _volumeIconService;
	private WindowsVolumeService? _windowsVolumeService;
	private StreamingCoordinator? _streamingCoordinator;
	private MainWindow? _mainWindow;
	private SettingsWindow? _settingsWindow;
	private bool _isExiting;

	protected override void OnStartup(StartupEventArgs e)
	{
		base.OnStartup(e);

		ShutdownMode = ShutdownMode.OnExplicitShutdown;

		_settingsService = new SettingsService();
		_logService = new AppLogService();
		_settingsService.Load();
		_logService.Info("App", "应用启动，正在初始化服务。");
		_adbService = new AdbService(_logService);
		_audioCaptureService = new AudioCaptureService(_logService);
		_audioTransportService = new AudioTransportService(_logService);
		_volumeIconService = new VolumeIconService(_logService);
		_windowsVolumeService = new WindowsVolumeService(_logService, _volumeIconService);
		_windowsVolumeService.StartMonitoring();
		_streamingCoordinator = new StreamingCoordinator(_settingsService, _adbService, _audioCaptureService, _audioTransportService, _windowsVolumeService, _volumeIconService, _logService);
		_settingsService.SettingsChanged += SettingsService_SettingsChanged;

		_mainWindow = new MainWindow(_settingsService, _streamingCoordinator, _windowsVolumeService, _logService)
		{
			ShowInTaskbar = false,
			WindowState = WindowState.Minimized
		};
		_mainWindow.Closing += MainWindow_Closing;

		_trayService = new TrayService(
			showMainWindow: ShowMainWindow,
			showSettings: ShowSettings,
			exitApplication: ExitApplication);

		_trayService.Initialize();

		_ = Dispatcher.BeginInvoke(async () =>
		{
			if (_streamingCoordinator is null)
			{
				return;
			}

			await _streamingCoordinator.AutoConnectIfPossibleAsync("启动后自动连接", restartIfRunning: false);
		});
	}

	protected override void OnExit(ExitEventArgs e)
	{
		if (_settingsService is not null)
		{
			_settingsService.SettingsChanged -= SettingsService_SettingsChanged;
		}

		_trayService?.Dispose();
		_windowsVolumeService?.Dispose();
		_streamingCoordinator?.Dispose();
		base.OnExit(e);
	}

	private async void SettingsService_SettingsChanged(object? sender, EventArgs e)
	{
		if (_streamingCoordinator is null || _isExiting)
		{
			return;
		}

		await _streamingCoordinator.AutoConnectIfPossibleAsync("配置修改后自动连接", restartIfRunning: true);
	}

	private void MainWindow_Closing(object? sender, System.ComponentModel.CancelEventArgs e)
	{
		if (_isExiting || _mainWindow is null)
		{
			return;
		}

		e.Cancel = true;
		_mainWindow.Hide();
		_mainWindow.ShowInTaskbar = false;
	}

	private void ShowMainWindow()
	{
		if (_mainWindow is null)
		{
			return;
		}

		if (!_mainWindow.IsVisible)
		{
			_mainWindow.Show();
		}

		_mainWindow.ShowInTaskbar = true;
		_mainWindow.WindowState = WindowState.Normal;
		_mainWindow.Activate();
		_mainWindow.Topmost = true;
		_mainWindow.Topmost = false;
		_mainWindow.Focus();
	}

	private void ShowSettings()
	{
		if (_settingsService is null)
		{
			return;
		}

		if (_adbService is null || _logService is null)
		{
			return;
		}

		if (_settingsWindow is not null)
		{
			_settingsWindow.Activate();
			return;
		}

		_settingsWindow = new SettingsWindow(_settingsService, _adbService, _logService)
		{
		};

		_settingsWindow.Closed += (_, _) => _settingsWindow = null;

		_settingsWindow.Show();
		_settingsWindow.Activate();
	}

	private void ExitApplication()
	{
		_isExiting = true;
		_trayService?.Dispose();
		_settingsWindow?.Close();
		_mainWindow?.Close();
		Shutdown();
	}
}