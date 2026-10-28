$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$libs = Join-Path $root 'libs'
$assets = Join-Path $root 'assets'

New-Item -ItemType Directory -Force -Path $libs, (Join-Path $libs 'aarx'), (Join-Path $libs 'jni\arm64-v8a'), $assets | Out-Null

function Get-File([string]$url, [string]$dst) {
    if (Test-Path $dst) {
        Write-Host "已存在：$dst"
        return
    }
    Write-Host "下载：$url"
    Invoke-WebRequest -Uri $url -OutFile $dst -UseBasicParsing
}

# 1) sherpa-onnx Android AAR
$aar = Join-Path $libs 'sherpa-onnx-2.0.1.aar'
Get-File 'https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-2.0.1.aar' $aar

# 2) kotlin-stdlib
Get-File 'https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/1.9.24/kotlin-stdlib-1.9.24.jar' (Join-Path $libs 'kotlin-stdlib.jar')

# 3) 解压 AAR：classes.jar + arm64 原生库
$tmpAar = Join-Path $root 'tmp\aar'
New-Item -ItemType Directory -Force -Path $tmpAar | Out-Null
tar -xf $aar -C $tmpAar
Copy-Item (Join-Path $tmpAar 'classes.jar') (Join-Path $libs 'aarx\classes.jar') -Force
Copy-Item (Join-Path $tmpAar 'jni\arm64-v8a\*.so') (Join-Path $libs 'jni\arm64-v8a\') -Force

# 4) SenseVoice INT8 模型
$modelTar = Join-Path $root 'tmp\sense-voice.tar.bz2'
Get-File 'https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2' $modelTar
$tmpModel = Join-Path $root 'tmp\model'
New-Item -ItemType Directory -Force -Path $tmpModel | Out-Null
tar -xjf $modelTar -C $tmpModel
$mdir = Join-Path $tmpModel 'sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17'
Copy-Item (Join-Path $mdir 'model.int8.onnx') (Join-Path $assets 'model.int8.onnx') -Force
Copy-Item (Join-Path $mdir 'tokens.txt') (Join-Path $assets 'tokens.txt') -Force

# 5) silero VAD 模型
Get-File 'https://cdn.jsdelivr.net/gh/snakers4/silero-vad@master/src/silero_vad/data/silero_vad.onnx' (Join-Path $assets 'silero_vad.onnx')

Write-Host ''
Write-Host '依赖准备完成。接下来运行：'
Write-Host '  powershell -ExecutionPolicy Bypass -File .\scripts\build.ps1 -Sdk <AndroidSDK> -Jdk <JDK17>'
