param([string]$AndroidSdk = "$env:LOCALAPPDATA/Android/Sdk")
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath "$AndroidSdk/platforms/android-34/android.jar")) {
    throw 'Android SDK platform 34 is required.'
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'JDK 17 is required.'
}

$env:ANDROID_HOME = $AndroidSdk
$stageRoot = Join-Path $env:LOCALAPPDATA ('GhibliLauncherBuild/' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8))
$buildRoot = Join-Path $stageRoot 'project'

New-Item -ItemType Directory -Path $buildRoot -Force | Out-Null
Write-Output "Preparing ASCII staging build at: $buildRoot"

$projectDir = $PSScriptRoot
Get-ChildItem -LiteralPath $projectDir -Force | Where-Object Name -NotIn @('.git', '.gradle', 'build', 'releases', 'verification', 'art', 'local.properties') | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination $buildRoot -Recurse -Force
}

$verifDir = Join-Path $projectDir 'verification'
$releasesDir = Join-Path $projectDir 'releases'
New-Item -ItemType Directory -Path $verifDir, $releasesDir -Force | Out-Null

Push-Location $buildRoot
try {
    Write-Output "Running Gradle unit tests, lint, and assembleDebug..."
    $buildLog = Join-Path $verifDir 'native-build.log'

    $oldEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & ./gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug *> $buildLog
    $gradleExit = $LASTEXITCODE
    $ErrorActionPreference = $oldEap
    if ($gradleExit -ne 0) {
        throw "Ghibli native build failed with exit code $gradleExit. See $buildLog"
    }

    $apk = Join-Path $buildRoot 'app/build/outputs/apk/debug/app-debug.apk'
    if (-not (Test-Path -LiteralPath $apk)) {
        throw 'Build did not produce an APK.'
    }

    $aapt = Join-Path $AndroidSdk 'build-tools/34.0.0/aapt.exe'
    if (-not (Test-Path -LiteralPath $aapt)) { throw 'Android build-tools 34.0.0 are required.' }
    $badging = (& $aapt dump badging $apk) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $badging -notmatch "package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'") {
        throw 'Unable to verify APK version metadata.'
    }
    $packageName = $Matches[1]
    $versionCode = [int]$Matches[2]
    $versionName = $Matches[3]
    if ($packageName -ne 'ua.ghibli.tv.valley') { throw "Unexpected APK applicationId: $packageName" }
    $fileVersion = $versionName -replace '[^A-Za-z0-9._-]', '-'
    $outputApk = Join-Path $releasesDir ("GhibliValley-$fileVersion-debug.apk")
    Copy-Item -LiteralPath $apk -Destination $outputApk -Force

    $lintHtml = Join-Path $buildRoot 'app/build/reports/lint-results-debug.html'
    if (Test-Path -LiteralPath $lintHtml) {
        Copy-Item -LiteralPath $lintHtml -Destination (Join-Path $verifDir 'native-lint.html') -Force
    }
    $lintXml = Join-Path $buildRoot 'app/build/reports/lint-results-debug.xml'
    if (Test-Path -LiteralPath $lintXml) {
        Copy-Item -LiteralPath $lintXml -Destination (Join-Path $verifDir 'native-lint.xml') -Force
    }
    $testResults = Join-Path $buildRoot 'app/build/test-results/testDebugUnitTest'
    if (Test-Path -LiteralPath $testResults) {
        Get-ChildItem -LiteralPath $testResults -Filter '*.xml' | Copy-Item -Destination $verifDir -Force
    }
    $uiResults = Join-Path $buildRoot 'app/build/reports/native-ui'
    if (Test-Path -LiteralPath $uiResults) {
        Copy-Item -LiteralPath $uiResults -Destination $verifDir -Recurse -Force
    }

    $fileHash = (Get-FileHash -LiteralPath $outputApk -Algorithm SHA256).Hash
    $fileBytes = (Get-Item -LiteralPath $outputApk).Length

    $manifest = [ordered]@{
        file = [IO.Path]::GetFileName($outputApk)
        bytes = $fileBytes
        sha256 = $fileHash
        package = $packageName
        versionCode = $versionCode
        versionName = $versionName
        signing = 'local Android debug key; Studio Ghibli native build'
        debuggable = $true
        build_staging = $buildRoot
        built_at = (Get-Date -Format 'o')
    }

    $manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $releasesDir 'build-manifest.json') -Encoding utf8

    Write-Output "SUCCESS: Built $outputApk"
    Write-Output "SHA256: $fileHash"
    Write-Output "Size: $fileBytes bytes"
} finally {
    Pop-Location
}
