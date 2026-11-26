# Benchmarks e metodologia — Glia V26

## Estado da versão

| Medida | Valor |
|---|---|
| Versão | V26 (versionCode 26, versionName 2.5.0) |
| Device usado no desenvolvimento | Redmi Note 11S, `product:miel_in`, `model:2201117SI`, serial `9XCAROHMCY65I7MB` |
| APK release | **94.068.464 bytes (~89,7 MB)** — ARM64, R8, assinatura local |
| Build release | `:app:assembleRelease --offline` OK |
| Runtime | Comandos determinísticos em Kotlin; sem LLM, sem GGUF e sem JNI próprio |
| STT | `SpeechRecognizer` do sistema, pt-BR, alimentado por PCM amplificado via `EXTRA_AUDIO_SOURCE`; retry em `ERROR_CLIENT`/`ERROR_AUDIO` e fallback para microfone padrão; sem Vosk embutido |
| Impressão de voz | 3D-Speaker CAM++ (`3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx`, 28.281.165 bytes, 16 kHz), opcional, verificação local por cosseno |
| Asset da impressão | `glia-voiceprint.zip`, 25.801.265 bytes, 1 entrada ONNX |
| Voz neural | VITS/Piper `pt_BR-dii-high.onnx` (63.511.201 bytes), feminina, 22.050 Hz |
| Runtime da voz | sherpa-onnx 1.12.40 AAR estático; `libsherpa-onnx-jni.so` ARM64 de 22.249.744 bytes |
| Asset da voz | `glia-neural-voice.zip`, 58.800.891 bytes, 64.254.245 descomprimidos, 11 entradas |
| Testes JVM | 58 testes, 0 falhas (JUnitCore) |
| Testes nativos | removidos junto com llama.cpp/GliaMemory |

## Comparação com V21

A V21 tinha **36.757.472 bytes** e embutia o modelo Vosk PT-BR (32.453.112 bytes),
usando o TTS do sistema. A V22 trocou esse conjunto por:
- modelo neural feminino pt-BR de 58.800.891 bytes (compactado);
- runtime sherpa-onnx com biblioteca nativa de 22.249.744 bytes.

A V25 passou para **68.259.040 bytes** com o ganho do microfone e a captura PCM
própria. A V26 sobe para **94.068.464 bytes** por incluir o modelo CAM++ de impressão
de voz (25.801.265 bytes no zip), ainda somente `arm64-v8a`. É o custo aceito para
ter voz neural feminina dedicada e filtro de falante, ambos offline.

## Custo de processamento

- O microfone fica contínuo enquanto o tile estiver ligado: cada escuta tem timeout de
  30 s e reinicia imediatamente, sem esperar nova palavra-chave, com wake lock parcial.
  O áudio é capturado em `AudioRecord` VOICE_RECOGNITION com AGC/NoiseSuppressor e
  ganho configurável (1x–4x, padrão 2x), com limiter `tanh` e VAD antes de seguir para
  o reconhecedor. Quando o fabricante devolve `ERROR_CLIENT` (2) ou `ERROR_AUDIO` (3),
  o app recria o reconhecedor; a segunda tentativa mantém o áudio amplificado sem
  exigir modo offline e a terceira usa o microfone padrão. Erros 5/6/8 são normais e
  não poluem o último erro.
- Comandos não carregam modelo de linguagem: hora/data usam `java.time`, calendário
  faz uma consulta pontual, lanterna usa `CameraManager`, apps usam o launcher do sistema.
- A síntese usa o modelo VITS com `numThreads = 1`, taxa 1,03 e reprodução em PCM float;
  não há download de voz nem inferência na nuvem.
- Quando o filtro está ativo, cada captura gera um embedding com CAM++ em 16 kHz
  (`numThreads = 1`, `provider = cpu`) e compara com até 8 amostras do dono; a
  extração falha fecha a porta em vez de liberar a fala.
- Os dois modelos são extraídos e validados uma vez, com gravação atômica em `filesDir`.

## Como medir no device

```powershell
adb shell dumpsys meminfo com.glia.assistant
adb logcat -s GliaVoice GliaTts NeuralVoiceStore VoicePrintModel GliaApplication
```

A UI mostra: status do assistente, status da voz, status do microfone e último erro.
Não há estatísticas de prefill/decode/tokens por segundo.

## Metodologia pendente / limitações

- Device desconectado na verificação final: latência de wake word, STT, TTS, o efeito
  real do ganho e a taxa de aceitação/rejeição da impressão de voz não foram medidos.
- HyperOS bloqueia injeção de input por ADB (`INJECT_EVENTS`); toques e tela bloqueada
  precisam de validação manual.
- `testDebugUnitTest` do Gradle falha com `ClassNotFoundException` no Test Executor;
  a suíte oficial roda via `scripts/run_all_tests.ps1` (58/58 OK).
- O reconhecimento depende do motor de fala do sistema; o app solicita modo offline,
  mas o fabricante pode decidir usar rede.
