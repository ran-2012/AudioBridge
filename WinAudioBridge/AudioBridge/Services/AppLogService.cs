using System.Collections.ObjectModel;
using WpfApp1.Models;

namespace WpfApp1.Services;

public sealed class AppLogService
{
    private const int MaxEntries = 300;
    private readonly ObservableCollection<AppLogEntry> _entries = new();
    private readonly ReadOnlyObservableCollection<AppLogEntry> _readonlyEntries;
    private readonly object _syncRoot = new();

    public AppLogService()
    {
        _readonlyEntries = new ReadOnlyObservableCollection<AppLogEntry>(_entries);
    }

    public ReadOnlyObservableCollection<AppLogEntry> Entries => _readonlyEntries;

    public void Info(string source, string message) => Add("INFO", source, message);

    public void Warning(string source, string message) => Add("WARN", source, message);

    public void Error(string source, string message) => Add("ERROR", source, message);

    private void Add(string level, string source, string message)
    {
        var entry = new AppLogEntry
        {
            Timestamp = DateTime.Now,
            Level = level,
            Source = source,
            Message = message
        };

        var dispatcher = System.Windows.Application.Current?.Dispatcher;
        if (dispatcher is not null && !dispatcher.CheckAccess())
        {
            dispatcher.Invoke(() => AddEntry(entry));
            return;
        }

        AddEntry(entry);
    }

    private void AddEntry(AppLogEntry entry)
    {
        lock (_syncRoot)
        {
            _entries.Insert(0, entry);
            while (_entries.Count > MaxEntries)
            {
                _entries.RemoveAt(_entries.Count - 1);
            }
        }
    }
}