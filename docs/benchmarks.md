# Benchmarks e metodologia — Glia V33

## Estado da versão

| Medida | Valor |
|---|---|
| Versão | V33 (versionCode 33, versionName 2.12.0) |
| Device usado no desenvolvimento | Redmi Note 11S, `product:miel_in`, `model:2201117SI`, serial `9XCAROHMCY65I7MB` |
| APK release | **107.920.203 bytes (~102,9 MB)** — ARM64, R8, assinatura local |
| Build release | `:app:assembleRelease --offline` OK |
| Runtime | Comandos determinísticos em Kotlin; sem LLM, sem GGUF e sem JNI próprio |
| STT | `SpeechRecognizer` do sistema, pt-BR, alimentado por janelas contínuas de PCM amplificado via `EXTRA_AUDIO_SOURCE` (3 s, 500 ms de sobreposição, sem VAD); comando direto sem ativação; retry em `ERROR_CLIENT`/`ERROR_AUDIO`; 5/6 só repetem uma vez com PCM amplificado e nunca abrem o microfone ao vivo por silêncio; sem Vosk embutido |
| Impressão de voz | 3D-Speaker CAM++ (`3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx`, 28.281.165 bytes, 16 kHz), opcional, verificação local pela maior similaridade de cosseno entre amostras |
| Asset da impressão | `glia-voiceprint.zip`, 25.801.265 bytes, 1 entrada ONNX |
| Voz neural | VITS/Piper `pt_BR-dii-high.onnx` (63.511.201 bytes, feminina, padrão) e `pt_BR-cadu-medium.onnx` (18.343.859 bytes, int8), ambos 22.050 Hz |
| Runtime da voz | sherpa-onnx 1.12.40 AAR estático; `libsherpa-onnx-jni.so` ARM64 de 22.249.744 bytes |
| Assets da voz | `glia-neural-voice.zip`, 58.800.891 bytes / 64.254.245 descomprimidos; `glia-neural-voice-cadu.zip`, 13.840.894 bytes / 19.087.043 descomprimidos; 11 entradas cada |
| Testes JVM | 95 testes, 0 falhas (JUnitCore) |
| Testes nativos | removidos junto com llama.cpp/GliaMemory |

## Comparação com V21

A V21 tinha **36.757.472 bytes** e embutia o modelo Vosk PT-BR (32.453.112 bytes),
usando o TTS do sistema. A V22 trocou esse conjunto por:
- modelo neural feminino pt-BR de 58.800.891 bytes (compactado);
- runtime sherpa-onnx com biblioteca nativa de 22.249.744 bytes.

A V25 passou para **68.259.040 bytes** com o ganho do microfone e a captura PCM
própria. A V26 sobe para **94.068.464 bytes** por incluir o modelo CAM++ de impressão
de voz (25.801.265 bytes no zip). A V27 ficou em **94.071.844 bytes** após as correções
de captura e retry. A V28 ficou em **94.073.432 bytes** com condicionamento de PCM e
testes adicionais. A V29 ficou em **94.074.272 bytes** com o detector de wake word exato
“Glia”. A V30 ficou em **94.072.248 bytes** com o comando direto sem ativação, mantendo a
suíte em 84 testes. A V31 fica em **94.071.620 bytes** com captura contínua em janelas
sobrepostas, modo único roxo e mais 4 testes puros (Janela, dedupe, buffer circular e
casos de calibração removidos), ainda somente `arm64-v8a`. A V32 adiciona o segundo modelo
neural Cadu int8 (13.840.894 bytes no zip) com seletor em Configurações e troca em runtime;
o APK sobe para **107.918.967 bytes** e a suíte para **93 testes** em 18 classes. A V33
corrige o ciclo de vida do microfone: o serviço só abre o `AudioRecord` depois de entrar
em foreground, não repete `startForeground`, mantém o capturador aberto durante respostas
e testes e elimina o fallback ao microfone vivo em janelas de silêncio (5/6). O APK fica
em **107.920.203 bytes** e a suíte em **95 testes** em 18 classes. É o custo aceito para
ter duas vozes neurais pt-BR locais e filtro de falante, todos offline.

## Custo de processamento

- O microfone fica contínuo enquanto o tile estiver ligado: um único `AudioRecord`
  VOICE_RECOGNITION é aberto na ativação e só é liberado no desligamento, com wake lock
  parcial. O áudio passa por NoiseSuppressor e ganho configurável (1x–4x, padrão 2x).
  O AGC nativo só é ligado em 1x; acima disso o sinal passa por filtro passa-alta
  (~77 Hz), ganho linear até 0,85 e limiter suave nos picos. Não há VAD: a captura
  gera janelas de 3 s com 500 ms de sobreposição e 100 ms de silêncio final; a
  deduplicação descarta transcrições repetidas pela sobreposição. Quando o fabricante
  devolve `ERROR_CLIENT` (2) ou `ERROR_AUDIO` (3), o app recria o reconhecedor e pode
  chegar à terceira tentativa com microfone ao vivo. `NO_MATCH` (5) e timeout (6) só
  repetem da tentativa 0 para a 1, ambas com PCM amplificado: uma janela silenciosa
  nunca faz o recognizer abrir o microfone do sistema. A sessão anterior é cancelada
  antes de cada escuta para não misturar callbacks atrasados. O `AudioRecord` fica
  aberto durante respostas e testes de voz; o áudio acumulado nesse intervalo é
  descartado com `flush()` antes de retomar o reconhecimento. A UI tem apenas um modo
  ativo (roxo); estados de pensamento/fala aparecem só no texto de status. Erros 5/6/8
  são normais e não poluem o último erro.
- Comandos não carregam modelo de linguagem: hora/data usam `java.time`, calendário
  faz uma consulta pontual, lanterna usa `CameraManager`, apps usam o launcher do sistema.
- A síntese usa um dos modelos VITS (Dii ou Cadu) com `numThreads = 1`, taxa 1,03 e
  reprodução em PCM float; não há download de voz nem inferência na nuvem. A troca de
  modelo recria o engine em uma thread dedicada e cancela a fala em andamento.
- Quando o filtro está ativo, cada captura gera um embedding com CAM++ em 16 kHz
  (`numThreads = 1`, `provider = cpu`) e compara com até 8 amostras do dono pela
  maior similaridade; uma amostra ruim do treino não derruba as demais. A extração
  falha é sinalizada uma vez e a fala é liberada para não deixar a assistente muda;
  somente uma rejeição efetiva da voz bloqueia o comando.
- Os modelos são extraídos e validados uma vez, com gravação atômica em `filesDir`.

## Como medir no device

```powershell
adb shell dumpsys meminfo com.glia.assistant
adb logcat -s GliaVoice GliaTts NeuralVoiceStore VoicePrintModel GliaApplication
```

A UI mostra: status do assistente, status da voz, status do microfone e último erro.
Não há estatísticas de prefill/decode/tokens por segundo.

## Metodologia pendente / limitações

- Device desconectado na verificação final: latência de STT em janela contínua, TTS,
  o efeito real do ganho e do filtro passa-alta e a taxa de aceitação/rejeição da
  impressão de voz não foram medidos.
- HyperOS bloqueia injeção de input por ADB (`INJECT_EVENTS`); toques e tela bloqueada
  precisam de validação manual.
- `testDebugUnitTest` do Gradle falha com `ClassNotFoundException` no Test Executor;
  a suíte oficial roda via `scripts/run_all_tests.ps1` (93/93 OK).
- O reconhecimento depende do motor de fala do sistema; o app solicita modo offline,
  mas o fabricante pode decidir usar rede.
