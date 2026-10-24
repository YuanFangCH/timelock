param(
    [switch]$InitializeSigningKey
)

$ErrorActionPreference = 'Stop'
$signingDirectory = Join-Path $env:LOCALAPPDATA 'TimeLock\Signing'
$keystorePath = Join-Path $signingDirectory 'timelock-release.p12'
$protectedPasswordPath = Join-Path $signingDirectory 'keystore-password.dpapi'
$releaseOutputDirectory = Join-Path $PSScriptRoot '..\dist'

if ($InitializeSigningKey) {
    if ((Test-Path -LiteralPath $keystorePath) -or (Test-Path -LiteralPath $protectedPasswordPath)) {
        throw 'A signing file already exists. Refusing to replace an existing release identity.'
    }

    New-Item -ItemType Directory -Path $signingDirectory -Force | Out-Null
    $randomBytes = New-Object byte[] 36
    $randomGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $randomGenerator.GetBytes($randomBytes)
    $storePassword = [Convert]::ToBase64String($randomBytes).Replace('=', 'A').Replace('+', 'B').Replace('/', 'C')
    [Array]::Clear($randomBytes, 0, $randomBytes.Length)
    $randomGenerator.Dispose()

    try {
        if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
            throw 'Set JAVA_HOME to a JDK 17 installation before generating the signing key.'
        }
        $keytoolPath = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
        if (-not (Test-Path -LiteralPath $keytoolPath)) {
            throw "Cannot find keytool under JAVA_HOME: $env:JAVA_HOME"
        }
        & $keytoolPath -genkeypair -noprompt -storetype PKCS12 -keystore $keystorePath `
            -alias timelock-preview -keyalg RSA -keysize 2048 -validity 9125 `
            -dname 'CN=TimeLock Preview, O=TimeLock' -storepass $storePassword -keypass $storePassword *> $null
        if ($LASTEXITCODE -ne 0) { throw 'keytool could not create the release signing key.' }

        $securePassword = ConvertTo-SecureString $storePassword -AsPlainText -Force
        ConvertFrom-SecureString $securePassword | Set-Content -LiteralPath $protectedPasswordPath -Encoding ascii
    }
    catch {
        Remove-Item -LiteralPath $keystorePath -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $protectedPasswordPath -Force -ErrorAction SilentlyContinue
        throw
    }
    finally {
        $storePassword = $null
        $securePassword = $null
    }
}

if (-not (Test-Path -LiteralPath $keystorePath) -or -not (Test-Path -LiteralPath $protectedPasswordPath)) {
    throw 'Release signing is not initialized. Run this script once with -InitializeSigningKey.'
}

$passwordSecureText = (Get-Content -LiteralPath $protectedPasswordPath -Raw).Trim()
$passwordSecure = ConvertTo-SecureString $passwordSecureText
$passwordPointer = [IntPtr]::Zero
try {
    $passwordPointer = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($passwordSecure)
    $passwordPlain = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    $env:TIMELOCK_KEYSTORE_PATH = $keystorePath
    $env:TIMELOCK_KEY_ALIAS = 'timelock-preview'
    $env:TIMELOCK_STORE_PASSWORD = $passwordPlain
    $env:TIMELOCK_KEY_PASSWORD = $passwordPlain

    Push-Location (Join-Path $PSScriptRoot '..')
    try {
        & .\gradlew.bat --no-daemon :app:assembleRelease
        if ($LASTEXITCODE -ne 0) { throw 'Gradle failed to assemble the preview APK.' }
    }
    finally {
        Pop-Location
    }

    New-Item -ItemType Directory -Path $releaseOutputDirectory -Force | Out-Null
    $builtApk = Join-Path $PSScriptRoot '..\app\build\outputs\apk\release\app-release.apk'
    $buildFile = Join-Path $PSScriptRoot '..\app\build.gradle.kts'
    $versionMatch = [regex]::Match((Get-Content -LiteralPath $buildFile -Raw), 'versionName\s*=\s*"([^"]+)"')
    if (-not $versionMatch.Success) { throw 'Could not read versionName from app/build.gradle.kts.' }
    $releaseName = "TimeLock-$($versionMatch.Groups[1].Value).apk"
    $releaseApk = Join-Path $releaseOutputDirectory $releaseName
    Copy-Item -LiteralPath $builtApk -Destination $releaseApk -Force
    $digest = (Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256).Hash.ToLowerInvariant()
    "$digest  $releaseName" |
        Set-Content -LiteralPath (Join-Path $releaseOutputDirectory 'SHA256SUMS.txt') -Encoding ascii
    Write-Output "Built signed preview APK: $releaseApk"
    Write-Output "SHA-256: $digest"
}
finally {
    $env:TIMELOCK_KEYSTORE_PATH = $null
    $env:TIMELOCK_KEY_ALIAS = $null
    $env:TIMELOCK_STORE_PASSWORD = $null
    $env:TIMELOCK_KEY_PASSWORD = $null
    if ($passwordPointer -ne [IntPtr]::Zero) {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
    $passwordPlain = $null
    $passwordSecureText = $null
    $passwordSecure = $null
}
