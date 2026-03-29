namespace WpfApp1.Models;

public sealed class AudioFrameCapturedEventArgs : EventArgs
{
    public AudioFrameCapturedEventArgs(byte[] buffer, int bytesRecorded)
    {
        Buffer = buffer;
        BytesRecorded = bytesRecorded;
    }

    public byte[] Buffer { get; }

    public int BytesRecorded { get; }
}
