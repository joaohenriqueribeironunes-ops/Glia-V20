# Benchmarks e Metodologia — Glia V20

> Status: **medições estáticas / de build concluídas** (sem device físico no ambiente).
> Valores de runtime exigem o Redmi Note 11S real e estão documentados como pendentes.

## Medições realizadas (estáticas)

| Medida | Valor |
|---|---|
| Versão do app | 2.0.0 (versionCode 20) |
| Tamanho do APK release (R8 + C++ release) | **~7.2 MB** |
| Task de build release | ~3 min (compilação nativa + minify) |
| Modelo alvo | Ternary Bonsai 2 4B Q2_0 (~1.21 GB) |
| Quantização | Q2_0 (roda no upstream llama.cpp b11556+) |
| Contexto máximo suportado | 262.144 tokens (teto do app: 4096) |
| Threads configuráveis | 2–8 (default 6 no Helio G96) |
| Memória máxima do app para modelo (teto) | ~2.5 GB (model + KV + buffers) |

## Metodologia de medição pretendida (device Redmi Note 11S)

1. **Abertura do app**: cronometrar `MainActivity.onCreate` até `modelStatus == pronto`.
2. **Modelo pronto**: `LlamaBridge.nativeIsLoaded` == true (mmap do GGUF).
3. **Tokens/s**: com `adb shell dumpsys activity` / logcat timestamps nos callbacks
   `onToken`, acumulando tokens/s durante resposta.
4. **Pico de RAM**: `adb shell dumpsys meminfo` filtrando o PID do processo Glia
   (peak `PSS`/`Private Dirty` durante geração).

## Limitações conhecidas

- Sem device/emulador no ambiente de build: abertura, tokens/s e pico de RAM são
  **estimativas documentais**, não medições reales.
- Primeira resposta exige carregamento mmap do GGUF (~1.21 GB Q2_0); modelo 4B em
  cache pode aumentar latência inicial.
- Mali-G57 (CPU) usado por design (kernels Q2_0 não-oficiais para Vulkan neste formato).

## Onde registrar novas medições

Adicionar linhas conforme execução em device com `adb logcat -s GliaVoice GliaNative`.
