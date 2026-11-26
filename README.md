# Glia — Assistente Pessoal de Voz 100% Offline (Android)

App nativo Android (Kotlin + C++/JNI) para conversas de voz fluidas e offline,
projetado para hardware **MediaTek Helio G96 / 6GB RAM**, totalmente offline.

## Stack

- Kotlin (Android Nativo) + C++ (NDK/JNI) para processamento de audio e inferência.
- LLM offline via `llama.cpp` (upstream, release b11556+). **Padrão embutido:**
  `Ternary-Bonsai-2-2B-Q2_0.gguf` (~521 MiB). **Alternativa importável:**
  `Ternary-Bonsai-2-4B-Q2_0.gguf` (PrismML, ~1.21 GB, Q2_0).
- STT offline via Vosk (`vosk-android 0.3.75`).
- TTS via `android.speech.tts.TextToSpeech` (PT-BR), streaming por frase.
- Wake word "Glia" via Vosk (loop contínuo) + ForegroundService + TileService (QS) + overlay transparente (MIUI).

## Funcionalidades (V20)

1. **Ativação**: Quick Settings Tile "Glia" (desligado = nenhum microfone/serviço/modelo em background;
   ligado = foreground service com notificação + escuta local da palavra "Glia") + palavra-chave.
2. **Modelo (duplo)**: 2B embutido no APK como padrão (extração atômica para `filesDir`, validação
   tamanho + magic GGUF); 4B (ou outro GGUF) importável via SAF como alternativa; seleção em
   Configurações; mmap; KV-cache enxugado; contexto teto 4096; threads 2–8; teto de memória ~2.5 GB;
   check de memória antes do load; mensagem clara se o arquivo estiver ausente.
3. **Tools (LLM + JSON estrito)**: parser de tool call (JSON) validado antes de executar;
   router de tools nativas; data/hora/dia da semana via `java.time` determinístico; lanterna,
   Bluetooth, Wi-Fi (com stubs de status).
4. **Design**: linguagem visual inspirada em HyperOS 2 — tema roxo com luz suave, "ilha" de status,
   centro de controle em pastilhas, cartões translúcidos com borda luminosa, claro/escuro, animações curtas.

## Compilar

```bash
cd Glia
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
$env:ANDROID_HOME="C:\Users\<user>\AppData\Local\Android\Sdk"
.\\gradlew.bat :app:assembleRelease   # release assinada com keystore local
```

APK release: `app/build/outputs/apk/release/app-release.apk` (**528,6 MB medidos** com o modelo 2B embutido;
o arquivo `.gguf` não é comprimido no APK). O modelo **não** é enviado ao repositório (`.gitignore`),
mas é incluído no APK de release.

## Release

- Tag: `v20`
- Repo: `Glia-V20`
- Assinatura: keystore local (`release.keystore` + `release.keystore.properties`, fora do git).

## Modelo

- **Padrão**: `app/src/main/assets/models/Ternary-Bonsai-2-2B-Q2_0.gguf` (546.689.504 bytes,
  não versionado; precisa existir localmente no build). O app extrai para `filesDir/embedded-2b.gguf`.
- **Alternativa**: Configurações → "4B importado" → Importar `.gguf` → copia para
  `filesDir/user-model.gguf`. Se o 4B estiver ausente, o app avisa e usa o 2B embutido.
- Q2_0 roda no upstream llama.cpp b11556+; sem links de download versionados.

## Benchmarks / limitações

- Ver `docs/benchmarks.md` e `docs/loci-analise.md`.
- Sem device no ambiente: abertura/modelo/tokens-s/RAM exigem medição em Redmi Note 11S.
- Vosk STT: modelo não embutido (instalar separadamente).
- Bluetooth/Wi-Fi: status respeita `BLUETOOTH_CONNECT`; ligar/desligar depende das restrições do
  HyperOS (Android 10+ pode negar a mudança de Wi-Fi; o app reporta falha honestamente).

## Permissões MIUI / HyperOS

- Microfone, FGS microphone/mediaPlayback, WAKE_LOCK, VIBRATE, BT/Wi-Fi, overlay, tile.
- Ignorar otimização de bateria recomendado.
