param(
    [string]$Sdk = $env:ANDROID_HOME,
    [string]$Jdk = $env:JAVA_HOME
)

$ErrorActionPreference = 'Continue'

if (-not $Sdk) { $Sdk = $env:ANDROID_SDK_ROOT }
if (-not $Sdk) { throw "请设置 ANDROID_HOME，或用 -Sdk 指定 Android SDK 路径" }
if (-not $Jdk) { throw "请设置 JAVA_HOME，或用 -Jdk 指定 JDK 17 路径" }

$root = Split-Path -Parent $PSScriptRoot
$bt = Join-Path $Sdk 'build-tools\35.0.0'
$androidJar = Join-Path $Sdk 'platforms\android-34\android.jar'
$build = Join-Path $root 'build'
$app = Join-Path $root 'app'

if (-not (Test-Path (Join-Path $bt 'aapt2.exe'))) { throw "找不到 build-tools 35.0.0：$bt" }
if (-not (Test-Path $androidJar)) { throw "找不到 android.jar：$androidJar" }

$env:JAVA_HOME = $Jdk
$env:PATH = "$Jdk\bin;$bt;$env:PATH"

Remove-Item (Join-Path $build 'classes'), (Join-Path $build 'gen'), (Join-Path $build 'dex'),
    (Join-Path $build 'compiled.zip'), (Join-Path $build 'base.apk'), (Join-Path $build 'classes.jar'),
    (Join-Path $build 'assembled.apk'), (Join-Path $build 'aligned.apk'), (Join-Path $build 'app-release.apk') `
    -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path (Join-Path $build 'classes'), (Join-Path $build 'gen'), (Join-Path $build 'dex') | Out-Null

Write-Host '[1/7] aapt2 compile'
& (Join-Path $bt 'aapt2.exe') compile --dir (Join-Path $app 'res') -o (Join-Path $build 'compiled.zip')
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }

Write-Host '[2/7] aapt2 link'
& (Join-Path $bt 'aapt2.exe') link -o (Join-Path $build 'base.apk') -I $androidJar `
    --manifest (Join-Path $app 'AndroidManifest.xml') --java (Join-Path $build 'gen') `
    --min-sdk-version 24 --target-sdk-version 34 (Join-Path $build 'compiled.zip')
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

Write-Host '[3/7] javac'
$srcs = Get-ChildItem (Join-Path $app 'src') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$cp = "$androidJar;$(Join-Path $root 'libs\aarx\classes.jar');$(Join-Path $root 'libs\kotlin-stdlib.jar')"
& (Join-Path $Jdk 'bin\javac.exe') -source 8 -target 8 -Xlint:-options -nowarn -encoding UTF-8 `
    -classpath $cp -d (Join-Path $build 'classes') `
    (Join-Path $build 'gen\com\dsh\voicemin\R.java') @srcs 2> (Join-Path $build 'javac.err')
if ($LASTEXITCODE -ne 0) { Get-Content (Join-Path $build 'javac.err'); throw 'javac failed' }

Write-Host '[4/7] jar classes'
& (Join-Path $Jdk 'bin\jar.exe') cf (Join-Path $build 'classes.jar') -C (Join-Path $build 'classes') .
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }

Write-Host '[5/7] d8'
& (Join-Path $bt 'd8.bat') --lib $androidJar --min-api 24 --output (Join-Path $build 'dex') `
    (Join-Path $build 'classes.jar') (Join-Path $root 'libs\aarx\classes.jar') (Join-Path $root 'libs\kotlin-stdlib.jar') `
    2> (Join-Path $build 'd8.err')
if ($LASTEXITCODE -ne 0) { Get-Content (Join-Path $build 'd8.err') -Tail 30; throw 'd8 failed' }

Write-Host '[6/7] assemble'
$py = $env:VOICEMIN_PYTHON
if (-not $py) { $py = 'python' }
& $py (Join-Path $PSScriptRoot 'assemble.py')
if ($LASTEXITCODE -ne 0) { throw 'assemble failed' }

Write-Host '[7/7] zipalign + sign'
& (Join-Path $bt 'zipalign.exe') -f 4 (Join-Path $build 'assembled.apk') (Join-Path $build 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

$ks = Join-Path $build 'debug.keystore'
if (-not (Test-Path $ks)) {
    & (Join-Path $Jdk 'bin\keytool.exe') -genkeypair -keystore $ks -storepass android -keypass android `
        -alias androiddebugkey -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000 -noprompt
}

& (Join-Path $bt 'apksigner.bat') sign --ks $ks --ks-pass pass:android --key-pass pass:android `
    --ks-key-alias androiddebugkey --out (Join-Path $build 'app-release.apk') (Join-Path $build 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }

& (Join-Path $bt 'apksigner.bat') verify --verbose (Join-Path $build 'app-release.apk')
Write-Host 'DONE'
Get-Item (Join-Path $build 'app-release.apk') | Select-Object Name, Length | Format-Table -AutoSize
