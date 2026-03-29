using System.Buffers.Binary;
using System.IO;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using WpfApp1.Models;

namespace WpfApp1.Services;

public sealed class AudioTransportService : IAsyncDisposable
{
    private const uint Magic = 0x57414231;
    private readonly AppLogService _logService;
    private readonly SemaphoreSlim _writeLock = new(1, 1);
    private TcpClient? _client;
    private NetworkStream? _stream;
    private CancellationTokenSource? _receiveLoopCancellationTokenSource;
    private Task? _receiveLoopTask;
    private uint _sentFrameCount;

    public AudioTransportService(AppLogService logService)
    {
        _logService = logService;
    }

    public bool IsConnected => _client?.Connected == true && _stream is not null;

    public event EventHandler<TransportMessageReceivedEventArgs>? MessageReceived;

    public async Task ConnectAsync(string host, int port, CancellationToken cancellationToken = default)
    {
        await DisconnectAsync();

        _client = new TcpClient();
        _logService.Info("Transport", $"开始连接 {host}:{port}。");
        using var registration = cancellationToken.Register(() => _client.Dispose());
        await _client.ConnectAsync(host, port, cancellationToken);
        _stream = _client.GetStream();
        _sentFrameCount = 0;
        _receiveLoopCancellationTokenSource = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        _receiveLoopTask = Task.Run(() => ReceiveLoopAsync(_receiveLoopCancellationTokenSource.Token), CancellationToken.None);
        _logService.Info("Transport", $"TCP 连接已建立：{host}:{port}。");
    }

    public async Task SendSessionHeaderAsync(StreamingSessionOptions options, CancellationToken cancellationToken = default)
    {
        EnsureConnected();

        var payload = new byte[18];
        BinaryPrimitives.WriteUInt16LittleEndian(payload.AsSpan(0, 2), GetEncodingCode(options.Encoding));
        BinaryPrimitives.WriteUInt32LittleEndian(payload.AsSpan(2, 4), (uint)options.SampleRate);
        BinaryPrimitives.WriteUInt16LittleEndian(payload.AsSpan(6, 2), (ushort)options.Channels);
        BinaryPrimitives.WriteUInt16LittleEndian(payload.AsSpan(8, 2), (ushort)options.BitsPerSample);
        BinaryPrimitives.WriteUInt32LittleEndian(payload.AsSpan(10, 4), (uint)options.BufferMilliseconds);
        BinaryPrimitives.WriteUInt32LittleEndian(payload.AsSpan(14, 4), 0);

        await WritePacketAsync(BridgeMessageType.SessionInit, payload, cancellationToken);
        _logService.Info("Transport", $"已发送 SessionInit：编码={options.Encoding}，采样率={options.SampleRate}，声道={options.Channels}，位深={options.BitsPerSample}，Buffer={options.BufferMilliseconds}ms。");
    }

    public async Task SendAudioFrameAsync(byte[] buffer, int bytesRecorded, uint sequence, CancellationToken cancellationToken = default)
    {
        EnsureConnected();

        if (bytesRecorded <= 0)
        {
            _logService.Warning("Transport", $"忽略零长度音频帧：sequence={sequence}。");
            return;
        }

        var payload = new byte[12 + bytesRecorded];
        BinaryPrimitives.WriteUInt32LittleEndian(payload.AsSpan(0, 4), sequence);
        BinaryPrimitives.WriteInt64LittleEndian(payload.AsSpan(4, 8), DateTimeOffset.UtcNow.ToUnixTimeMilliseconds());
        Buffer.BlockCopy(buffer, 0, payload, 12, bytesRecorded);

        await WritePacketAsync(BridgeMessageType.AudioFrame, payload, cancellationToken);
        _sentFrameCount++;
        if (_sentFrameCount == 1 || _sentFrameCount % 200 == 0)
        {
            _logService.Info("Transport", $"已发送音频帧：count={_sentFrameCount}，sequence={sequence}，bytes={bytesRecorded}。");
        }
    }

    public Task SendJsonControlMessageAsync(BridgeMessageType messageType, string json, CancellationToken cancellationToken = default)
    {
        EnsureConnected();
        var payload = Encoding.UTF8.GetBytes(json);
        return WritePacketAsync(messageType, payload, cancellationToken);
    }

    public async Task DisconnectAsync()
    {
        if (_client is null && _stream is null)
        {
            return;
        }

        if (_stream is not null)
        {
            _receiveLoopCancellationTokenSource?.Cancel();

            await _stream.DisposeAsync();
            _stream = null;
        }

        if (_receiveLoopTask is not null)
        {
            try
            {
                await _receiveLoopTask;
            }
            catch (OperationCanceledException)
            {
                // ignore
            }
            catch (IOException)
            {
                // ignore
            }
        }

        _receiveLoopTask = null;
        _receiveLoopCancellationTokenSource?.Dispose();
        _receiveLoopCancellationTokenSource = null;

        _client?.Dispose();
        _client = null;
        _logService.Info("Transport", "TCP 连接已断开。");
    }

    public async ValueTask DisposeAsync()
    {
        await DisconnectAsync();
    }

    private async Task WritePacketAsync(BridgeMessageType messageType, byte[] payload, CancellationToken cancellationToken)
    {
        var header = new byte[12];
        BinaryPrimitives.WriteUInt32LittleEndian(header.AsSpan(0, 4), Magic);
        BinaryPrimitives.WriteUInt16LittleEndian(header.AsSpan(4, 2), 1);
        BinaryPrimitives.WriteUInt16LittleEndian(header.AsSpan(6, 2), (ushort)messageType);
        BinaryPrimitives.WriteUInt32LittleEndian(header.AsSpan(8, 4), (uint)payload.Length);

        await _writeLock.WaitAsync(cancellationToken);
        try
        {
            await _stream!.WriteAsync(header, cancellationToken);
            await _stream.WriteAsync(payload, cancellationToken);
            await _stream.FlushAsync(cancellationToken);
        }
        finally
        {
            _writeLock.Release();
        }
    }

    private async Task ReceiveLoopAsync(CancellationToken cancellationToken)
    {
        if (_stream is null)
        {
            return;
        }

        try
        {
            while (!cancellationToken.IsCancellationRequested)
            {
                var header = await ReadExactlyAsync(12, cancellationToken);
                var magic = BinaryPrimitives.ReadUInt32LittleEndian(header.AsSpan(0, 4));
                if (magic != Magic)
                {
                    throw new InvalidDataException($"收到非法协议 Magic：{magic:X8}");
                }

                var version = BinaryPrimitives.ReadUInt16LittleEndian(header.AsSpan(4, 2));
                if (version != 1)
                {
                    throw new InvalidDataException($"收到不支持的协议版本：{version}");
                }

                var messageType = (BridgeMessageType)BinaryPrimitives.ReadUInt16LittleEndian(header.AsSpan(6, 2));
                var payloadLength = BinaryPrimitives.ReadUInt32LittleEndian(header.AsSpan(8, 4));
                if (payloadLength > 1024 * 1024)
                {
                    throw new InvalidDataException($"收到非法负载长度：{payloadLength}");
                }

                var payload = await ReadExactlyAsync((int)payloadLength, cancellationToken);
                MessageReceived?.Invoke(this, new TransportMessageReceivedEventArgs(messageType, payload));
            }
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (ObjectDisposedException)
        {
            // ignore
        }
        catch (IOException ex)
        {
            if (!cancellationToken.IsCancellationRequested)
            {
                _logService.Warning("Transport", $"接收控制消息时连接已断开：{ex.Message}");
            }
        }
        catch (Exception ex)
        {
            if (!cancellationToken.IsCancellationRequested)
            {
                _logService.Error("Transport", $"接收控制消息失败：{ex.Message}");
            }
        }
    }

    private async Task<byte[]> ReadExactlyAsync(int length, CancellationToken cancellationToken)
    {
        var buffer = new byte[length];
        var offset = 0;

        while (offset < length)
        {
            var count = await _stream!.ReadAsync(buffer.AsMemory(offset, length - offset), cancellationToken);
            if (count == 0)
            {
                throw new IOException("远端已关闭连接。 ");
            }

            offset += count;
        }

        return buffer;
    }

    private static ushort GetEncodingCode(string encoding) => encoding switch
    {
        "Float32" => 2,
        "Opus" => 3,
        _ => 1
    };

    private void EnsureConnected()
    {
        if (!IsConnected)
        {
            throw new InvalidOperationException("TCP 连接尚未建立。请先完成传输连接初始化。");
        }
    }
}
