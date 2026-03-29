using System.Windows;
using WpfApp1.Services;
using WpfApp1.ViewModels;

namespace WpfApp1;

/// <summary>
/// Interaction logic for MainWindow.xaml
/// </summary>
public partial class MainWindow : Window
{
    private readonly StatusViewModel _viewModel;

    public MainWindow(SettingsService settingsService, StreamingCoordinator streamingCoordinator, WindowsVolumeService windowsVolumeService, AppLogService logService)
    {
        InitializeComponent();
        _viewModel = new StatusViewModel(settingsService, streamingCoordinator, windowsVolumeService, logService);
        DataContext = _viewModel;
    }

    private async void PrepareLink_Click(object sender, RoutedEventArgs e)
    {
        await _viewModel.PrepareAsync();
    }

    private async void StartStreaming_Click(object sender, RoutedEventArgs e)
    {
        await _viewModel.StartAsync();
    }

    private async void StopStreaming_Click(object sender, RoutedEventArgs e)
    {
        await _viewModel.StopAsync();
    }
}