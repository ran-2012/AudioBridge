using System.Windows;
using WpfApp1.Models;
using WpfApp1.Services;

namespace WpfApp1;

public partial class SettingsWindow : Window
{
    private readonly AdbService _adbService;
    private readonly AppLogService _logService;
    private readonly SettingsService _settingsService;
    private string _preferredDeviceSerial = string.Empty;

    public SettingsWindow(SettingsService settingsService, AdbService adbService, AppLogService logService)
    {
        _settingsService = settingsService;
        _adbService = adbService;
        _logService = logService;
        InitializeComponent();
        InitializeOptions();
        LoadCurrentValues();
    }

    private void InitializeOptions()
    {
        EncodingComboBox.ItemsSource = new[] { "PCM16", "Float32", "Opus" };
        SampleRateComboBox.ItemsSource = new[] { 44100, 48000 };
        ChannelsComboBox.ItemsSource = new[] { 1, 2 };
        BufferComboBox.ItemsSource = new[] { 20, 40, 60, 100 };
    }

    private void LoadCurrentValues()
    {
        var settings = _settingsService.GetCopy();
        EncodingComboBox.SelectedItem = settings.Encoding;
        SampleRateComboBox.SelectedItem = settings.SampleRate;
        ChannelsComboBox.SelectedItem = settings.Channels;
        BufferComboBox.SelectedItem = settings.BufferMilliseconds;
        AndroidPackageNameTextBox.Text = settings.AndroidAppPackageName;
        _preferredDeviceSerial = settings.PreferredDeviceSerial;
        EnableAutoReconnectCheckBox.IsChecked = settings.EnableAutoReconnect;
        UpdateSelectedDeviceText();
    }

    private async void SettingsWindow_Loaded(object sender, RoutedEventArgs e)
    {
        await RefreshDevicesAsync();
    }

    private void Save_Click(object sender, RoutedEventArgs e)
    {
        if (EncodingComboBox.SelectedItem is not string encoding ||
            SampleRateComboBox.SelectedItem is not int sampleRate ||
            ChannelsComboBox.SelectedItem is not int channels ||
            BufferComboBox.SelectedItem is not int bufferMilliseconds)
        {
            System.Windows.MessageBox.Show(this, "请先选择完整的设置项。", "WinAudioBridge", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }

        _settingsService.Save(new AppSettings
        {
            Encoding = encoding,
            SampleRate = sampleRate,
            Channels = channels,
            BufferMilliseconds = bufferMilliseconds,
            AndroidAppPackageName = AndroidPackageNameTextBox.Text.Trim(),
            PreferredDeviceSerial = _preferredDeviceSerial,
            EnableAutoReconnect = EnableAutoReconnectCheckBox.IsChecked == true
        });

        _logService.Info("Settings", $"设置已保存：编码={encoding}，采样率={sampleRate}，声道={channels}，Buffer={bufferMilliseconds}ms，优先设备={(_preferredDeviceSerial.Length == 0 ? "自动" : _preferredDeviceSerial)}，自动重连={(EnableAutoReconnectCheckBox.IsChecked == true ? "开启" : "关闭")}。");

        Close();
    }

    private void Cancel_Click(object sender, RoutedEventArgs e)
    {
        Close();
    }

    private void ResetDefaults_Click(object sender, RoutedEventArgs e)
    {
        var defaults = AppSettings.CreateDefault();
        EncodingComboBox.SelectedItem = defaults.Encoding;
        SampleRateComboBox.SelectedItem = defaults.SampleRate;
        ChannelsComboBox.SelectedItem = defaults.Channels;
        BufferComboBox.SelectedItem = defaults.BufferMilliseconds;
        AndroidPackageNameTextBox.Text = defaults.AndroidAppPackageName;
        _preferredDeviceSerial = defaults.PreferredDeviceSerial;
        EnableAutoReconnectCheckBox.IsChecked = defaults.EnableAutoReconnect;
        DevicesListView.SelectedItem = null;
        UpdateSelectedDeviceText();
    }

    private async void RefreshDevices_Click(object sender, RoutedEventArgs e)
    {
        await RefreshDevicesAsync();
    }

    private async Task RefreshDevicesAsync()
    {
        _logService.Info("Settings", "开始刷新 Android 设备列表。");
        SetRefreshState(isLoading: true, "正在查询 Android 设备和应用运行状态...");

        try
        {
            var result = await _adbService.QueryConnectedDevicesAsync(AndroidPackageNameTextBox.Text.Trim());
            DevicesListView.ItemsSource = result.Devices;
            DeviceStatusTextBlock.Text = result.StatusMessage;
            RestoreSelectedDevice(result.Devices);
            _logService.Info("Settings", $"设备列表刷新完成：{result.StatusMessage}");
        }
        catch (Exception ex)
        {
            DevicesListView.ItemsSource = Array.Empty<AndroidDeviceInfo>();
            DeviceStatusTextBlock.Text = $"查询失败：{ex.Message}";
            _logService.Error("Settings", $"刷新设备失败：{ex.Message}");
        }
        finally
        {
            SetRefreshState(isLoading: false, DeviceStatusTextBlock.Text);
        }
    }

    private void SetRefreshState(bool isLoading, string statusText)
    {
        RefreshDevicesButton.IsEnabled = !isLoading;
        DeviceStatusTextBlock.Text = statusText;
    }

    private void DevicesListView_SelectionChanged(object sender, System.Windows.Controls.SelectionChangedEventArgs e)
    {
        if (DevicesListView.SelectedItem is AndroidDeviceInfo device)
        {
            _preferredDeviceSerial = device.Serial;
            UpdateSelectedDeviceText(device);
            _logService.Info("Settings", $"已选择优先设备：{device.Model} ({device.Serial})。");
        }
    }

    private void RestoreSelectedDevice(IReadOnlyList<AndroidDeviceInfo> devices)
    {
        var selected = devices.FirstOrDefault(x => string.Equals(x.Serial, _preferredDeviceSerial, StringComparison.OrdinalIgnoreCase));
        DevicesListView.SelectedItem = selected;
        UpdateSelectedDeviceText(selected);
    }

    private void UpdateSelectedDeviceText(AndroidDeviceInfo? device = null)
    {
        if (device is not null)
        {
            SelectedDeviceTextBlock.Text = $"当前优先设备：{device.Model} ({device.Serial})";
            return;
        }

        SelectedDeviceTextBlock.Text = string.IsNullOrWhiteSpace(_preferredDeviceSerial)
            ? "当前未指定优先设备，将自动选择可用设备。"
            : $"当前优先设备序列号：{_preferredDeviceSerial}";
    }
}
