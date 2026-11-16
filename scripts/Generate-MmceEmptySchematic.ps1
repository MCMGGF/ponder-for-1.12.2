$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$resourceDirectory = Join-Path $projectRoot 'src\main\resources\assets\ponder_legacy\ponder\mmce'
[System.IO.Directory]::CreateDirectory($resourceDirectory) | Out-Null
$target = Join-Path $resourceDirectory 'empty.nbt'
# GZIP-compressed NBT root compound with no blocks, palette or live-world data.
$output = [System.IO.File]::Create($target)
try {
    $gzip = [System.IO.Compression.GZipStream]::new($output, [System.IO.Compression.CompressionLevel]::Optimal, $true)
    try {
        $rootCompound = [byte[]] @(10, 0, 0, 0)
        $gzip.Write($rootCompound, 0, $rootCompound.Length)
    }
    finally {
        $gzip.Dispose()
    }
}
finally {
    $output.Dispose()
}
Get-Item -LiteralPath $target | Select-Object FullName, Length
