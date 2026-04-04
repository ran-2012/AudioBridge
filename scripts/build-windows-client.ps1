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
