[CmdletBinding()]
param(
    [ValidateSet('Debug', 'Release')]
    [string]$WindowsConfiguration = 'Release',

    [ValidateSet('Debug', 'Release')]
    [string]$AndroidConfiguration = 'Release'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$buildWindowsScript = Join-Path $PSScriptRoot 'build-windows-client.ps1'
$buildAndroidScript = Join-Path $PSScriptRoot 'build-android-client.ps1'

if (-not (Test-Path $buildWindowsScript)) {
    throw "未找到脚本: $buildWindowsScript"
}

if (-not (Test-Path $buildAndroidScript)) {
    throw "未找到脚本: $buildAndroidScript"
}

& $buildWindowsScript -Configuration $WindowsConfiguration
if ($LASTEXITCODE -ne 0) {
    throw 'Windows 客户端构建失败。'
}

& $buildAndroidScript -Configuration $AndroidConfiguration
if ($LASTEXITCODE -ne 0) {
    throw 'Android 客户端构建失败。'
}

$artifactRoot = Join-Path (Split-Path -Parent $PSScriptRoot) 'build'
Write-Host "全部客户端构建完成，产物目录: $artifactRoot"
