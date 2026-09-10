# ==============================================================================
# AI BOTOY Local Rapid Build & Release Pipeline (Option A)
#
# Usage:
#   .\publish.ps1 [-VersionName "1.6.1"] [-ReleaseNotes "Notes..."]
#   .\publish.ps1 -NotesFile "release-notes.md"
#   .\publish.ps1 -SkipTests
#
# Configuration:
#   Create local.signing.properties in project root (ignored by .gitignore):
#     KEYSTORE_PATH=signing.keystore
#     KEY_ALIAS=your_key_alias
#     KEYSTORE_PASSWORD=your_keystore_password
#     KEY_PASSWORD=your_key_password
# ==============================================================================

[CmdletBinding()]
param (
    [Parameter(Position = 0)]
    [string]$VersionName,

    [Parameter(Position = 1)]
    [string]$ReleaseNotes,

    [string]$NotesFile,

    [string]$PropertiesFile = "local.signing.properties",

    [switch]$SkipTests,

    [switch]$SkipGitPush,

    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host "Usage:"
    Write-Host "  .\publish.ps1 [-VersionName <version>] [-ReleaseNotes <notes>]"
    Write-Host "  .\publish.ps1 -NotesFile <path-to-notes.md>"
    Write-Host "  .\publish.ps1 -SkipTests       # Skip unit tests for faster build"
    Write-Host "  .\publish.ps1 -SkipGitPush     # Build & sign without pushing to git"
    Write-Host ""
    Write-Host "Configuration:"
    Write-Host "  Create local.signing.properties in project root (git ignored):"
    Write-Host "    KEYSTORE_PATH=signing.keystore"
    Write-Host "    KEY_ALIAS=your_key_alias"
    Write-Host "    KEYSTORE_PASSWORD=your_keystore_password"
    Write-Host "    KEY_PASSWORD=your_key_password"
    exit 0
}

$startTime = [System.DateTime]::Now
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  AI BOTOY Local Build & Release Pipeline" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

# 1. Environment: JDK 17
$candidateJdks = @(
    "D:\dev\jdk-17",
    "C:\Users\awxds\AppData\Local\Temp\aichat-jdk17-validation\jdk17\jdk-17.0.20.1+1",
    "C:\Program Files\Java\jdk-17"
)
$foundJdk17 = $false
foreach ($jdk in $candidateJdks) {
    if (Test-Path "$jdk\bin\java.exe") {
        $env:JAVA_HOME = $jdk
        $env:Path = $jdk + "\bin;" + $env:Path
        Write-Host "[ENV] JAVA_HOME set to: $jdk" -ForegroundColor Green
        $foundJdk17 = $true
        break
    }
}

if (-not $foundJdk17 -and (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe"))) {
    Write-Error "Valid JDK 17 not found. Please set `$env:JAVA_HOME or install JDK 17."
}

# 2. Environment: Android SDK & apksigner
if (-not $env:ANDROID_HOME -or -not (Test-Path $env:ANDROID_HOME)) {
    if (Test-Path "D:\dev\android-sdk") {
        $env:ANDROID_HOME = "D:\dev\android-sdk"
    } elseif (Test-Path "$env:LOCALAPPDATA\Android\Sdk") {
        $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
    }
}

$apkSigner = $null
$buildToolsVersions = @("36.0.0", "36.1.0", "35.0.0")
foreach ($bt in $buildToolsVersions) {
    $candidate = "$env:ANDROID_HOME\build-tools\$bt\apksigner.bat"
    if (Test-Path $candidate) {
        $apkSigner = $candidate
        break
    }
}

if (-not $apkSigner) {
    Write-Error "apksigner.bat not found. Please check Android SDK build-tools installation."
}
Write-Host "[ENV] apksigner located: $apkSigner" -ForegroundColor Green

# 3. Read version from app/build.gradle.kts
$gradleBuildFile = "app\build.gradle.kts"
if (-not (Test-Path $gradleBuildFile)) {
    Write-Error "$gradleBuildFile not found."
}

$gradleContent = Get-Content $gradleBuildFile -Raw
if ($gradleContent -match 'versionCode\s*=\s*(\d+)') {
    $versionCode = [int]$matches[1]
} else {
    Write-Error "Failed to parse versionCode from $gradleBuildFile"
}

if (-not $VersionName) {
    if ($gradleContent -match 'versionName\s*=\s*"([^"]+)"') {
        $VersionName = $matches[1]
    } else {
        Write-Error "Failed to parse versionName from $gradleBuildFile"
    }
}

Write-Host "[VERSION] Target Version: $VersionName (versionCode: $versionCode)" -ForegroundColor Yellow

# 4. Read signing properties
$keystorePath = "signing.keystore"
$keyAlias = $null
$keystorePassword = $null
$keyPassword = $null

if (Test-Path $PropertiesFile) {
    Get-Content $PropertiesFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#")) {
            $parts = $line.Split("=", 2)
            if ($parts.Length -eq 2) {
                $k = $parts[0].Trim()
                $v = $parts[1].Trim()
                if ($k -eq "KEYSTORE_PATH") { $keystorePath = $v }
                if ($k -eq "KEY_ALIAS") { $keyAlias = $v }
                if ($k -eq "KEYSTORE_PASSWORD") { $keystorePassword = $v }
                if ($k -eq "KEY_PASSWORD") { $keyPassword = $v }
            }
        }
    }
}

# Fallback to environment variables
if (-not $keyAlias) { $keyAlias = $env:AI_CHAT_SIGNING_KEY_ALIAS }
if (-not $keystorePassword) { $keystorePassword = $env:AI_CHAT_SIGNING_KEYSTORE_PASSWORD }
if (-not $keyPassword) { $keyPassword = $env:AI_CHAT_SIGNING_KEY_PASSWORD }

if (-not (Test-Path $keystorePath)) {
    Write-Error "Keystore file not found: $keystorePath. Please place keystore or set KEYSTORE_PATH in $PropertiesFile."
}

if (-not $keyAlias -or -not $keystorePassword -or -not $keyPassword) {
    Write-Error "Incomplete signing config. Please ensure KEY_ALIAS, KEYSTORE_PASSWORD, and KEY_PASSWORD are set in $PropertiesFile."
}

# 5. Read release notes
if ($NotesFile -and (Test-Path $NotesFile)) {
    $ReleaseNotes = Get-Content $NotesFile -Raw -Encoding UTF8
}
if (-not $ReleaseNotes) {
    $ReleaseNotes = "AI BOTOY $VersionName release."
}

# 6. Build
if (-not (Test-Path "dist")) {
    New-Item -ItemType Directory -Force -Path "dist" | Out-Null
}

if (-not $SkipTests) {
    Write-Host "[TEST] Running unit tests..." -ForegroundColor Cyan
    & ".\gradlew.bat" :app:testDebugUnitTest --no-daemon
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Unit tests failed. Aborting release."
    }
}

Write-Host "[BUILD] Compiling Release APK..." -ForegroundColor Cyan
& ".\gradlew.bat" :app:assembleRelease --no-daemon -PUPDATE_MANIFEST_URL="https://github.com/GodBook/ai-chat-android/releases/latest/download/latest.json"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Compiling Release APK failed."
}

$unsignedApk = "app\build\outputs\apk\release\app-release-unsigned.apk"
if (-not (Test-Path $unsignedApk)) {
    Write-Error "Unsigned APK not found: $unsignedApk"
}

# 7. Sign and verify
$signedApkName = "ai-botoy-$VersionName.apk"
$signedApkPath = "dist\$signedApkName"
if (Test-Path $signedApkPath) { Remove-Item -Force $signedApkPath }

Write-Host "[SIGN] Signing APK with production keystore..." -ForegroundColor Cyan
& $apkSigner sign --ks "$keystorePath" --ks-key-alias "$keyAlias" --ks-pass "pass:$keystorePassword" --key-pass "pass:$keyPassword" --out "$signedApkPath" "$unsignedApk"
if ($LASTEXITCODE -ne 0) {
    Write-Error "APK signing failed."
}

Write-Host "[VERIFY] Verifying APK signature..." -ForegroundColor Cyan
& $apkSigner verify --verbose "$signedApkPath"
if ($LASTEXITCODE -ne 0) {
    Write-Error "APK signature verification failed."
}

# 8. Compute SHA-256 and generate latest.json
$sha256 = (Get-FileHash "$signedApkPath" -Algorithm SHA256).Hash.ToLower()
$downloadUrl = "https://github.com/GodBook/ai-chat-android/releases/download/v$VersionName/$signedApkName"

$manifestObj = [ordered]@{
    versionCode = $versionCode
    versionName = $VersionName
    downloadUrl = $downloadUrl
    sha256 = $sha256
    releaseNotes = $ReleaseNotes
}

$manifestPath = "dist\latest.json"
$manifestJson = $manifestObj | ConvertTo-Json -Depth 5
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText((Resolve-Path .).Path + "\$manifestPath", $manifestJson, $utf8NoBom)
Write-Host "[MANIFEST] Generated $manifestPath (SHA-256: $sha256)" -ForegroundColor Green

# 9. Git commit & tag
$tag = "v$VersionName"
if (-not $SkipGitPush) {
    Write-Host "[GIT] Checking status and pushing to origin..." -ForegroundColor Cyan
    git add .
    $status = git status --porcelain
    if ($status) {
        git commit -m "Release $VersionName"
    }
    git push origin main

    $existingTag = git tag -l "$tag"
    if ($existingTag) {
        Write-Host "[GIT] Tag $tag already exists, skipping tag creation." -ForegroundColor Yellow
    } else {
        git tag "$tag"
        git push origin "$tag"
        Write-Host "[GIT] Created and pushed tag $tag" -ForegroundColor Green
    }
}

# 10. Publish to GitHub Release via gh CLI
Write-Host "[RELEASE] Uploading release assets to GitHub..." -ForegroundColor Cyan
$releaseExists = $false
try {
    & gh release view "$tag" >$null 2>&1
    if ($LASTEXITCODE -eq 0) {
        $releaseExists = $true
    }
} catch {
    $releaseExists = $false
}

$notesFilePath = "dist\release-notes-$VersionName.md"
[System.IO.File]::WriteAllText((Resolve-Path .).Path + "\$notesFilePath", $ReleaseNotes, $utf8NoBom)

if ($releaseExists) {
    Write-Host "[RELEASE] Updating existing Release: $tag" -ForegroundColor Cyan
    & gh release edit "$tag" --title "AI BOTOY $VersionName" --notes-file "$notesFilePath"
    & gh release upload "$tag" "$signedApkPath" "$manifestPath" --clobber
} else {
    Write-Host "[RELEASE] Creating new Release: $tag" -ForegroundColor Cyan
    & gh release create "$tag" "$signedApkPath" "$manifestPath" --title "AI BOTOY $VersionName" --notes-file "$notesFilePath"
}

if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to upload GitHub release."
}

$elapsed = [System.DateTime]::Now - $startTime
Write-Host "==================================================" -ForegroundColor Green
Write-Host "  Success! Total time: $($elapsed.TotalSeconds.ToString('F1')) seconds" -ForegroundColor Green
Write-Host "  Release URL: https://github.com/GodBook/ai-chat-android/releases/tag/$tag" -ForegroundColor Green
Write-Host "  Signed APK: $signedApkPath" -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Green
