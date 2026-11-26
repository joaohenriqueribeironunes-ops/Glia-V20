# Glia — Assistente Pessoal de Voz 100% Offline (Android)

App nativo Android (Kotlin + C++/JNI) para conversas de voz fluidas e offline,
projetado para hardware **MediaTek Helio G96 / 6GB RAM**, totalmente offline.

## Stack

- Kotlin (Android Nativo) + C++ (NDK/JNI) para processamento de audio e inferência.
- LLM offline via `llama.cpp` (upstream, b11556+), modelo único:
  **Ternary Bonsai 2 4B** (`Ternary-Bonsai-2-4B-Q2_0.gguf`, 1.212.103.520 bytes, Q2_0).
- STT offline via Vosk (`vosk-android 0.3.75`), com modelo PT-BR **embutido no APK**
  (`vosk-model-small-pt-0.3.zip`).
- TTS via `android.speech.tts.TextToSpeech` (PT-BR), streaming por frase.
- Wake word "Glia" via Vosk + ForegroundService + TileService (QS) + overlay transparente (MIUI).

## Funcionalidades (V20.6)

1. **Ativação**: Quick Settings Tile "Glia" + palavra-chave "Glia"; tile desligado = nenhum mic/serviço/modelo em background.
2. **Modelo único 4B com descoberta automática**: o assistente procura o GGUF no caminho salvo, em `filesDir/user-model.gguf`, em `filesDir/Ternary-Bonsai-2-4B-Q2_0.gguf`, no diretório externo Download do app e em `/sdcard/Download` / `/sdcard/Documents`; valida magic GGUF + tamanho/sha256 e copia o modelo encontrado para o armazenamento privado; também mantém a importação via SAF. mmap; KV q8_0 com fallback F16; flash attention; `n_threads_batch`; contexto adaptativo 256–4096.
3. **Rodando abaixo do teto de memória**: `GliaMemory` lê `MemAvailable` de `/proc/meminfo`, reserva **1,25 GiB** para o sistema + **256 MiB** de runtime e calcula o orçamento restante para o modelo; o JNI calcula bytes/token com `n_layer`/`n_head_kv`/`n_embd`, reduz `n_ctx` automaticamente e falha com erro claro (`-4`) se nem o contexto mínimo couber. O app mostra no status o contexto real, o tipo de KV e o teto em MB.
4. **Vosk PT-BR embutido**: o zip do modelo é validado por SHA-256, estrutura e tamanho antes da extração atômica para `filesDir/vosk-model-small-pt-0.3`; o reconhecedor usa os 14 arquivos obrigatórios. Não é mais necessário copiar o modelo de voz manualmente.
5. **Tool calling**: JSON estrito validado antes de executar; hora/data/dia da semana via `java.time`; lanterna, Bluetooth e Wi-Fi.
6. **Design**: tema roxo HyperOS-like, claro/escuro, cartões translúcidos, pílula HyperIsland, tiles de centro de controle.

## Compilar e testar

```powershell
cd Glia
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
.\gradlew.bat :app:assembleRelease --offline --console=plain
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_all_tests.ps1
```

APK release: `app/build/outputs/apk/release/app-release.apk`
(**39.772.730 bytes / 37,9 MB**; R8 + assinatura local). O tamanho cresceu em relação à
V20.5 por causa do modelo Vosk embutido (~31 MiB compactados).

**Testes rigorosos**: 37 testes JVM (JUnitCore) + 400.015 verificações do cálculo de
memória em C++ nativo, todos passando. Detalhes e limitações em `docs/testes.md`.

## Modelo

- Arquivo: `Ternary-Bonsai-2-4B-Q2_0.gguf`
- Origem verificada (a mesma referida pelo Loci): `prism-ml/Ternary-Bonsai-2-4B-gguf`, commit `50ebba61be85e5be07e8083db468ace0fc1bac43`.
- SHA-256: `64a5183b44921bdb5ac4a6f5f92007821d6ebe5c66997d3c147fd214ab9433d4`.
- No app: o assistente descobre o 4B automaticamente nos caminhos acima e copia para `filesDir/user-model.gguf`; se não encontrar, use Configurações → Importar modelo `.gguf`.
- No device usado no desenvolvimento, o 4B foi instalado via ADB (deep link do Loci + download verificado + debug assinado com a keystore release + `run-as` + release final).

## Vosk PT-BR embutido

- Asset: `app/src/main/assets/vosk-model-small-pt-0.3.zip` — 32.453.112 bytes,
  SHA-256 `6e1ce909032e1afa7a88e68a3d628ecafff302bdf195befab308826c395e93b7`.
- 16 entradas no zip, 53.552.717 bytes descomprimidos, 14 arquivos obrigatórios
  (`final.mdl`, `Gr.fst`, `HCLr.fst`, `mfcc.conf`, `phones.txt`, `disambig_tid.int`,
  `word_boundary.int`, `README`, `ivector/*`).
- `VoskModelStore.ensureModel()` copia o asset para um `.part`, valida hash/estrutura/tamanho,
  extrai num staging `.extract` e publica de forma atômica; rejeita entradas com `..`,
  separadores absolutos ou `:` no nome.

## Otimizações inspiradas no Loci (implementação própria)

- KV q8_0 com fallback F16, flash attention, separação lógica de prefill/decode e estatísticas nativas (prefill_ms, decode_ms, tok/s).
- Admissão por memória com headroom de 1,25 GiB e orçamento adaptativo de contexto.
- Nenhum código, asset ou arquivo do Loci foi copiado para o Glia.

## Benchmarks / limitações

- Ver `docs/benchmarks.md`, `docs/loci-analise.md` e `docs/testes.md`.
- Device desconectado na verificação final: falta validar em runtime o wake word, o STT e o TPS reais.
- HyperOS bloqueia injeção de input por ADB (`INJECT_EVENTS`) e o Loci não exporta seu arquivo de modelo; por isso a cópia final foi feita com `run-as` num build debug assinado com a mesma keystore do release.

## Permissões MIUI / HyperOS

Microfone, FGS microphone/mediaPlayback, WAKE_LOCK, VIBRATE, BT/Wi-Fi, overlay, tile; recomendado ignorar otimização de bateria.
