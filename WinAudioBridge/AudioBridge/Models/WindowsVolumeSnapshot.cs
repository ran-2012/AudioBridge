namespace WpfApp1.Models;

public sealed class WindowsVolumeSnapshot
{
    public WindowsMasterVolumeState MasterVolume { get; init; } = new();

    public IReadOnlyList<WindowsAppVolumeSession> Sessions { get; init; } = Array.Empty<WindowsAppVolumeSession>();

    public DateTimeOffset CapturedAtUtc { get; init; } = DateTimeOffset.UtcNow;

    public static WindowsVolumeSnapshot Empty { get; } = new();
}