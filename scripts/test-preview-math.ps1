param(
    [string]$JavaHome = 'C:\Program Files\Java\zulu25.36.205-ca-jdk25.0.4.1-win_x64'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$output = Join-Path $root 'build\manual-tests\preview-math'
$null = New-Item -ItemType Directory -Force -Path $output
$source = Join-Path $root 'src\main\java\net\createmod\ponder\foundation\ui\PreviewCameraMath.java'
$test = Join-Path $root 'src\test\java\net\createmod\ponder\foundation\ui\PreviewCameraMathTest.java'
& (Join-Path $JavaHome 'bin\javac.exe') --release 8 -d $output $source $test
if ($LASTEXITCODE -ne 0) { throw 'Preview math test compilation failed' }
& (Join-Path $JavaHome 'bin\java.exe') -cp $output net.createmod.ponder.foundation.ui.PreviewCameraMathTest
if ($LASTEXITCODE -ne 0) { throw 'Preview math tests failed' }
