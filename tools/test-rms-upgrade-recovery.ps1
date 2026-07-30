param(
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$projectRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $projectRoot '.tools'
$downloadRoot = Join-Path $toolRoot 'downloads'
$ecj = Join-Path $downloadRoot 'ecj-4.6.1.jar'
$cldc = Join-Path $downloadRoot 'cldcapi11-2.0.4.jar'
$midp = Join-Path $downloadRoot 'midpapi20-2.0.4.jar'
$jsr75 = Join-Path $toolRoot 'microemu\microemu-jsr-75-2.0.4.jar'
$proguard = Join-Path $toolRoot 'proguard-7.9.1\lib\proguard.jar'
$testRoot = Join-Path $projectRoot 'build\rms-upgrade-recovery'
$fixtureClasses = Join-Path $testRoot 'classes'
$rawJar = Join-Path $testRoot 'upgrade-recovery-raw.jar'
$finalJar = Join-Path $testRoot 'upgrade-recovery.jar'
$manifest = Join-Path $testRoot 'manifest.mf'
$jadFile = Join-Path $testRoot 'upgrade-recovery.jad'
$emulatorHome = Join-Path $testRoot 'emulator-home'

if (-not $SkipBuild) {
    & (Join-Path $PSScriptRoot 'build.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'Main build failed.' }
}

$resolvedRoot = [System.IO.Path]::GetFullPath($projectRoot) + [System.IO.Path]::DirectorySeparatorChar
$resolvedTest = [System.IO.Path]::GetFullPath($testRoot)
if (-not $resolvedTest.StartsWith($resolvedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to clean outside workspace: $resolvedTest"
}
if (Test-Path -LiteralPath $testRoot) { Remove-Item -LiteralPath $testRoot -Recurse -Force }
New-Item -ItemType Directory -Path $fixtureClasses -Force | Out-Null
New-Item -ItemType Directory -Path $emulatorHome -Force | Out-Null

$manifestLines = @(
    'Manifest-Version: 1.0',
    'MIDlet-Name: J2ME LLM RMS Test',
    'MIDlet-Version: 0.4.1',
    'MIDlet-Vendor: Chihoko',
    'MIDlet-1: RMS Test,,com.chihoko.j2mellm.tests.UpgradeRecoveryMidlet',
    'MicroEdition-Configuration: CLDC-1.1',
    'MicroEdition-Profile: MIDP-2.0'
)
[System.IO.File]::WriteAllLines($manifest, $manifestLines,
        (New-Object System.Text.UTF8Encoding($false)))

$compileClassPath = (Join-Path $projectRoot 'build\classes') + [System.IO.Path]::PathSeparator + `
        $midp + [System.IO.Path]::PathSeparator + $jsr75
$fixture = Join-Path $PSScriptRoot 'fixtures\UpgradeRecoveryMidlet.java'
& java -jar $ecj -encoding UTF-8 -source 1.3 -target 1.1 -bootclasspath $cldc `
    -classpath $compileClassPath -d $fixtureClasses $fixture
if ($LASTEXITCODE -ne 0) { throw 'Upgrade fixture compilation failed.' }

& jar cfm $rawJar $manifest -C (Join-Path $projectRoot 'build\classes') . -C $fixtureClasses .
if ($LASTEXITCODE -ne 0) { throw 'Upgrade fixture packaging failed.' }

$proguardArguments = @(
    '-jar', $proguard,
    '-injars', $rawJar,
    '-outjars', $finalJar,
    '-libraryjars', $cldc,
    '-libraryjars', $midp,
    '-libraryjars', $jsr75,
    '-target', '1.1',
    '-microedition',
    '-dontshrink',
    '-dontoptimize',
    '-dontobfuscate',
    '-dontnote',
    '-dontwarn',
    '-keepattributes', 'Exceptions,InnerClasses',
    '-keep', 'public class com.chihoko.j2mellm.tests.UpgradeRecoveryMidlet extends javax.microedition.midlet.MIDlet { public protected *; }'
)
& java @proguardArguments
if ($LASTEXITCODE -ne 0) { throw 'Upgrade fixture preverification failed.' }

$jarSize = (Get-Item -LiteralPath $finalJar).Length
$jadLines = @(
    'MIDlet-Name: J2ME LLM RMS Test',
    'MIDlet-Version: 0.4.1',
    'MIDlet-Vendor: Chihoko',
    'MIDlet-1: RMS Test,,com.chihoko.j2mellm.tests.UpgradeRecoveryMidlet',
    'MicroEdition-Configuration: CLDC-1.1',
    'MicroEdition-Profile: MIDP-2.0',
    'MIDlet-Jar-URL: file:///Q:/build/rms-upgrade-recovery/upgrade-recovery.jar',
    "MIDlet-Jar-Size: $jarSize"
)
[System.IO.File]::WriteAllLines($jadFile, $jadLines,
        (New-Object System.Text.UTF8Encoding($false)))

$runtimeJars = @(Get-ChildItem -Path (Join-Path $toolRoot 'microemu\*.jar') |
        ForEach-Object { $_.FullName })
$runtimeClassPath = $runtimeJars -join [System.IO.Path]::PathSeparator
$emulatorDrive = 'Q:'
if (Test-Path -LiteralPath ($emulatorDrive + '\')) {
    throw "Temporary emulator drive is already in use: $emulatorDrive"
}
& subst $emulatorDrive $projectRoot
if ($LASTEXITCODE -ne 0) { throw 'Failed to create temporary emulator drive.' }
$jadUrl = 'file:///Q:/build/rms-upgrade-recovery/upgrade-recovery.jad'
$emulatorHomeForJava = 'Q:\build\rms-upgrade-recovery\emulator-home'
$startInfo = New-Object System.Diagnostics.ProcessStartInfo
$startInfo.FileName = 'java'
$startInfo.Arguments = '-Duser.home="' + $emulatorHomeForJava + '" -cp "' +
        $runtimeClassPath + '" org.microemu.app.Main "-Xautotest:' + $jadUrl + '" --quit'
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
$startInfo.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
try {
    $process = [System.Diagnostics.Process]::Start($startInfo)
    if (-not $process.WaitForExit(20000)) {
        $process.Kill()
        $process.WaitForExit()
    }
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
} finally {
    & subst $emulatorDrive /D
}
[System.IO.File]::WriteAllText((Join-Path $testRoot 'stdout.log'), $stdout)
[System.IO.File]::WriteAllText((Join-Path $testRoot 'stderr.log'), $stderr)
if ($stdout -notmatch 'UPGRADE_RECOVERY_MIDLET_PASSED') {
    throw "RMS upgrade/recovery fixture failed.`nSTDOUT:`n$stdout`nSTDERR:`n$stderr"
}
Write-Output 'MicroEmulator RMS upgrade/recovery test passed.'

