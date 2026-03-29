using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Security.Cryptography;
using System.Text;

namespace WpfApp1.Services;

public sealed class VolumeIconService
{
    private const int TargetIconSize = 64;
    private readonly AppLogService _logService;

    public VolumeIconService(AppLogService logService)
    {
        _logService = logService;
    }

    public string CreateIconKey(string? iconPath, string? executablePath, int processId, string sessionId)
    {
        var seed = string.Join('|', processId, sessionId, executablePath ?? string.Empty, iconPath ?? string.Empty);
        return ComputeSha256(seed);
    }

    public string GetIconHash(string? iconPath, string? executablePath)
    {
        var source = ResolveExistingPath(iconPath, executablePath);
        if (source is null)
        {
            return string.Empty;
        }

        try
        {
            var fileInfo = new FileInfo(source);
            var seed = $"{fileInfo.FullName}|{fileInfo.Length}|{fileInfo.LastWriteTimeUtc.Ticks}";
            return ComputeSha256(seed);
        }
        catch (Exception ex)
        {
            _logService.Warning("VolumeIcon", $"计算图标摘要失败：{ex.Message}");
            return string.Empty;
        }
    }

    public byte[]? TryGetPngBytes(string? iconPath, string? executablePath)
    {
        var source = ResolveExistingPath(iconPath, executablePath);
        if (source is null)
        {
            return null;
        }

        try
        {
            using var icon = Icon.ExtractAssociatedIcon(source);
            if (icon is null)
            {
                return null;
            }

            using var bitmap = new Bitmap(icon.ToBitmap(), new Size(TargetIconSize, TargetIconSize));
            using var memoryStream = new MemoryStream();
            bitmap.Save(memoryStream, ImageFormat.Png);
            return memoryStream.ToArray();
        }
        catch (Exception ex)
        {
            _logService.Warning("VolumeIcon", $"提取图标失败：path={source}，error={ex.Message}");
            return null;
        }
    }

    private static string? ResolveExistingPath(string? iconPath, string? executablePath)
    {
        foreach (var candidate in new[] { iconPath, executablePath })
        {
            if (string.IsNullOrWhiteSpace(candidate))
            {
                continue;
            }

            var normalized = NormalizeIconPath(candidate);
            if (File.Exists(normalized))
            {
                return normalized;
            }
        }

        return null;
    }

    private static string NormalizeIconPath(string value)
    {
        var trimmed = value.Trim();
        var commaIndex = trimmed.IndexOf(',');
        if (commaIndex > 0)
        {
            trimmed = trimmed[..commaIndex];
        }

        return trimmed.Trim('"');
    }

    private static string ComputeSha256(string value)
    {
        var bytes = Encoding.UTF8.GetBytes(value);
        var hash = SHA256.HashData(bytes);
        return Convert.ToHexString(hash);
    }
}