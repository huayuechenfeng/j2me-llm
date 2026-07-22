
param(
    [switch]$SkipTests
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
$buildRoot = Join-Path $projectRoot 'build'
$classRoot = Join-Path $buildRoot 'classes'
$testRoot = Join-Path $buildRoot 'tests'
$rawRoot = Join-Path $buildRoot 'raw'
$distRoot = Join-Path $projectRoot 'dist'
$rawJar = Join-Path $rawRoot 'J2ME-LLM-raw.jar'
$finalJar = Join-Path $distRoot 'J2ME-LLM.jar'
$finalJad = Join-Path $distRoot 'J2ME-LLM.jad'
$manifest = Join-Path $projectRoot 'config\manifest.mf'

foreach ($required in @($ecj, $cldc, $midp, $jsr75, $proguard, $manifest)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Missing build dependency: $required`nRun tools\bootstrap.ps1 first."
    }
}

foreach ($target in @($classRoot, $testRoot, $rawRoot, $distRoot)) {
    $fullTarget = [System.IO.Path]::GetFullPath($target)
    $fullRoot = [System.IO.Path]::GetFullPath($projectRoot) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $fullTarget.StartsWith($fullRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clean a path outside the workspace: $fullTarget"
    }
    if (Test-Path -LiteralPath $fullTarget) { Remove-Item -LiteralPath $fullTarget -Recurse -Force }
    New-Item -ItemType Directory -Path $fullTarget | Out-Null
}

Push-Location $projectRoot
try {
    if (-not $SkipTests) {
        $testSources = @(
            'src\com\chihoko\j2mellm\util\Json.java',
            'src\com\chihoko\j2mellm\util\Utf8.java',
            'src\com\chihoko\j2mellm\util\Base64.java',
            'src\com\chihoko\j2mellm\util\Crc32.java',
            'src\com\chihoko\j2mellm\util\ImageReferenceParser.java',
            'src\com\chihoko\j2mellm\util\ImageDimensions.java',
            'src\com\chihoko\j2mellm\util\JsonStreamWriter.java',
            'src\com\chihoko\j2mellm\model\ImageAttachment.java',
            'src\com\chihoko\j2mellm\model\MessageMedia.java',
            'src\com\chihoko\j2mellm\model\ChatMessage.java',
            'src\com\chihoko\j2mellm\model\ProviderProfile.java',
            'src\com\chihoko\j2mellm\model\ProviderPresets.java',
            'src\com\chihoko\j2mellm\model\ProfileState.java',
            'src\com\chihoko\j2mellm\net\ChatListener.java',
            'src\com\chihoko\j2mellm\net\ThinkingFilter.java',
            'src\com\chihoko\j2mellm\net\ThinkingRequestPolicy.java',
            'src\com\chihoko\j2mellm\net\ModelCatalogParser.java',
            'src\com\chihoko\j2mellm\net\ChatRequestWriter.java',
            'src\com\chihoko\j2mellm\store\ProfileCodec.java',
            'src\com\chihoko\j2mellm\store\ConversationRecordValidator.java',
            'src\com\chihoko\j2mellm\store\LegacyConfigCodec.java',
            'src\com\chihoko\j2mellm\provision\ProvisioningProfile.java',
            'src\com\chihoko\j2mellm\provision\ProvisioningPackage.java',
            'src\com\chihoko\j2mellm\provision\ProvisioningCodec.java',
            'src\com\chihoko\j2mellm\provision\ProvisioningMapper.java'
        ) + @(Get-ChildItem -Path 'tests' -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
        & javac -encoding UTF-8 -d $testRoot @testSources
        if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
        $runtimeClassPath = $testRoot
        $testClasses = @(
            'com.chihoko.j2mellm.util.JsonSelfTest',
            'com.chihoko.j2mellm.net.ThinkingFilterSelfTest',
            'com.chihoko.j2mellm.util.MediaSelfTest',
            'com.chihoko.j2mellm.util.ImageDimensionsSelfTest',
            'com.chihoko.j2mellm.util.JsonStreamWriterSelfTest',
            'com.chihoko.j2mellm.net.ModelCatalogParserSelfTest',
            'com.chihoko.j2mellm.net.ChatRequestWriterSelfTest',
            'com.chihoko.j2mellm.store.ProfileCodecSelfTest',
            'com.chihoko.j2mellm.store.ConversationRecordValidatorSelfTest',
            'com.chihoko.j2mellm.provision.ProvisioningCodecSelfTest',
            'com.chihoko.j2mellm.provision.ProvisioningMapperSelfTest'
        )
        foreach ($testClass in $testClasses) {
            & java -cp $runtimeClassPath $testClass
            if ($LASTEXITCODE -ne 0) { throw "$testClass failed." }
        }
    }

    $sources = @(Get-ChildItem -Path 'src' -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
    $compileClassPath = $midp + [System.IO.Path]::PathSeparator + $jsr75
    & java -jar $ecj -encoding UTF-8 -source 1.3 -target 1.1 -bootclasspath $cldc `
        -classpath $compileClassPath -d $classRoot @sources
    if ($LASTEXITCODE -ne 0) { throw 'MIDP source compilation failed.' }

    & jar cfm $rawJar $manifest -C $classRoot .
    if ($LASTEXITCODE -ne 0) { throw 'Raw JAR packaging failed.' }

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
        '-keep', 'public class com.chihoko.j2mellm.LlmMidlet extends javax.microedition.midlet.MIDlet { public protected *; }'
    )
    & java @proguardArguments
    if ($LASTEXITCODE -ne 0) { throw 'Java ME preverification failed.' }

    $jarSize = (Get-Item -LiteralPath $finalJar).Length
    $jadLines = @(
        'MIDlet-Name: J2ME LLM',
        'MIDlet-Version: 0.2.0',
        'MIDlet-Vendor: Chihoko',
        'MIDlet-1: J2ME LLM,,com.chihoko.j2mellm.LlmMidlet',
        'MicroEdition-Configuration: CLDC-1.1',
        'MicroEdition-Profile: MIDP-2.0',
        'MIDlet-Permissions: javax.microedition.io.Connector.http, javax.microedition.io.Connector.https',
        'MIDlet-Permissions-Opt: javax.microedition.io.Connector.file.read, javax.microedition.io.Connector.file.write',
        'MIDlet-Jar-URL: J2ME-LLM.jar',
        "MIDlet-Jar-Size: $jarSize"
    )
    [System.IO.File]::WriteAllLines($finalJad, $jadLines, (New-Object System.Text.UTF8Encoding($false)))

    $entries = & jar tf $finalJar
    if ($LASTEXITCODE -ne 0 -or -not ($entries -contains 'com/chihoko/j2mellm/LlmMidlet.class')) {
        throw 'The final JAR is missing the MIDlet main class.'
    }
    Write-Output "Build complete: $finalJar ($jarSize bytes)"
    Write-Output "Descriptor: $finalJad"
} finally {
    Pop-Location
}






