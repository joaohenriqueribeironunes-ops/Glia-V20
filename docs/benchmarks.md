# Benchmarks e metodologia — Glia V28

## Estado da versão

| Medida | Valor |
|---|---|
| Versão | V28 (versionCode 28, versionName 2.7.0) |
| Device usado no desenvolvimento | Redmi Note 11S, `product:miel_in`, `model:2201117SI`, serial `9XCAROHMCY65I7MB` |
| APK release | **94.073.432 bytes (~89,7 MB)** — ARM64, R8, assinatura local |
| Build release | `:app:assembleRelease --offline` OK |
| Runtime | Comandos determinísticos em Kotlin; sem LLM, sem GGUF e sem JNI próprio |
| STT | `SpeechRecognizer` do sistema, pt-BR, alimentado por PCM amplificado via `EXTRA_AUDIO_SOURCE`; retry em `ERROR_CLIENT`/`ERROR_AUDIO`/`NO_MATCH`/timeout e fallback para microfone ao vivo; sem Vosk embutido |
| Impressão de voz | 3D-Speaker CAM++ (`3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx`, 28.281.165 bytes, 16 kHz), opcional, verificação local pela maior similaridade de cosseno entre amostras |
| Asset da impressão | `glia-voiceprint.zip`, 25.801.265 bytes, 1 entrada ONNX |
| Voz neural | VITS/Piper `pt_BR-dii-high.onnx` (63.511.201 bytes), feminina, 22.050 Hz |
| Runtime da voz | sherpa-onnx 1.12.40 AAR estático; `libsherpa-onnx-jni.so` ARM64 de 22.249.744 bytes |
| Asset da voz | `glia-neural-voice.zip`, 58.800.891 bytes, 64.254.245 descomprimidos, 11 entradas |
| Testes JVM | 78 testes, 0 falhas (JUnitCore) |
| Testes nativos | removidos junto com llama.cpp/GliaMemory |

## Comparação com V21

A V21 tinha **36.757.472 bytes** e embutia o modelo Vosk PT-BR (32.453.112 bytes),
usando o TTS do sistema. A V22 trocou esse conjunto por:
- modelo neural feminino pt-BR de 58.800.891 bytes (compactado);
- runtime sherpa-onnx com biblioteca nativa de 22.249.744 bytes.

A V25 passou para **68.259.040 bytes** com o ganho do microfone e a captura PCM
própria. A V26 sobe para **94.068.464 bytes** por incluir o modelo CAM++ de impressão
de voz (25.801.265 bytes no zip). A V27 ficou em **94.071.844 bytes** após as correções
de captura e retry. A V28 fica em **94.073.432 bytes** com condicionamento de PCM e
testes adicionais, ainda somente `arm64-v8a`. É o custo aceito para ter voz neural
feminina dedicada e filtro de falante, ambos offline.

## Custo de processamento

- O microfone fica contínuo enquanto o tile estiver ligado: cada escuta tem timeout de
  30 s e reinicia imediatamente, sem esperar nova palavra-chave, com wake lock parcial.
  O áudio é capturado em `AudioRecord` VOICE_RECOGNITION com NoiseSuppressor e ganho
  configurável (1x–4x, padrão 2x). O AGC nativo só é ligado em 1x; acima disso o sinal
  passa por filtro passa-alta (~77 Hz), ganho linear até 0,85 e limiter suave nos picos,
  seguido de `SpeechGate` com 100 ms de calibração e limiar adaptativo. Quando o
  fabricante devolve `ERROR_CLIENT` (2) ou `ERROR_AUDIO` (3), o app recria o reconhecedor; `NO_MATCH` (5)
  e timeout (6) também acionam retry quando a origem é amplificada, porque o fabricante
  pode ter ignorado o PCM. A segunda tentativa mantém o áudio amplificado sem exigir
  modo offline e a terceira usa o microfone ao vivo. A captura termina com 100 ms de
  silêncio para o reconhecedor fechar a hipótese. A sessão anterior é cancelada antes
  de cada escuta para não misturar callbacks atrasados. Erros 5/6/8 são normais e não
  poluem o último erro.
- Comandos não carregam modelo de linguagem: hora/data usam `java.time`, calendário
  faz uma consulta pontual, lanterna usa `CameraManager`, apps usam o launcher do sistema.
- A síntese usa o modelo VITS com `numThreads = 1`, taxa 1,03 e reprodução em PCM float;
  não há download de voz nem inferência na nuvem.
- Quando o filtro está ativo, cada captura gera um embedding com CAM++ em 16 kHz
  (`numThreads = 1`, `provider = cpu`) e compara com até 8 amostras do dono pela
  maior similaridade; uma amostra ruim do treino não derruba as demais. A extração
  falha é sinalizada uma vez e a fala é liberada para não deixar a assistente muda;
  somente uma rejeição efetiva da voz bloqueia o comando.
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
  real do ganho e do filtro passa-alta e a taxa de aceitação/rejeição da impressão de
  voz não foram medidos.
- HyperOS bloqueia injeção de input por ADB (`INJECT_EVENTS`); toques e tela bloqueada
  precisam de validação manual.
- `testDebugUnitTest` do Gradle falha com `ClassNotFoundException` no Test Executor;
  a suíte oficial roda via `scripts/run_all_tests.ps1` (78/78 OK).
- O reconhecimento depende do motor de fala do sistema; o app solicita modo offline,
  mas o fabricante pode decidir usar rede.
