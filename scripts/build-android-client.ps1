[CmdletBinding()]
param(
    [ValidateSet('Debug', 'Release')]
    [string]$Configuration = 'Release',

    [string]$ArtifactName
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$androidRoot = Join-Path $repoRoot 'AudioBridge'
$gradleWrapper = Join-Path $androidRoot 'gradlew.bat'
$artifactRoot = Join-Path $repoRoot 'build'

if (-not $ArtifactName) {
    $ArtifactName = "AudioBridge-android-client-$($Configuration.ToLowerInvariant()).apk"
}

$configurationLower = $Configuration.ToLowerInvariant()
$assembleTask = "assemble$Configuration"
$sourceApkPath = Join-Path $androidRoot "app\build\outputs\apk\$configurationLower\app-$configurationLower.apk"
$artifactPath = Join-Path $artifactRoot $ArtifactName

if (-not (Test-Path $gradleWrapper)) {
    throw "未找到 Gradle Wrapper: $gradleWrapper"
}

New-Item -ItemType Directory -Force -Path $artifactRoot | Out-Null

Write-Host "==> 构建 Android 客户端 ($Configuration)"
Push-Location $androidRoot
try {
    & $gradleWrapper $assembleTask
    if ($LASTEXITCODE -ne 0) {
        throw 'Gradle 构建失败。'
    }
}
finally {
    Pop-Location
}

if (-not (Test-Path $sourceApkPath)) {
    throw "未找到 APK 输出: $sourceApkPath"
}

Write-Host "==> 复制产物到 $artifactPath"
Copy-Item -Force $sourceApkPath $artifactPath

Write-Host "Android 客户端产物已生成: $artifactPath"
