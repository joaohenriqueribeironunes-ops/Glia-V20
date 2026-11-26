# Glia — Assistente Pessoal de Voz 100% Offline (Android)

App nativo Android (Kotlin + C++/JNI) para conversas de voz fluidas e offline,
projetado para hardware **MediaTek Helio G96 / 6GB RAM**, totalmente offline.

## Stack

- Kotlin (Android Nativo) + C++ (NDK/JNI) para processamento de audio e inferência.
- LLM offline via `llama.cpp` (upstream, release b11556+), modelo recomendado:
  **Ternary Bonsai 2 4B** da PrismML (`Ternary-Bonsai-2-4B-Q2_0.gguf`, ~1.21 GB, Q2_0).
- STT offline via Vosk (`vosk-android 0.3.75`).
- TTS via `android.speech.tts.TextToSpeech` (PT-BR), streaming por frase.
- Wake word "Glia" via Vosk (loop contínuo) + ForegroundService + TileService (QS) + overlay transparente (MIUI).

## Funcionalidades (V20)

1. **Ativação**: Quick Settings Tile "Glia" (desligado = nenhum microfone/serviço/modelo em background;
   ligado = foreground service com notificação + escuta local da palavra "Glia") + palavra-chave.
2. **Modelo**: 4B importável/configurável pelo usuário via SAF (.gguf); sem asset embutido;
   validação de magic GGUF + tamanho; mmap; KV-cache enxugado; contexto teto 4096; threads 2–8;
   teto de memória ~2.5 GB; check de memória antes do load.
3. **Tools (LLM + JSON estrito)**: parser de tool call (JSON) validado antes de executar;
   router de tools nativas; data/hora/dia da semana via `java.time` determinístico; lanterna,
   Bluetooth, Wi-Fi (com stubs de status).
4. **Design**: identidade roxa, cartões translúcidos, cantos arredondados, tema nativo.

## Compilar

```bash
cd Glia
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
$env:ANDROID_HOME="C:\Users\<user>\AppData\Local\Android\Sdk"
.\\gradlew.bat :app:assembleRelease   # release assinada com keystore local
```

APK release: `app/build/outputs/apk/release/app-release.apk` (~7.2 MB).

## Release

- Tag: `v20`
- Repo: `Glia-V20`
- Assinatura: keystore local (`release.keystore` + `release.keystore.properties`, fora do git).

## Modelo

- Fornecer o arquivo `Ternary-Bonsai-2-4B-Q2_0.gguf` (PrismML, Apache 2.0; Q2_0 roda no
  upstream llama.cpp b11556+). Não há link de download versionado.
- No app: Configurações → Importar modelo .gguf → copia para `filesDir/user-model.gguf`.

## Benchmarks / limitações

- Ver `docs/benchmarks.md` e `docs/loci-analise.md`.
- Sem device no ambiente: abertura/modelo/tokens-s/RAM exigem medição em Redmi Note 11S.
- Vosk STT: modelo não embutido (instalar separadamente).
- Bluetooth/Wi-Fi: status respeita `BLUETOOTH_CONNECT`; ligar/desligar depende das restrições do
  HyperOS (Android 10+ pode negar a mudança de Wi-Fi; o app reporta falha honestamente).

## Permissões MIUI / HyperOS

- Microfone, FGS microphone/mediaPlayback, WAKE_LOCK, VIBRATE, BT/Wi-Fi, overlay, tile.
- Ignorar otimização de bateria recomendado.
