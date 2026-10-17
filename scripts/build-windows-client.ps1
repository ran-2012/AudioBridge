[CmdletBinding()]
param(
    [ValidateSet('Debug', 'Release')]
    [string]$Configuration = 'Release',

    [string]$ArtifactName
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$projectPath = Join-Path $repoRoot 'WinAudioBridge\AudioBridge\AudioBridge.csproj'
$artifactRoot = Join-Path $repoRoot 'build'
$tempRoot = Join-Path $artifactRoot '.tmp'
$publishDir = Join-Path $tempRoot 'windows-client'

if (-not $ArtifactName) {
    $ArtifactName = "WinAudioBridge-windows-client-$($Configuration.ToLowerInvariant()).zip"
}

$artifactPath = Join-Path $artifactRoot $ArtifactName

if (-not (Test-Path $projectPath)) {
    throw "未找到 Windows 项目文件: $projectPath"
}

if (-not (Get-Command dotnet -ErrorAction SilentlyContinue)) {
    throw '未找到 dotnet，请先安装 .NET SDK。'
}

$repoPrefix = $repoRoot + [System.IO.Path]::DirectorySeparatorChar
$runningClients = @(Get-Process -Name AudioBridge -ErrorAction SilentlyContinue | Where-Object {
    $_.Path -and $_.Path.StartsWith($repoPrefix, [System.StringComparison]::OrdinalIgnoreCase)
})
$restartPath = $null
$savedSettings = $null
if ($runningClients.Count -gt 0) {
    $restartPath = $runningClients[0].Path
    $settingsPath = Join-Path (Split-Path -Parent $restartPath) 'settings.json'
    if (Test-Path $settingsPath) {
        $savedSettings = [System.IO.File]::ReadAllBytes($settingsPath)
    }

    Write-Host '==> 关闭正在运行的 Windows 客户端'
    $runningClients | Stop-Process -PassThru | Wait-Process -Timeout 15
}

New-Item -ItemType Directory -Force -Path $artifactRoot | Out-Null
if (Test-Path $publishDir) {
    Remove-Item -Recurse -Force $publishDir
}
New-Item -ItemType Directory -Force -Path $publishDir | Out-Null

Write-Host "==> 发布 Windows 客户端 ($Configuration)"
& dotnet publish $projectPath -c $Configuration -o $publishDir /p:UseAppHost=true
if ($LASTEXITCODE -ne 0) {
    throw 'dotnet publish 执行失败。'
}

if (Test-Path $artifactPath) {
    Remove-Item -Force $artifactPath
}

Write-Host "==> 打包产物到 $artifactPath"
Compress-Archive -Path (Join-Path $publishDir '*') -DestinationPath $artifactPath -Force

Write-Host "Windows 客户端产物已生成: $artifactPath"

if ($restartPath) {
    $restartDir = Split-Path -Parent $restartPath
    if (-not [string]::Equals($restartDir, $publishDir, [System.StringComparison]::OrdinalIgnoreCase)) {
        Write-Host "==> 更新客户端运行目录: $restartDir"
        foreach ($file in Get-ChildItem $publishDir -File -Recurse) {
            $relativePath = [System.IO.Path]::GetRelativePath($publishDir, $file.FullName)
            if ($relativePath -eq 'settings.json') {
                continue
            }

            $destination = Join-Path $restartDir $relativePath
            if ((Test-Path $destination) -and
                (Get-FileHash $file.FullName).Hash -eq (Get-FileHash $destination).Hash) {
                continue
            }

            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
            Copy-Item $file.FullName -Destination $destination -Force
        }
    }

    if ($null -ne $savedSettings) {
        [System.IO.File]::WriteAllBytes((Join-Path $restartDir 'settings.json'), $savedSettings)
    }
    Write-Host "==> 重新启动 Windows 客户端: $restartPath"
    Start-Process -FilePath $restartPath -WorkingDirectory $restartDir
}
