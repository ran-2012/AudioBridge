using NAudio.Wave;
using WpfApp1.Models;

namespace WpfApp1.Services;

public sealed class AudioCaptureService : IDisposable
{
    private readonly AppLogService _logService;
    private readonly object _syncRoot = new();
    private WasapiLoopbackCapture? _capture;
    private uint _capturedFrameCount;

    public AudioCaptureService(AppLogService logService)
    {
        _logService = logService;
    }

    public bool IsCapturing { get; private set; }

    public string CurrentWaveFormatDescription => _capture?.WaveFormat is null
        ? "未启动"
        : $"{_capture.WaveFormat.SampleRate} Hz / {_capture.WaveFormat.Channels}ch / {_capture.WaveFormat.BitsPerSample}bit";

    public event EventHandler<AudioFrameCapturedEventArgs>? AudioFrameCaptured;

    public WaveFormat GetDefaultLoopbackFormat()
    {
        using var probeCapture = new WasapiLoopbackCapture();
        return probeCapture.WaveFormat;
    }

    public void Start()
    {
        lock (_syncRoot)
        {
            if (IsCapturing)
            {
                return;
            }

            _capture = new WasapiLoopbackCapture();
            _capture.DataAvailable += OnDataAvailable;
            _capture.RecordingStopped += OnRecordingStopped;
            _capture.StartRecording();
            IsCapturing = true;
            _capturedFrameCount = 0;
            _logService.Info("Capture", $"已启动系统回环采集，实际格式：{CurrentWaveFormatDescription}。");
        }
    }

    public void Stop()
    {
        WasapiLoopbackCapture? captureToStop;

        lock (_syncRoot)
        {
            if (!IsCapturing)
            {
                return;
            }

            captureToStop = _capture;
            IsCapturing = false;
        }

        captureToStop?.StopRecording();
        _logService.Info("Capture", "音频采集已停止。");
    }

    public void Dispose()
    {
        Stop();
    }

    private void OnDataAvailable(object? sender, WaveInEventArgs e)
    {
        if (e.BytesRecorded <= 0)
        {
            _logService.Warning("Capture", "收到零长度音频帧，已忽略。通常表示当前没有可用的系统回环音频数据。");
            return;
        }

        var buffer = new byte[e.BytesRecorded];
        Buffer.BlockCopy(e.Buffer, 0, buffer, 0, e.BytesRecorded);
        _capturedFrameCount++;
        if (_capturedFrameCount == 1 || _capturedFrameCount % 200 == 0)
        {
            _logService.Info("Capture", $"采集到音频帧：count={_capturedFrameCount}，bytes={e.BytesRecorded}。");
        }
        AudioFrameCaptured?.Invoke(this, new AudioFrameCapturedEventArgs(buffer, e.BytesRecorded));
    }

    private void OnRecordingStopped(object? sender, StoppedEventArgs e)
    {
        lock (_syncRoot)
        {
            CleanupCapture();
            IsCapturing = false;
        }

        if (e.Exception is not null)
        {
            _logService.Error("Capture", $"音频采集停止异常：{e.Exception.Message}");
            return;
        }

        _logService.Info("Capture", "采集回调已停止。 ");
    }

    private void CleanupCapture()
    {
        if (_capture is null)
        {
            return;
        }

        _capture.DataAvailable -= OnDataAvailable;
        _capture.RecordingStopped -= OnRecordingStopped;
        _capture.Dispose();
        _capture = null;
    }
}
