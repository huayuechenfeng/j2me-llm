$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$projectRoot = Split-Path -Parent $PSScriptRoot
$nodeVersion = '24.14.0'
$packageVersion = '0.3.0'
$nodeArchiveName = 'node-v' + $nodeVersion + '-win-x64.zip'
$downloadRoot = Join-Path $projectRoot '.tools\downloads'
$nodeArchive = Join-Path $downloadRoot $nodeArchiveName
$checksumFile = Join-Path $downloadRoot ('node-v' + $nodeVersion + '-SHASUMS256.txt')
$nodeRoot = Join-Path $projectRoot ('.tools\node-v' + $nodeVersion + '-win-x64')
$oneClickName = -join @([char]0x4e00, [char]0x952e, [char]0x7f51, [char]0x5173)
$sourceRoot = Join-Path $projectRoot $oneClickName
$launcher = Get-ChildItem -LiteralPath $sourceRoot -Filter '*.bat' | Select-Object -First 1
if (-not $launcher) { throw 'Portable gateway launcher is missing.' }
$buildRoot = Join-Path $projectRoot 'build\gateway-package'
$packageName = 'J2ME-LLM-Gateway-v' + $packageVersion + '-windows-x64'
$packageRoot = Join-Path $buildRoot $packageName
$runtimeRoot = Join-Path $packageRoot 'runtime'
$outputZip = Join-Path $projectRoot ('dist\' + $packageName + '.zip')

foreach ($directory in @($downloadRoot, $buildRoot, (Split-Path -Parent $outputZip))) {
    if (-not (Test-Path -LiteralPath $directory)) {
        New-Item -ItemType Directory -Path $directory | Out-Null
    }
}

function Download-File([string]$Url, [string]$Destination) {
    if (Test-Path -LiteralPath $Destination) { return }
    Write-Output ('Downloading ' + [IO.Path]::GetFileName($Destination))
    Invoke-WebRequest -UseBasicParsing -Uri $Url -OutFile $Destination
}

Download-File ('https://nodejs.org/dist/v' + $nodeVersion + '/' + $nodeArchiveName) $nodeArchive
Download-File ('https://nodejs.org/dist/v' + $nodeVersion + '/SHASUMS256.txt') $checksumFile

$checksumLine = Get-Content -LiteralPath $checksumFile |
    Where-Object { $_ -match ([regex]::Escape($nodeArchiveName) + '$') } |
    Select-Object -First 1
if (-not $checksumLine) { throw 'Node.js checksum entry is missing.' }
$expected = ($checksumLine -split '\s+')[0].ToUpperInvariant()
$actual = (Get-FileHash -LiteralPath $nodeArchive -Algorithm SHA256).Hash
if ($actual -ne $expected) { throw 'Node.js archive checksum mismatch.' }

if (-not (Test-Path -LiteralPath (Join-Path $nodeRoot 'node.exe'))) {
    Expand-Archive -LiteralPath $nodeArchive -DestinationPath (Split-Path -Parent $nodeRoot) -Force
}

$resolvedBuild = [IO.Path]::GetFullPath($buildRoot)
$expectedBuild = [IO.Path]::GetFullPath((Join-Path $projectRoot 'build\gateway-package'))
if ($resolvedBuild -ne $expectedBuild) { throw 'Unexpected gateway build path.' }
if (Test-Path -LiteralPath $packageRoot) {
    Remove-Item -LiteralPath $packageRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $runtimeRoot | Out-Null

Copy-Item -LiteralPath $launcher.FullName -Destination $packageRoot
Copy-Item -LiteralPath (Join-Path $sourceRoot 'README.txt') -Destination $packageRoot
Copy-Item -LiteralPath (Join-Path $projectRoot 'gateway\server.js') -Destination $runtimeRoot
Copy-Item -LiteralPath (Join-Path $projectRoot 'gateway\self-test.js') -Destination $runtimeRoot
Copy-Item -LiteralPath (Join-Path $nodeRoot 'node.exe') -Destination $runtimeRoot
Copy-Item -LiteralPath (Join-Path $nodeRoot 'LICENSE') -Destination (Join-Path $runtimeRoot 'NODE-LICENSE.txt')

$config = @(
    '# J2ME LLM portable gateway configuration',
    '# Edit values after the equals sign. Do not add quotes. Keep this file private.',
    '# OpenAI: https://api.openai.com/v1/chat/completions and https://api.openai.com/v1/models',
    '# DeepSeek: https://api.deepseek.com/chat/completions and https://api.deepseek.com/models',
    '# Kimi: https://api.moonshot.cn/v1/chat/completions and https://api.moonshot.cn/v1/models',
    '',
    'HOST=0.0.0.0',
    'PORT=8787',
    'UPSTREAM_URL=https://api.openai.com/v1/chat/completions',
    'UPSTREAM_MODELS_URL=https://api.openai.com/v1/models',
    'UPSTREAM_API_KEY=PASTE_YOUR_REAL_API_KEY_HERE',
    'UPSTREAM_MODEL=',
    'DEVICE_TOKEN=AUTO',
    'LOG_ERRORS=0'
)
[IO.File]::WriteAllLines((Join-Path $packageRoot 'gateway.conf'), $config,
    (New-Object Text.UTF8Encoding($false)))

& (Join-Path $runtimeRoot 'node.exe') (Join-Path $runtimeRoot 'self-test.js')
if ($LASTEXITCODE -ne 0) { throw 'Packaged gateway self-test failed.' }

if (Test-Path -LiteralPath $outputZip) {
    Remove-Item -LiteralPath $outputZip -Force
}
Compress-Archive -LiteralPath $packageRoot -DestinationPath $outputZip -CompressionLevel Optimal

$required = @(
    ($packageName + '/' + $launcher.Name),
    ($packageName + '/gateway.conf'),
    ($packageName + '/README.txt'),
    ($packageName + '/runtime/node.exe'),
    ($packageName + '/runtime/server.js'),
    ($packageName + '/runtime/self-test.js'),
    ($packageName + '/runtime/NODE-LICENSE.txt')
)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($outputZip)
try {
    $names = @($archive.Entries | ForEach-Object { $_.FullName.Replace('\', '/') })
    foreach ($name in $required) {
        if ($names -notcontains $name) { throw ('Package entry is missing: ' + $name) }
    }
} finally {
    $archive.Dispose()
}

$file = Get-Item -LiteralPath $outputZip
Write-Output ('Gateway package: ' + $file.FullName)
Write-Output ('Bytes: ' + $file.Length)
Write-Output ('SHA256: ' + (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash)
