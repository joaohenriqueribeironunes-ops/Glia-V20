# Glia — Assistente Pessoal de Voz 100% Offline (Android)

App nativo Android (Kotlin + C++/JNI) para conversas de voz fluidas e offline,
projetado para **Redmi Note 11S / Helio G96 / 6 GB RAM**, totalmente offline.

## Stack

- Kotlin (Android Nativo) + C++ (NDK/JNI) para áudio e inferência.
- LLM offline via `llama.cpp` (upstream, b11556+). **Modelo único:** `Ternary-Bonsai-2-4B-Q2_0.gguf`
  (PrismML, 1.212.103.520 bytes, Q2_0).
- STT offline via Vosk (`vosk-android 0.3.75`).
- TTS via `android.speech.tts.TextToSpeech` (PT-BR), streaming por frase.
- Wake word "Glia" via Vosk (loop contínuo) + ForegroundService + TileService (QS) + overlay (MIUI).

## Funcionalidades (V20.3)

1. **Ativação**: Quick Settings Tile "Glia" (desligado = nenhum microfone/serviço/modelo em
   background; ligado = foreground service com notificação + escuta local da palavra "Glia").
2. **Modelo 4B**: arquivo importável via SAF para `filesDir/user-model.gguf`; validação de
   magic GGUF e tamanho; mmap; KV `q8_0` com fallback F16; flash attention; contexto teto 4096;
   threads 2–8; headroom de memória ~1,25 GiB; mensagem clara se o arquivo estiver ausente.
3. **Tools (LLM + JSON estrito)**: parser de tool call validado antes de executar; router nativo;
   data/hora/dia da semana via `java.time` determinístico; lanterna, Bluetooth e Wi-Fi.
4. **Design**: linguagem visual inspirada em HyperOS 2 — roxo, luz suave, "ilha" de status,
   centro de controle em pastilhas, cartões translúcidos, claro/escuro, animações curtas.

## Compilar

```powershell
cd Glia
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
.\gradlew.bat :app:assembleRelease --offline --console=plain
```

APK release: `app/build/outputs/apk/release/app-release.apk` (**7.581.986 bytes / 7,2 MB medidos**).
O `.gguf` **não** entra no APK e não é versionado; precisa ser importado no app.

## Release

- Tag: `v20`
- Repo: `Glia-V20`
- Assinatura: keystore local (`release.keystore` + `release.keystore.properties`, fora do git).

## Modelo 4B

- Origem observada no Loci: `prism-ml/Ternary-Bonsai-2-4B-gguf`, arquivo
  `Ternary-Bonsai-2-4B-Q2_0.gguf`, commit `50ebba61be85e5be07e8083db468ace0fc1bac43`.
- SHA-256 verificado: `64a5183b44921bdb5ac4a6f5f92007821d6ebe5c66997d3c147fd214ab9433d4`.
- No device: o arquivo foi copiado para `filesDir/user-model.gguf` do Glia via build debug
  assinado com a mesma keystore + `run-as`, preservado ao reinstalar o release.
- Importação manual: Configurações → Importar `.gguf` → Salvar → Recarregar modelo.

## Otimizações de inferência (herdadas da análise Loci, implementação própria)

KV cache `q8_0` com fallback F16, flash attention, `n_threads`/`n_threads_batch`, mmap,
`nativeCountTokens`, estatísticas nativas de prefill/decode/TPS e headroom de ~1,25 GiB.
As estatísticas reais aparecem na tela principal (`prefill ... · decode ... · tok/s`).
Detalhes e limites em `docs/loci-analise.md`.

## Benchmarks / limitações

- Ver `docs/benchmarks.md`. Device conectado, mas medições de tokens/s/RAM ainda dependem de
  execução com o tile ligado e uma frase de teste.
- Vosk STT: modelo não embutido (instalar separadamente).
- BT/Wi-Fi: ligar/desligar depende das restrições do HyperOS; o app reporta falha honestamente.

## Permissões MIUI / HyperOS

- Microfone, FGS microphone/mediaPlayback, WAKE_LOCK, VIBRATE, BT/Wi-Fi, overlay, tile.
- Ignorar otimização de bateria recomendado.
