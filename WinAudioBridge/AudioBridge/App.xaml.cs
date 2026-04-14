using System.Threading;
using System.Windows;
using WpfApp1.Services;

namespace WpfApp1;

/// <summary>
/// Interaction logic for App.xaml
/// </summary>
public partial class App : System.Windows.Application
{
	private const string SingleInstanceMutexName = "Local\\WinAudioBridge.SingleInstance";
	private Mutex? _singleInstanceMutex;
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
	private bool _cleanupCompleted;

	protected override void OnStartup(StartupEventArgs e)
	{
		_singleInstanceMutex = new Mutex(initiallyOwned: true, SingleInstanceMutexName, out var createdNew);
		if (!createdNew)
		{
			_singleInstanceMutex.Dispose();
			_singleInstanceMutex = null;
			System.Windows.MessageBox.Show(
				"WinAudioBridge 已在运行。",
				"WinAudioBridge",
				MessageBoxButton.OK,
				MessageBoxImage.Information);
			Shutdown();
			return;
		}

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
		ReleaseSingleInstanceMutex();
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

		if (_adbService is null || _logService is null || _audioCaptureService is null)
		{
			return;
		}

		if (_settingsWindow is not null)
		{
			_settingsWindow.Activate();
			return;
		}

		_settingsWindow = new SettingsWindow(_settingsService, _adbService, _logService, _audioCaptureService)
		{
		};

		_settingsWindow.Closed += (_, _) => _settingsWindow = null;

		_settingsWindow.Show();
		_settingsWindow.Activate();
	}

	private async void ExitApplication()
	{
		if (!Dispatcher.CheckAccess())
		{
			_ = Dispatcher.InvokeAsync(ExitApplication);
			return;
		}

		if (_isExiting)
		{
			return;
		}

		_isExiting = true;

		try
		{
			await CleanupBeforeShutdownAsync();
		}
		finally
		{
			Shutdown();
		}
	}

	private async Task CleanupBeforeShutdownAsync()
	{
		if (_cleanupCompleted)
		{
			return;
		}

		if (_settingsService is not null)
		{
			_settingsService.SettingsChanged -= SettingsService_SettingsChanged;
		}

		_logService?.Info("App", "应用正在退出，开始释放资源。 ");

		_trayService?.Dispose();
		_trayService = null;

		_settingsWindow?.Close();
		_settingsWindow = null;

		_mainWindow?.Close();
		_mainWindow = null;

		if (_streamingCoordinator is not null)
		{
			await _streamingCoordinator.DisposeAsync();
			_streamingCoordinator = null;
		}

		if (_windowsVolumeService is not null)
		{
			await _windowsVolumeService.DisposeAsync();
			_windowsVolumeService = null;
		}

		_cleanupCompleted = true;
		_logService?.Info("App", "应用退出清理完成。 ");
	}

	private void ReleaseSingleInstanceMutex()
	{
		if (_singleInstanceMutex is null)
		{
			return;
		}

		try
		{
			_singleInstanceMutex.ReleaseMutex();
		}
		catch (ApplicationException)
		{
			// ignore
		}

		_singleInstanceMutex.Dispose();
		_singleInstanceMutex = null;
	}
}