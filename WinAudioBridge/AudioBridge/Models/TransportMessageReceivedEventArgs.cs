namespace WpfApp1.Models;

public sealed class TransportMessageReceivedEventArgs : EventArgs
{
    public TransportMessageReceivedEventArgs(BridgeMessageType messageType, byte[] payload)
    {
        MessageType = messageType;
        Payload = payload;
    }

    public BridgeMessageType MessageType { get; }

    public byte[] Payload { get; }

    public string GetPayloadAsUtf8() => System.Text.Encoding.UTF8.GetString(Payload);
}