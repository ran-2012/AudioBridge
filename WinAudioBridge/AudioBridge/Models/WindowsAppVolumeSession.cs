namespace WpfApp1.Models;

public sealed class WindowsAppVolumeSession
{
    public string SessionId { get; init; } = string.Empty;

    public int ProcessId { get; init; }

    public string ProcessName { get; init; } = string.Empty;

    public string DisplayName { get; init; } = string.Empty;

    public string State { get; init; } = string.Empty;

    public float Volume { get; init; }

    public bool IsMuted { get; init; }

    public string? IconPath { get; init; }

    public string? ExecutablePath { get; init; }

    public string IconKey { get; init; } = string.Empty;

    public string IconHash { get; init; } = string.Empty;
}