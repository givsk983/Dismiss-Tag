param([string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk")
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    $platform = Get-ChildItem -LiteralPath "$SdkRoot\platforms" -Directory |
        Where-Object Name -Match '^android-\d+$' |
        Sort-Object { [int]($_.Name -replace 'android-', '') } -Descending | Select-Object -First 1
    if (!$platform) { throw 'Android SDK platform not found.' }
    $androidJar = Join-Path $platform.FullName 'android.jar'
    $output = '.build-cache/sim-checks'
    New-Item -ItemType Directory -Force $output | Out-Null
    # Standalone BuildConfig avoids requiring an APK build before running these checks.
    $buildConfig = Join-Path $output 'BuildConfig.java'
    Set-Content -LiteralPath $buildConfig -Encoding UTF8 -Value 'package io.github.appprivacyprofile; final class BuildConfig { static final String APPLICATION_ID = "io.github.appprivacyprofile"; }'
    $source = 'app/src/main/java/io/github/appprivacyprofile'
    javac -encoding UTF-8 -d $output -cp $androidJar "$source/CarrierProfile.java" "$source/SimProfile.java" "$source/Profile.java" $buildConfig 'tests/io/github/appprivacyprofile/SimProfileChecks.java'
    if ($LASTEXITCODE -ne 0) { throw 'SIM check compilation failed.' }
    java -cp "$output;$androidJar" io.github.appprivacyprofile.SimProfileChecks
    if ($LASTEXITCODE -ne 0) { throw 'SIM regression checks failed.' }
} finally { Pop-Location }
