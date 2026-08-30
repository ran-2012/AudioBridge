using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using WpfApp1.Models;

namespace WpfApp1.Services;

/// <summary>
/// 局域网发现服务：监听 UDP 发现端口，响应 Android 端发送的探测广播并单播回复服务公告。
/// 仅在连接模式为 Lan 且启用局域网发现时运行。
/// </summary>
public sealed class LanDiscoveryService : IDisposable
{
    private const string ProbeType = "winAudioBridgeProbe";
    private const string AnnounceType = "winAudioBridgeAnnounce";
    private readonly SettingsService _settingsService;
    private readonly AppLogService _logService;
    private readonly JsonSerializerOptions _jsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };
    private UdpClient? _udpClient;
    private CancellationTokenSource? _cts;
    private Task? _listenTask;

    public LanDiscoveryService(SettingsService settingsService, AppLogService logService)
    {
        _settingsService = settingsService;
        _logService = logService;
    }

    public bool IsListening => _listenTask is { IsCompleted: false };

    /// <summary>按当前设置启动发现监听（非 Lan 模式或未启用时自动跳过）。</summary>
    public void Start()
    {
        Stop();

        if (!ShouldRun())
        {
            return;
        }

        _cts = new CancellationTokenSource();
        _listenTask = Task.Run(() => ListenLoopAsync(_cts.Token));
    }

    public void Stop()
    {
        _cts?.Cancel();
        _cts?.Dispose();
        _cts = null;
        _udpClient?.Dispose();
        _udpClient = null;
        _listenTask = null;
    }

    private bool ShouldRun()
    {
        var settings = _settingsService.Current;
        return settings.EnableLanDiscovery
               && string.Equals(settings.ConnectionMode, "Lan", StringComparison.OrdinalIgnoreCase);
    }

    private async Task ListenLoopAsync(CancellationToken cancellationToken)
    {
        var port = _settingsService.Current.DiscoveryPort;

        try
        {
            _udpClient = new UdpClient(port);
            _logService.Info("LanDiscovery", $"开始监听 UDP 发现端口 {port}，等待 Android 探测。");

            while (!cancellationToken.IsCancellationRequested)
            {
                var result = await _udpClient.ReceiveAsync(cancellationToken).ConfigureAwait(false);
                await HandleDatagramAsync(result.Buffer, result.RemoteEndPoint, cancellationToken).ConfigureAwait(false);
            }
        }
        catch (OperationCanceledException)
        {
            // 正常取消，忽略
        }
        catch (SocketException ex)
        {
            if (!cancellationToken.IsCancellationRequested)
            {
                _logService.Warning("LanDiscovery", $"发现监听端口 {port} 不可用：{ex.Message}");
            }
        }
        catch (Exception ex)
        {
            _logService.Warning("LanDiscovery", $"发现监听异常：{ex.Message}");
        }
    }

    private async Task HandleDatagramAsync(byte[] buffer, IPEndPoint remoteEndPoint, CancellationToken cancellationToken)
    {
        try
        {
            var json = Encoding.UTF8.GetString(buffer);
            using var doc = JsonDocument.Parse(json);
            var root = doc.RootElement;

            if (!root.TryGetProperty("t", out var typeProperty)
                || !string.Equals(typeProperty.GetString(), ProbeType, StringComparison.Ordinal))
            {
                return;
            }

            if (!root.TryGetProperty("app", out var appProperty) || string.IsNullOrWhiteSpace(appProperty.GetString()))
            {
                return;
            }

            var localIp = ResolveLocalIpv4();
            if (string.IsNullOrWhiteSpace(localIp))
            {
                _logService.Warning("LanDiscovery", "未能解析本机 IPv4，跳过公告回复。");
                return;
            }

            var announce = new
            {
                t = AnnounceType,
                name = Environment.MachineName,
                host = localIp,
                port = _settingsService.Current.LanListenPort,
                ver = 1
            };

            var payload = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(announce, _jsonOptions));
            await _udpClient!.SendAsync(payload, payload.Length, remoteEndPoint).ConfigureAwait(false);
            _logService.Info("LanDiscovery", $"已响应 {remoteEndPoint.Address} 的发现探测。");
        }
        catch (Exception ex)
        {
            _logService.Warning("LanDiscovery", $"处理发现探测失败：{ex.Message}");
        }
    }

    private static string? ResolveLocalIpv4()
    {
        try
        {
            foreach (var address in Dns.GetHostAddresses(Dns.GetHostName()))
            {
                if (address.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(address))
                {
                    return address.ToString();
                }
            }
        }
        catch
        {
            // ignore
        }

        return null;
    }

    public void Dispose()
    {
        Stop();
    }
}
