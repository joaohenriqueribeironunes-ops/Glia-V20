# Analise do Loci — Técnicas Aplicadas ao Glia V20

> **Nota honesta:** o APK do Loci **não foi localizado** no ambiente (buscas em
> Downloads/Desktop/OneDrive/C:\ durante o desenvolvimento; nenhum APK correspondente
> encontrado). A engenharia reversa não pôde ser executada. As técnicas abaixo foram
> aplicadas com base em documentação técnica pública do llama.cpp/GGUF, sem cópia de
> código ou assets do Loci.

## Técnicas do Loci estudadas/aplicadas (via documentação e código próprio)

1. **mmap do GGUF**: `llama_model_load_from_file` com `LLAMA_LOAD_MODE_MMAP`
   (arquivo sob /data/data, sem cópia para RAM).
2. **KV-cache enxugado**: limpar KV (`llama_memory_clear`) a cada geração; contexto
   limitado ao configurado (teto 4096 tokens).
3. **Sampler customizado**: penalidades (repetition/presence), top_k, top_p, min_p, temp.
4. **Contexto curto**: prompt de sistema enxuto + instrução de ferramenta.

## Onde o Loci poderia ter contribuído

- Ciclo de vida do modelo (warm start, swap), pré-carregamento.

## Limitações

- REA/UREA indisponíveis no ambiente; substituições usadas: inspeção estática via
  ferramentas de linha de comando equivalentes.
- GPU Mali-G57: sem benchmark real disponível; decisão documentada de usar CPU backend.
