param(
    [string]$JavaHome = 'C:\Program Files\Java\zulu25.36.205-ca-jdk25.0.4.1-win_x64'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$output = Join-Path $root 'build\manual-tests\ui-layout-cache'
$cache = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'
$groups = 'org.junit.jupiter', 'org.junit.platform', 'org.apiguardian', 'org.opentest4j'
$jars = foreach ($group in $groups) {
    Get-ChildItem -LiteralPath (Join-Path $cache $group) -Recurse -Filter '*.jar' -File |
        Where-Object { $_.Name -notmatch '(sources|javadoc)' } |
        Select-Object -ExpandProperty FullName
}
if (!$jars) { throw 'Cached JUnit dependencies are required for the independent tests' }
$classpath = $jars -join ';'
$null = New-Item -ItemType Directory -Force -Path $output
$names = 'PreviewFrameCache', 'PreviewCaptionPaging', 'PreviewDebugLayout', 'PreviewPointerInput'
$sources = foreach ($name in $names) {
    Join-Path $root "src\main\java\net\createmod\ponder\foundation\ui\$name.java"
    Join-Path $root "src\test\java\net\createmod\ponder\foundation\ui\${name}Test.java"
}
& (Join-Path $JavaHome 'bin\javac.exe') --release 8 -encoding UTF-8 -cp $classpath -d $output @sources
if ($LASTEXITCODE -ne 0) { throw 'Preview UI tests did not compile' }
$testClasspath = $output + ';' + $classpath
$report = @'
import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.*;
import org.junit.platform.engine.discovery.*;
var listener = new SummaryGeneratingListener();
var request = LauncherDiscoveryRequestBuilder.request().selectors(DiscoverySelectors.selectClass("net.createmod.ponder.foundation.ui.PreviewFrameCacheTest"), DiscoverySelectors.selectClass("net.createmod.ponder.foundation.ui.PreviewCaptionPagingTest"), DiscoverySelectors.selectClass("net.createmod.ponder.foundation.ui.PreviewDebugLayoutTest"), DiscoverySelectors.selectClass("net.createmod.ponder.foundation.ui.PreviewPointerInputTest")).build();
LauncherFactory.create().execute(request, listener);
var summary = listener.getSummary();
summary.printTo(new java.io.PrintWriter(System.out, true));
summary.printFailuresTo(new java.io.PrintWriter(System.out, true));
System.out.println("PONDER_UI_TESTS=" + summary.getTestsFoundCount() + ";FAILED=" + summary.getTestsFailedCount() + ";ABORTED=" + summary.getTestsAbortedCount() + ";SKIPPED=" + summary.getTestsSkippedCount() + ";CONTAINER_FAILED=" + summary.getContainersFailedCount());
/exit
'@ | & (Join-Path $JavaHome 'bin\jshell.exe') --class-path $testClasspath - 2>&1
$exitCode = $LASTEXITCODE
$report | ForEach-Object { Write-Output $_ }
if ($exitCode -ne 0 -or ($report -join "`n") -notmatch 'PONDER_UI_TESTS=[1-9]\d*;FAILED=0;ABORTED=0;SKIPPED=0;CONTAINER_FAILED=0') {
    throw 'Preview UI regression tests failed'
}
