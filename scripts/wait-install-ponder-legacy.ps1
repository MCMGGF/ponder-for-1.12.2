$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$SourceJar = Join-Path $ProjectRoot 'build\libs\ponder_legacy-0.1.0-beta.jar'
$ClientRootBase64 = 'QzpcVXNlcnNcU2VlZHNcRGVza3RvcFzmlrDlu7rmlofku7blpLkgKDQpXOWFieW9sVxbSkFWQTIx54mI5pysLeaOqOiNkF3prZTmlLnlt6XlnYot55S16ISR5a6i5oi356uvIDYuMy4wVkUt6Kej5Y6L5a+G56CB77yaODg4OFzprZTmlLnlt6XlnYot5a6i5oi356uv'
$ClientRoot = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($ClientRootBase64))
$ModsDir = Join-Path $ClientRoot '.minecraft\mods'
$ModId = 'ponder_legacy'
$LogPath = Join-Path $ProjectRoot 'build\wait-install-ponder-legacy.log'

function Write-InstallLog {
    param([string] $Message)
    $line = '[{0}] {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message
    $logDir = Split-Path -Parent $LogPath
    if (-not (Test-Path -LiteralPath $logDir)) {
        New-Item -ItemType Directory -Path $logDir -Force | Out-Null
    }
    Add-Content -LiteralPath $LogPath -Value $line -Encoding UTF8
}

function Get-ProcessText {
    param($Process)
    (($Process.CommandLine, $Process.ExecutablePath, $Process.Name) -join "`n").ToLowerInvariant()
}

function Get-ClientProcess {
    $root = $ClientRoot.ToLowerInvariant()
    $minecraft = (Join-Path $ClientRoot '.minecraft').ToLowerInvariant()

    Get-CimInstance Win32_Process | Where-Object {
        if ($_.ProcessId -eq $PID) {
            return $false
        }

        $name = [string] $_.Name
        $text = Get-ProcessText $_
        $isClientPath = $text.Contains($root) -or $text.Contains($minecraft)
        if (-not $isClientPath) {
            return $false
        }

        $name -match '^(java|javaw)\.exe$|launcher|minecraft|pcl|hmcl|java21'
    }
}

function Get-JarModId {
    param([string] $JarPath)

    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction SilentlyContinue
        $zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
        try {
            $entry = $zip.GetEntry('mcmod.info')
            if ($null -eq $entry) {
                return $null
            }

            $reader = [System.IO.StreamReader]::new($entry.Open())
            try {
                $json = $reader.ReadToEnd()
            } finally {
                $reader.Dispose()
            }

            $info = $json | ConvertFrom-Json
            if ($info -is [array]) {
                return [string] $info[0].modid
            }
            return [string] $info.modid
        } finally {
            $zip.Dispose()
        }
    } catch {
        Write-InstallLog ("Could not read mod id from {0}: {1}" -f $JarPath, $_.Exception.Message)
        return $null
    }
}

function Install-ModJar {
    if (-not (Test-Path -LiteralPath $SourceJar)) {
        throw "Source jar does not exist: $SourceJar"
    }
    if (-not (Test-Path -LiteralPath $ModsDir)) {
        throw "Mods directory does not exist: $ModsDir"
    }

    $sourceModId = Get-JarModId $SourceJar
    if ($sourceModId -ne $ModId) {
        throw "Source jar modid was '$sourceModId', expected '$ModId'"
    }

    $destination = Join-Path $ModsDir (Split-Path -Leaf $SourceJar)
    Write-InstallLog "Installing $SourceJar to $destination"

    $oldJars = Get-ChildItem -LiteralPath $ModsDir -Filter '*.jar' -File | Where-Object {
        (Get-JarModId $_.FullName) -eq $ModId
    }

    foreach ($oldJar in $oldJars) {
        Write-InstallLog ("Deleting old {0}" -f $oldJar.FullName)
        Remove-Item -LiteralPath $oldJar.FullName -Force
    }

    Copy-Item -LiteralPath $SourceJar -Destination $destination -Force
    $installedModId = Get-JarModId $destination
    if ($installedModId -ne $ModId) {
        throw "Installed jar modid was '$installedModId', expected '$ModId'"
    }

    $installed = Get-Item -LiteralPath $destination
    Write-InstallLog ("Installed {0} ({1} bytes)" -f $installed.FullName, $installed.Length)
}

try {
    Write-InstallLog 'Started wait/install script.'
    $running = @(Get-ClientProcess)
    if ($running.Count -gt 0) {
        Write-InstallLog ("Client process detected; waiting: {0}" -f (($running | ForEach-Object { '{0}:{1}' -f $_.Name, $_.ProcessId }) -join ', '))
    }

    while (@(Get-ClientProcess).Count -gt 0) {
        Start-Sleep -Seconds 5
    }

    Write-InstallLog 'No matching client process is running; switching jar now.'
    Install-ModJar
    Write-InstallLog 'Finished successfully.'
} catch {
    Write-InstallLog ("Failed: {0}" -f $_.Exception.Message)
    throw
}
