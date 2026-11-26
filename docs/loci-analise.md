# Análise do Loci — REA, achados e mapeamento no Glia V20.2

> Objetivo do usuário: "destrinche o 'Loci' com o REA e use toda a lógica de comunicação
> entre a base do app e o Ternary Bonsai 2 4B já instalado ... da maneira mais rápida e
> principalmente 'Otimizada possível'."

## Alvo e evidência

O dispositivo Redmi Note 11S (`9XCAROHMCY65I7MB`, `model:2201117SI`) foi conectado
via ADB e autorizado. O app Loci está instalado como `app.lociai.loci`. O REA
`pull_adb_package` extraiu o pacote com cobertura completa
(`ev_0889750928dee842e65014a5c942e55f27ed13f2c66e71cdcc8693e614129b43`) para
`%TEMP%\opencode\loci-apk-pull\app.lociai.loci\`:

| Arquivo | Tamanho | SHA-256 |
|---|---:|---|
| base.apk | 33.956.351 | 42835456a63289bd8da556a44fc96baab7974c59ca89571f797b40095c28ffc1 |
| split_config.arm64_v8a.apk | 36.927.823 | 1acb3b491c76f4190651f796c231363d1e84dfc2b28dc0dd3c7a3937059c6901 |
| split_config.pt.apk | 237.977 | — |
| split_config.xxhdpi.apk | 70.845 | — |

`inspect_android_package` não pôde ser executado: `REA_JADX_MCP_JAR` não configurado
e nenhum `jadx*.jar` foi encontrado. `decode_android_resources` também ficou
indisponível (apktool fora do PATH). Ghidra não está configurado e, neste Windows,
não aceita ELF arm64; IDA/Kuna/Hopper também indisponíveis. Por isso a leitura de
código Java/Kotlin foi feita com `dexdump -l plain` sobre os DEX extraídos do APK,
complementada por strings ASCII do `libloci_llama_jni.so`.

**Limitação importante:** trata-se de evidência estática. Nenhuma execução do Loci
foi instrumentada com Frida; nenhum código ou asset do Loci foi copiado para o Glia.

## Observações (DEX + biblioteca nativa)

Classes Loci relevantes:
- `com.mwaldman.loci.domain.inference.NativeLlamaBridge`
- `com.mwaldman.loci.domain.inference.LlamaInferenceService`
- `com.mwaldman.loci.domain.inference.NativeAllocationCallback`
- `com.mwaldman.loci.domain.inference.NativeLlamaTokenCallback`

API nativa observada no DEX inclui: `nativeCreateWithMemoryBudget`, `nativePrefill`,
`nativePromotePrefill`, `nativeCanResume`, `nativeCanRewindPrefix`,
`nativePushKvSnapshot`/`nativePopKvSnapshot`, `nativeSaveConversationKvState`,
`nativeShedContext`, `nativeGenerate`, `nativeResumeGeneration`, `nativeStop`,
`nativeCountTokens`, `nativeLastGenerationStats`, `nativePrefillProgress`,
`nativeCurrentCoreResidency`, `nativeResidencyStats`, `nativeSystemInfo`,
`nativeCpuFeatures`, `nativeLoadMultimodalProjector`.

Strings/configuração observadas:
- `gpuLayers=99, preferGpu=false, residencySystemHeadroomBytes=1342177280`
- `decodeThreads`, `prefillThreads`, `numOfThreads`
- `kvCacheBytes`, `kvTypeK/V`, `headCountKv`, `flashAttentionType`
- `retainedBytes`, `reusedPrefixTokens`, `admittedContextTokens`, `safePromptLimitTokens`
- `Bonsai 2 4B` e a descrição "Best-in-class quality under 1 GB..." (texto do modelo no app)

Símbolos reais confirmados em `libloci_llama_jni.so`: `llama_model_load_from_file`,
`llama_init_from_model`, `llama_attach_threadpool`, `ggml_threadpool_new`,
`ggml_threadpool_params_default`, `llama_memory_clear`, `llama_state_seq_get_data/set_data`,
`llama_sampler_init_penalties/top_k/top_p/min_p/temp/dist/greedy`, `llama_n_threads`,
`llama_n_threads_batch`, `llama_tokenize`, `llama_batch_get_one`, `llama_decode`,
`llama_get_logits_ith`, `llama_model_chat_template`, `ggml_backend_load_all_from_path`,
`ggml_backend_dev_by_type`, `llama_flash_attn_type_name`.

Logs nativos observados:
- `prefill done: tokens=%zu reused=%zu`
- `generate done: ... prefill_ms=%lld decode_ms=%lld pacing_ms=%lld tps=%.2f`
- `residency preload/post-load gate: ... available=... margin=... reserve=... budget=... decision=...`
- `Context creation failed with q8_0 KV; retrying with F16 defaults`
- `Native model allocations exceed available memory. (native_memory_budget_exceeded)`

## Interpretação (inferência, não observação de execução)

O Loci separa prefill e decode em fases com estatísticas próprias, usa CPU com
threadpool (`preferGpu=false`), KV cache `q8_0` com fallback para F16, admite ou
recusa a carga conforme memória disponível com headroom de ~1,25 GiB, mantém
snapshots de KV/conversação e mede TPS/latência dentro do motor nativo.

## Mapeamento para o Glia V20.2 (implementado, sem copiar código)

| Padrão observado no Loci | Implementação própria no Glia | Arquivo |
|---|---|---|
| CPU com `n_threads` / `n_threads_batch` | já existia; mantido | `llama_jni.cpp` |
| KV `q8_0` com fallback F16 + flash attention | `make_context(quantized_kv)` usa `GGML_TYPE_Q8_0`, `LLAMA_FLASH_ATTN_TYPE_ENABLED` e recai para F16 se `llama_init_from_model` falhar | `llama_jni.cpp` |
| Stats `prefill_ms` / `decode_ms` / `tps` | `nativeLastGenerationStats()` publica `[promptTokens, generatedTokens, prefillMs, decodeMs, tpsX100, reusedPrefixTokens]`; UI mostra `prefill ... decode ... tok/s` | `llama_jni.cpp`, `LlamaBridge.kt`, `GliaState.kt`, `GliaVoiceService.kt` |
| `nativeCountTokens` | exposto para diagnóstico de limite de contexto | `LlamaBridge.kt` |
| Headroom de memória ~1,25 GiB | `GliaMemory.SYSTEM_HEADROOM_BYTES = 1_342_177_280`, `needed = modelSize + headroom` | `GliaMemory.kt` |
| mmap + KV limpo por geração | já existia; mantido | `llama_jni.cpp` |

Não replicados nesta rodada (dependem de protocolo de estado mais amplo e não foram
necessários para a comunicação voz→modelo): snapshots de KV entre conversas,
`nativeResumeGeneration` e hotset de núcleos. `reusedPrefixTokens` é reportado como
0 no Glia porque o prompt é reconstruído por turno.

## Resultado do build

`:app:assembleDebug :app:assembleRelease --offline` passou com R8 e assinatura local.
APK release final (sem modelo embutido) = **7.581.986 bytes (7,2 MB)**. O modelo 4B
é instalado separadamente em `filesDir/user-model.gguf` (1.212.103.520 bytes).

## Obtenção do modelo 4B a partir do Loci (V20.3)

O Loci foi aberto via ADB com `loci://open_from_hf?model=prism-ml/Ternary-Bonsai-2-4B-gguf`.
A DEX revelou o repositório HF e o arquivo `Ternary-Bonsai-2-4B-Q2_0.gguf` (commit
`50ebba61be85e5be07e8083db468ace0fc1bac43`). O download do Loci fica em
`/data/user/0/app.lociai.loci`, inacessível: `allowBackup=0`, app não debuggable, sem
root e sem FILE_PROVIDER para modelos. Não foi possível ler esse arquivo por ADB.

O **mesmo artefato verificado** foi obtido pela URL pública usada pelo Loci:
- tamanho `1.212.103.520` bytes;
- SHA-256 `64a5183b44921bdb5ac4a6f5f92007821d6ebe5c66997d3c147fd214ab9433d4`.

Instalação no Glia sem toque na UI (INJECT_EVENTS indisponível no HyperOS):
1. `adb push` para `/sdcard/Download/Ternary-Bonsai-2-4B-Q2_0.gguf`;
2. APK debug assinado com a keystore release e `debuggable=true`;
3. `run-as com.glia.assistant` copiou o arquivo para `files/user-model.gguf`;
4. APK release com a mesma assinatura substituiu o debug preservando os dados.

O modelo 2B foi removido: não há asset embutido nem seletor 2B nas configurações.

## Limitações

- JADX/REA Android e descompiladores nativos indisponíveis: análise de DEX por
  `dexdump` e strings, sem pseudocódigo Java ou C++.
- Nenhum benchmark de runtime foi executado; números de TPS/latência são coletados
  pelo próprio app em device, não estimados neste documento.
- Mali-G57 segue em backend CPU (decisão documentada).
