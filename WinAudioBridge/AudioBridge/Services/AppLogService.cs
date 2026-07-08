using System.Collections.ObjectModel;
using System.IO;
using System.Text;
using WpfApp1.Models;

namespace WpfApp1.Services;

public sealed class AppLogService
{
    private const int MaxEntries = 300;
    private readonly ObservableCollection<AppLogEntry> _entries = new();
    private readonly ReadOnlyObservableCollection<AppLogEntry> _readonlyEntries;
    private readonly object _syncRoot = new();
    private readonly object _fileSyncRoot = new();
    private readonly string _logFilePath;

    public AppLogService()
    {
        _readonlyEntries = new ReadOnlyObservableCollection<AppLogEntry>(_entries);

        var logDirectory = Path.Combine(AppContext.BaseDirectory, "logs");
        _logFilePath = Path.Combine(logDirectory, $"winaudiobridge-{DateTime.Now:yyyyMMdd}.log");
        try
        {
            Directory.CreateDirectory(logDirectory);
        }
        catch
        {
            // 日志目录创建失败时忽略，仅保留内存日志。
        }
    }

    public string LogFilePath => _logFilePath;

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

        WriteToFile(entry);

        var dispatcher = System.Windows.Application.Current?.Dispatcher;
        if (dispatcher is not null && !dispatcher.CheckAccess())
        {
            dispatcher.Invoke(() => AddEntry(entry));
            return;
        }

        AddEntry(entry);
    }

    private void WriteToFile(AppLogEntry entry)
    {
        var line = $"{entry.Timestamp:yyyy-MM-dd HH:mm:ss.fff} [{entry.Level}] [{entry.Source}] {entry.Message}";
        try
        {
            lock (_fileSyncRoot)
            {
                File.AppendAllText(_logFilePath, line + Environment.NewLine, Encoding.UTF8);
            }
        }
        catch
        {
            // 写文件失败时忽略，避免影响主流程。
        }
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