using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using WpfApp1.Models;
using WpfApp1.Services;
using Xunit;

namespace AudioBridge.Tests;

// 这些用例锁定 AudioTransportService 的连接契约：
// ConnectAsync 第一步会无条件断开既有连接（destructive reconnect）。
// 正是该行为在多触发点并发调用 StreamingCoordinator.StartStreamingAsync 时，
// 导致“设备已连接但无声音”。StreamingCoordinator 现已用 _lifecycleLock 串行化生命周期操作，
// 确保任意时刻只有一个连接流程在执行，从而不会出现并发 ConnectAsync 互相拆连接。
public sealed class AudioTransportServiceTests
{
    [Fact]
    public async Task ConnectAsync_ShouldEstablishConnection_OnSingleConnect()
    {
        using var listener = new LoopbackListener();
        await using var transport = new AudioTransportService(new AppLogService());

        await transport.ConnectAsync("127.0.0.1", listener.Port);
        var serverSide = await listener.AcceptAsync();

        Assert.True(transport.IsConnected);
        Assert.True(serverSide.Connected);
    }

    [Fact]
    public async Task ConnectAsync_ShouldTearDownPreviousConnection_WhenCalledWhileConnected()
    {
        using var listener = new LoopbackListener();
        await using var transport = new AudioTransportService(new AppLogService());

        await transport.ConnectAsync("127.0.0.1", listener.Port);
        var firstServerSide = await listener.AcceptAsync();
        Assert.True(transport.IsConnected);

        // 第二次连接：ConnectAsync 内部先 DisconnectAsync，旧连接被拆掉。
        await transport.ConnectAsync("127.0.0.1", listener.Port);
        var secondServerSide = await listener.AcceptAsync();

        Assert.True(transport.IsConnected);
        Assert.True(await IsRemoteClosedAsync(firstServerSide));
        Assert.True(secondServerSide.Connected);
    }

    [Fact]
    public async Task SendAudioFrameAsync_ShouldThrow_WhenNotConnected()
    {
        await using var transport = new AudioTransportService(new AppLogService());

        await Assert.ThrowsAsync<InvalidOperationException>(
            () => transport.SendAudioFrameAsync(new byte[] { 1, 2, 3, 4 }, 4, sequence: 1));
    }

    [Fact]
    public async Task DisconnectAsync_ShouldClearConnectedState()
    {
        using var listener = new LoopbackListener();
        await using var transport = new AudioTransportService(new AppLogService());

        await transport.ConnectAsync("127.0.0.1", listener.Port);
        _ = await listener.AcceptAsync();
        Assert.True(transport.IsConnected);

        await transport.DisconnectAsync();

        Assert.False(transport.IsConnected);
    }

    [Fact]
    public async Task SendAndroidPlaybackStatusAckAsync_ShouldSendAckPacketWithJsonPayload()
    {
        using var listener = new LoopbackListener();
        await using var transport = new AudioTransportService(new AppLogService());

        await transport.ConnectAsync("127.0.0.1", listener.Port);
        using var serverSide = await listener.AcceptAsync();

        await transport.SendAndroidPlaybackStatusAckAsync(sequence: 7, echoedTimestampElapsedRealtimeMillis: 1234);

        var header = await ReceiveExactlyAsync(serverSide, 12);
        var messageType = BitConverter.ToUInt16(header, 6);
        var payloadLength = BitConverter.ToUInt32(header, 8);
        var payload = await ReceiveExactlyAsync(serverSide, (int)payloadLength);
        var json = JsonDocument.Parse(Encoding.UTF8.GetString(payload)).RootElement;

        Assert.Equal((ushort)BridgeMessageType.AndroidPlaybackStatusAck, messageType);
        Assert.Equal(7u, json.GetProperty("sequence").GetUInt32());
        Assert.True(json.GetProperty("accepted").GetBoolean());
        Assert.Equal(1234, json.GetProperty("echoedTimestampElapsedRealtimeMillis").GetInt64());
    }

    [Fact]
    public async Task StartListening_AcceptClient_ShouldEstablishServerConnection()
    {
        int port;
        using (var probe = new LoopbackListener()) { port = probe.Port; }

        await using var transport = new AudioTransportService(new AppLogService());
        await transport.StartListeningAsync("127.0.0.1", port);
        Assert.True(transport.IsListening);

        using var client = new TcpClient();
        await client.ConnectAsync(IPAddress.Loopback, port);
        await transport.AcceptClientAsync();

        Assert.True(transport.IsConnected);
        Assert.True(client.Connected);
    }

    [Fact]
    public async Task AcceptClient_ShouldReplacePreviousConnection_WhenNewClientArrives()
    {
        int port;
        using (var probe = new LoopbackListener()) { port = probe.Port; }

        await using var transport = new AudioTransportService(new AppLogService());
        await transport.StartListeningAsync("127.0.0.1", port);

        using var first = new TcpClient();
        await first.ConnectAsync(IPAddress.Loopback, port);
        await transport.AcceptClientAsync();
        Assert.True(transport.IsConnected);

        using var second = new TcpClient();
        await second.ConnectAsync(IPAddress.Loopback, port);
        await transport.AcceptClientAsync();

        Assert.True(transport.IsConnected);
        Assert.True(await IsRemoteClosedAsync(first.Client));
        Assert.True(second.Connected);
    }

    [Fact]
    public async Task StopListening_ShouldStopAcceptingNewClients()
    {
        int port;
        using (var probe = new LoopbackListener()) { port = probe.Port; }

        await using var transport = new AudioTransportService(new AppLogService());
        await transport.StartListeningAsync("127.0.0.1", port);
        Assert.True(transport.IsListening);

        await transport.StopListeningAsync();
        Assert.False(transport.IsListening);
    }

    [Fact]
    public async Task SendLatencyProbeAsync_ShouldSend8ByteTimestampPayload()
    {
        int port;
        using (var probe = new LoopbackListener()) { port = probe.Port; }

        await using var transport = new AudioTransportService(new AppLogService());
        await transport.StartListeningAsync("127.0.0.1", port);
        using var client = new TcpClient();
        await client.ConnectAsync(IPAddress.Loopback, port);
        await transport.AcceptClientAsync();

        await transport.SendLatencyProbeAsync();

        var header = await ReceiveExactlyAsync(client.Client, 12);
        var messageType = BitConverter.ToUInt16(header, 6);
        var payloadLength = BitConverter.ToUInt32(header, 8);
        var payload = await ReceiveExactlyAsync(client.Client, (int)payloadLength);

        Assert.Equal((ushort)BridgeMessageType.LatencyProbe, messageType);
        Assert.Equal(8u, payloadLength);
        Assert.Equal(8, payload.Length);
    }

    [Fact]
    public async Task SendLatencyProbeAckAsync_ShouldEchoTimestamp()
    {
        int port;
        using (var probe = new LoopbackListener()) { port = probe.Port; }

        await using var transport = new AudioTransportService(new AppLogService());
        await transport.StartListeningAsync("127.0.0.1", port);
        using var client = new TcpClient();
        await client.ConnectAsync(IPAddress.Loopback, port);
        await transport.AcceptClientAsync();

        long timestamp = 1_700_000_000_123L;
        await transport.SendLatencyProbeAckAsync(timestamp);

        var header = await ReceiveExactlyAsync(client.Client, 12);
        var messageType = BitConverter.ToUInt16(header, 6);
        var payloadLength = BitConverter.ToUInt32(header, 8);
        var payload = await ReceiveExactlyAsync(client.Client, (int)payloadLength);

        Assert.Equal((ushort)BridgeMessageType.LatencyProbeAck, messageType);
        Assert.Equal(timestamp, BitConverter.ToInt64(payload, 0));
    }

    private static async Task<bool> IsRemoteClosedAsync(Socket socket)
    {
        // 远端关闭后，读取应立即返回 0 字节。轮询短暂等待异步断开完成。
        var buffer = new byte[1];
        for (var attempt = 0; attempt < 50; attempt++)
        {
            if (socket.Poll(0, SelectMode.SelectRead))
            {
                var read = await socket.ReceiveAsync(buffer, SocketFlags.None);
                return read == 0;
            }

            await Task.Delay(20);
        }

        return false;
    }

    private static async Task<byte[]> ReceiveExactlyAsync(Socket socket, int length)
    {
        var buffer = new byte[length];
        var offset = 0;
        while (offset < length)
        {
            var read = await socket.ReceiveAsync(buffer.AsMemory(offset, length - offset), SocketFlags.None);
            if (read == 0)
            {
                throw new InvalidOperationException("Socket closed before reading expected bytes.");
            }

            offset += read;
        }

        return buffer;
    }

    private sealed class LoopbackListener : IDisposable
    {
        private readonly TcpListener _listener;

        public LoopbackListener()
        {
            _listener = new TcpListener(IPAddress.Loopback, 0);
            _listener.Start();
            Port = ((IPEndPoint)_listener.LocalEndpoint).Port;
        }

        public int Port { get; }

        public async Task<Socket> AcceptAsync() => await _listener.AcceptSocketAsync();

        public void Dispose() => _listener.Stop();
    }
}
