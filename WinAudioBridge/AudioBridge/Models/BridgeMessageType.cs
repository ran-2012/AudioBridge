namespace WpfApp1.Models;

public enum BridgeMessageType : ushort
{
    SessionInit = 0x01,
    AudioFrame = 0x02,
    Heartbeat = 0x03,
    Status = 0x04,
    Stop = 0x05,
    VolumeCatalogRequest = 0x10,
    VolumeCatalogSnapshot = 0x11,
    VolumeSetMasterRequest = 0x12,
    VolumeSetSessionRequest = 0x13,
    VolumeSessionDelta = 0x14,
    IconContentRequest = 0x15,
    IconContentResponse = 0x16,
    CommandAck = 0x17
}