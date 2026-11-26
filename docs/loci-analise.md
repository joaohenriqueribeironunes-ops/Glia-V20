# Análise do Loci — registro histórico (V20.x)

> Esta análise foi feita na linha V20 para investigar o modelo Ternary Bonsai 2 4B.
> A V21 removeu completamente o LLM do Glia. Nenhum código, asset ou arquivo do Loci
> foi copiado para o Glia; apenas padrões observados foram reinterpretados em código próprio.

## Alvo e evidência

O dispositivo Redmi Note 11S (`9XCAROHMCY65I7MB`, `model:2201117SI`) foi conectado
via ADB e autorizado. O app Loci (`app.lociai.loci`) foi extraído com REA
(`pull_adb_package`, cobertura `ev_0889750928dee842e65014a5c942e55f27ed13f2c66e71cdcc8693e614129b43`).

| Arquivo | Tamanho | SHA-256 |
|---|---:|---|
| base.apk | 33.956.351 | `42835456a63289bd8da556a44fc96baab7974c59ca89571f797b40095c28ffc1` |
| split_config.arm64_v8a.apk | 36.927.823 | `1acb3b491c76f4190651f796c231363d1e84dfc2b28dc0dd3c7a3937059c6901` |
| split_config.pt.apk | 237.977 | — |
| split_config.xxhdpi.apk | 70.845 | — |

JADX/REA Android, apktool, Ghidra, IDA/Kuna/Hopper não estavam disponíveis; a leitura
foi feita com `dexdump -l plain` sobre os DEX e strings de `libloci_llama_jni.so`.
Trata-se de evidência estática: nenhuma execução do Loci foi instrumentada.

## Achados observados

Classes: `NativeLlamaBridge`, `LlamaInferenceService`, `NativeAllocationCallback`,
`NativeLlamaTokenCallback`. APIs nativas incluíam `nativeCreateWithMemoryBudget`,
`nativePrefill`, `nativeCanResume`, `nativePushKvSnapshot`, `nativeGenerate`,
`nativeStop`, `nativeCountTokens` e `nativeLastGenerationStats`.

Configuração observada: `gpuLayers=99`, `preferGpu=false`,
`residencySystemHeadroomBytes=1342177280`, `decodeThreads`, `prefillThreads`,
`kvCacheBytes`, `kvTypeK/V`, `flashAttentionType`. Símbolos confirmados incluem
`llama_model_load_from_file`, `llama_flash_attn_type_name`, `ggml_backend_dev_by_type`
e samplers `top_k/top_p/min_p/temp/dist/greedy`.

Interpretação: o Loci separava prefill e decode, usava CPU com threadpool, KV `q8_0`
com fallback F16 e admissão por memória disponível com headroom de ~1,25 GiB.

## Mapeamento no Glia V20 (implementação própria)

| Padrão observado | Implementação V20 | Arquivos (removidos na V21) |
|---|---|---|
| CPU com `n_threads`/`n_threads_batch` | threadpool nativo próprio | `llama_jni.cpp` |
| KV `q8_0` com fallback F16 | `make_context(quantized_kv)` | `llama_jni.cpp` |
| `prefill_ms`/`decode_ms`/`tps` | publicado via JNI e mostrado na UI | `LlamaBridge.kt`, `GliaState.kt`, `GliaVoiceService.kt` |
| Headroom de ~1,25 GiB | `GliaMemory.plan()` | `GliaMemory.kt`, `MemoryBudget.kt` |
| Descoberta do GGUF | caminhos salvos, privados e Download público | `ModelStore.kt`, `ModelDiscovery.kt` |

O modelo 4B (`Ternary-Bonsai-2-4B-Q2_0.gguf`, 1.212.103.520 bytes, SHA-256
`64a5183b44921bdb5ac4a6f5f92007821d6ebe5c66997d3c147fd214ab9433d4`) foi obtido da
mesma fonte verificada (`prism-ml/Ternary-Bonsai-2-4B-gguf`, commit
`50ebba61be85e5be07e8083db468ace0fc1bac43`) e instalado por ADB/debug `run-as` porque
o HyperOS bloqueia `INJECT_EVENTS` e o Loci não exporta seu arquivo privado.

## Situação na V21/V22/V23/V24/V25/V26/V27/V28

- Pacote `llm/`, `GliaMemory`, `MemoryBudget`, `llama.cpp`, `CMakeLists.txt`, testes de
  GGUF/budget e configurações de inferência foram removidos.
- Comandos são determinísticos (`CommandParser`/`CommandEngine`). Não há mais GGUF,
  tool calling JSON, KV cache nem tokens/s.
- A V21 removeu o LLM e usou Vosk + TTS do sistema: APK de 36.757.472 bytes.
- A V22 removeu o Vosk, passou a usar o `SpeechRecognizer` do sistema e embutiu a voz
  neural feminina pt-BR Dii com sherpa-onnx: APK de 68.237.708 bytes.
- A V23 corrigiu o `ERROR_CLIENT` do reconhecimento do sistema: `cancel()` ocioso foi
  eliminado, o reconhecedor é recriado e a escuta é repetida sem `EXTRA_PREFER_OFFLINE`.
  APK de 68.239.932 bytes.
- A V24 manteve o microfone ligado enquanto o tile estiver ativo: a escuta reinicia
  imediatamente após cada fala, sem backoff, e a palavra “Olá Glia” não precisa ser
  repetida antes de cada comando. APK de 68.238.688 bytes.
- A V25 ampliou o alcance do microfone: `AudioRecord` com ganho 1x–4x, AGC e
  NoiseSuppressor gera PCM que é entregue ao `SpeechRecognizer` via
  `EXTRA_AUDIO_SOURCE`. Fabricantes que rejeitam esse extra caem de volta para o
  microfone padrão após retry. APK de 68.259.040 bytes.
- A V26 adicionou treino opcional de impressão de voz: três frases geram embeddings
  CAM++ locais e o áudio de cada escuta é verificado antes de autorizar wake word e
  comandos. O modelo 3D-Speaker (Apache 2.0) foi adquirido separadamente, sem copiar
  nada do Loci. APK de 94.068.464 bytes.
- A V27 corrigiu o reconhecimento de som: `SpeechGate` com limiar adaptativo ao ruído,
  retry em `NO_MATCH`/timeout quando o fabricante ignora o PCM amplificado, sessão do
  reconhecedor reiniciada sem callbacks atrasados, verificação de voz desativada no
  fallback com microfone ao vivo e falha técnica do filtro avisada sem silenciar a
  assistente. A suíte passou a 72 testes puros. APK de 94.071.844 bytes.
- A V28 melhorou a precisão do microfone: ganho linear até 0,85 com limiter só nos
  picos, AGC nativo somente em 1x, filtro passa-alta de ~77 Hz, calibração de 100 ms
  no detector de fala, 100 ms de silêncio final e verificação de voz pela maior
  similaridade entre as amostras. A suíte passou a 78 testes puros. APK de
  94.073.432 bytes. Nenhum código ou asset do Loci foi copiado em nenhuma versão.
