namespace WpfApp1.Models;

public sealed class AppLogEntry
{
    public DateTime Timestamp { get; init; } = DateTime.Now;

    public string Level { get; init; } = "INFO";

    public string Source { get; init; } = "App";

    public string Message { get; init; } = string.Empty;

    public string DisplayText => $"{Timestamp:HH:mm:ss.fff} [{Level}] [{Source}] {Message}";
}