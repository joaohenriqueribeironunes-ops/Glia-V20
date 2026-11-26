# Benchmarks e metodologia — Glia V22

## Estado da versão

| Medida | Valor |
|---|---|
| Versão | V22 (versionCode 22, versionName 2.2.0) |
| Device usado no desenvolvimento | Redmi Note 11S, `product:miel_in`, `model:2201117SI`, serial `9XCAROHMCY65I7MB` |
| APK release | **68.237.708 bytes (~65,1 MB)** — ARM64, R8, assinatura local |
| Build release | `:app:assembleRelease --offline` OK |
| Runtime | Comandos determinísticos em Kotlin; sem LLM, sem GGUF e sem JNI próprio |
| STT | `SpeechRecognizer` do sistema, pt-BR, `EXTRA_PREFER_OFFLINE`; sem Vosk embutido |
| Voz neural | VITS/Piper `pt_BR-dii-high.onnx` (63.511.201 bytes), feminina, 22.050 Hz |
| Runtime da voz | sherpa-onnx 1.12.40 AAR estático; `libsherpa-onnx-jni.so` ARM64 de 22.249.744 bytes |
| Asset da voz | `glia-neural-voice.zip`, 58.800.891 bytes, 64.254.245 descomprimidos, 11 entradas |
| Testes JVM | 42 testes, 0 falhas (JUnitCore) |
| Testes nativos | removidos junto com llama.cpp/GliaMemory |

## Comparação com V21

A V21 tinha **36.757.472 bytes** e embutia o modelo Vosk PT-BR (32.453.112 bytes),
usando o TTS do sistema. A V22 troca esse conjunto por:
- modelo neural feminino pt-BR de 58.800.891 bytes (compactado);
- runtime sherpa-onnx com biblioteca nativa de 22.249.744 bytes.

O APK passou para **68.237.708 bytes**, ainda somente `arm64-v8a`. É o custo aceito
para ter uma voz neural feminina dedicada e offline, em vez da voz genérica do sistema.

## Custo de processamento

- O wake loop usa `SpeechRecognizer` one-shot com wake lock parcial; cada escuta tem
  timeout e o sistema é reiniciado automaticamente pelo app.
- Comandos não carregam modelo de linguagem: hora/data usam `java.time`, calendário
  faz uma consulta pontual, lanterna usa `CameraManager`, apps usam o launcher do sistema.
- A síntese usa o modelo VITS com `numThreads = 1`, taxa 1,03 e reprodução em PCM float;
  não há download de voz nem inferência na nuvem.
- O modelo é extraído e validado uma vez, com gravação atômica em `filesDir`.

## Como medir no device

```powershell
adb shell dumpsys meminfo com.glia.assistant
adb logcat -s GliaVoice GliaTts NeuralVoiceStore GliaApplication
```

A UI mostra: status do assistente, status da voz, status do microfone e último erro.
Não há estatísticas de prefill/decode/tokens por segundo.

## Metodologia pendente / limitações

- Device desconectado na verificação final: latência de wake word, STT e TTS não foi
  medida nesta rodada.
- HyperOS bloqueia injeção de input por ADB (`INJECT_EVENTS`); toques e tela bloqueada
  precisam de validação manual.
- `testDebugUnitTest` do Gradle falha com `ClassNotFoundException` no Test Executor;
  a suíte oficial roda via `scripts/run_all_tests.ps1` (42/42 OK).
- O reconhecimento depende do motor de fala do sistema; o app solicita modo offline,
  mas o fabricante pode decidir usar rede.
