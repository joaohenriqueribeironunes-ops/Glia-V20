# Benchmarks e Metodologia — Glia V20

> Status: **medições estáticas / de build concluídas** (sem device físico no ambiente).
> Valores de runtime exigem o Redmi Note 11S real e estão documentados como pendentes.

## Medições realizadas (estáticas)

| Medida | Valor |
|---|---|
| Versão do app | 2.0.0 (versionCode 20) |
| Tamanho do APK release (R8 + C++ release + modelo 2B embutido) | **528,6 MB (554.273.047 bytes, medido)** |
| Task de build release (incremental, asset já copiado) | **1 min 21 s (medido)** |
| Modelo padrão | Ternary Bonsai 2 2B Q2_0 (546.689.504 bytes) |
| Modelo alternativo | Ternary Bonsai 2 4B Q2_0 (~1,21 GB, importado) |
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
- Primeira resposta exige extração única do GGUF embutido (521 MiB) para `filesDir` e mmap;
  no modelo 4B importado (~1,21 GB Q2_0) a latência inicial é maior.
- Mali-G57 (CPU) usado por design (kernels Q2_0 não-oficiais para Vulkan neste formato).

## Onde registrar novas medições

Adicionar linhas conforme execução em device com `adb logcat -s GliaVoice GliaNative`.
