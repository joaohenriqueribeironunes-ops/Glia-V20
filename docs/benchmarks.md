# Benchmarks e metodologia — Glia V21

## Estado da versão

| Medida | Valor |
|---|---|
| Versão | V21 (versionCode 21, versionName 2.1.0) |
| Device usado no desenvolvimento | Redmi Note 11S, `product:miel_in`, `model:2201117SI`, serial `9XCAROHMCY65I7MB` |
| APK release | **36.757.472 bytes (~35,1 MB)** — ARM64, R8, assinatura local |
| Build release | `:app:assembleRelease --offline` OK |
| Runtime | Comandos determinísticos em Kotlin; sem LLM, sem GGUF e sem JNI próprio |
| STT | Vosk PT-BR embutido: `vosk-model-small-pt-0.3.zip`, 32.453.112 bytes, SHA-256 `6e1ce909032e1afa7a88e68a3d628ecafff302bdf195befab308826c395e93b7` |
| Vosk descomprimido | 16 entradas, 53.552.717 bytes, 14 arquivos obrigatórios |
| TTS | Voz local pt-BR preferida pelo `VoiceRanking`, pitch 1,06, taxa 1,02 |
| Testes JVM | 49 testes, 0 falhas (JUnitCore) |
| Testes nativos | removidos junto com llama.cpp/GliaMemory |

## Comparação com V20.6

V20.6 tinha **39.772.730 bytes** e incluía o motor llama.cpp compilado para ARM64.
A V21 removeu `libglia_native.so`, o código C++/JNI, os assets GGUF e os controles de
inferência. Mesmo mantendo o Vosk embutido, o APK caiu para **36.757.472 bytes**.

## Custo de processamento

- O wake loop usa Vosk com wake lock parcial e processa áudio em buffers de 100 ms;
  o RMS para detecção de fala passou a ser calculado em Kotlin, sem JNI.
- Comandos não carregam modelo de linguagem: hora/data usam `java.time`, calendário
  faz uma consulta pontual, lanterna usa `CameraManager`, apps usam o launcher do sistema.
- O TTS usa o motor do aparelho, sem download e sem inferência neural dentro do app;
  a escolha da voz é feita uma vez por inicialização.

## Como medir no device

```powershell
adb shell dumpsys meminfo com.glia.assistant
adb logcat -s GliaVoice GliaTts AudioRecorder VoskStt
```

A UI mostra: status do assistente, status da voz, status do microfone e último erro.
Não há mais estatísticas de prefill/decode/tokens por segundo.

## Metodologia pendente / limitações

- Device desconectado na verificação final: latência de wake word, STT e TTS não foi
  medida nesta rodada.
- HyperOS bloqueia injeção de input por ADB (`INJECT_EVENTS`); toques e tela bloqueada
  precisam de validação manual.
- `testDebugUnitTest` do Gradle falha com `ClassNotFoundException` no Test Executor;
  a suíte oficial roda via `scripts/run_all_tests.ps1` (49/49 OK).
- A voz neural depende do motor TTS instalado; o app não instala nem baixa vozes.
