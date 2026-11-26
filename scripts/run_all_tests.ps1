$ErrorActionPreference = 'Stop'

# Full offline test suite for Glia V31 (deterministic commands, no LLM, neural voice).
# JVM tests are launched directly via JUnitCore with an explicit classpath:
# the Gradle Test Executor in this environment cannot see compiled test classes.
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $root

$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot'
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$gradle = Join-Path $root 'gradlew.bat'

Write-Host '== Compiling Kotlin sources and tests =='
& $gradle :app:compileDebugUnitTestKotlin --offline --console=plain
if ($LASTEXITCODE -ne 0) { throw "Kotlin test compilation failed: $LASTEXITCODE" }

$junit = Get-ChildItem 'C:\Users\conta\.gradle\caches\modules-2\files-2.1' -Recurse -Filter 'junit-4.13.2.jar' -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
$hamcrest = Get-ChildItem 'C:\Users\conta\.gradle\caches\modules-2\files-2.1' -Recurse -Filter 'hamcrest-core-1.3.jar' -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
$kotlinStdlib = Get-ChildItem 'C:\Users\conta\.gradle\caches\modules-2\files-2.1' -Recurse -Filter 'kotlin-stdlib-2.0.21.jar' -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
$androidJar = 'C:\Users\conta\AppData\Local\Android\Sdk\platforms\android-35\android.jar'

if (-not $junit -or -not $hamcrest -or -not $kotlinStdlib -or -not (Test-Path $androidJar)) {
    throw 'JUnit/Hamcrest/Kotlin/Android jars not found'
}

$entries = @(
    'app\build\tmp\kotlin-classes\debugUnitTest',
    'app\build\tmp\kotlin-classes\debug',
    'app\build\intermediates\runtime_app_classes_jar\debug\bundleDebugClassesToRuntimeJar\classes.jar',
    $junit,
    $hamcrest,
    $kotlinStdlib,
    $androidJar
)
$cp = ($entries -join ';')

$classes = @(
    'com.glia.assistant.commands.CommandParserTest',
    'com.glia.assistant.commands.CalendarSummaryTest',
    'com.glia.assistant.commands.CognitiveNotesStoreTest',
    'com.glia.assistant.commands.AppMatcherTest',
    'com.glia.assistant.tts.NeuralVoiceAssetTest',
    'com.glia.assistant.tts.NeuralVoiceStoreTest',
    'com.glia.assistant.stt.SttRetryPolicyTest',
    'com.glia.assistant.stt.ContinuousListenPolicyTest',
    'com.glia.assistant.stt.MicGainTest',
    'com.glia.assistant.stt.PcmConditionerTest',
    'com.glia.assistant.stt.CaptureWindowTest',
    'com.glia.assistant.stt.TranscriptDeduperTest',
    'com.glia.assistant.stt.RollingTailTest',
    'com.glia.assistant.stt.CapturePlannerTest',
    'com.glia.assistant.commands.SecondaryCommandTest',
    'com.glia.assistant.voice.VoicePrintMathTest',
    'com.glia.assistant.voice.VoiceCheckTest'
)

Write-Host '== Running JVM tests (JUnitCore) =='
& $java '-Dfile.encoding=UTF-8' -cp $cp org.junit.runner.JUnitCore $classes
if ($LASTEXITCODE -ne 0) { throw "JVM tests failed: $LASTEXITCODE" }

Write-Host 'ALL_TESTS_OK'
