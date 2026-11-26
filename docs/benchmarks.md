# Benchmarks e Metodologia — Glia V20.3

> Device físico conectado: Redmi Note 11S (`2201117SI`, `miel_in`), serial `9XCAROHMCY65I7MB`.
> Medições de build e instalação concluídas; medições de runtime (tokens/s, RAM) ainda pendentes.

## Medições realizadas

| Medida | Valor |
|---|---|
| Versão do app | 2.0.0 (versionCode 20), source V20.3 |
| APK release (R8 + C++ release, **sem modelo embutido**) | **7.581.986 bytes (7,2 MB, medido)** |
| Modelo instalado | `filesDir/user-model.gguf` no Glia |
| Tamanho do modelo | **1.212.103.520 bytes (medido)** |
| SHA-256 do modelo | `64a5183b44921bdb5ac4a6f5f92007821d6ebe5c66997d3c147fd214ab9433d4` |
| Commit HF do modelo | `50ebba61be85e5be07e8083db468ace0fc1bac43` |
| Push para `/sdcard/Download` | ~34,5 MB/s, 33,5 s |
| Quantização | Q2_0 (upstream llama.cpp b11556+) |
| Contexto | teto do app 4096 tokens |
| Threads | 2–8 (default 6 no Helio G96) |
| Headroom de memória | 1.342.177.280 bytes (~1,25 GiB) |
| Assinatura instalada | `3750aff5` (mesma da keystore release local) |

## Como o modelo chegou ao device

1. Deep link do Loci disparado por ADB: `loci://open_from_hf?model=prism-ml/Ternary-Bonsai-2-4B-gguf`.
2. O download do Loci ficou em app-private inacessível (`allowBackup=0`, não debuggable, sem root).
3. O mesmo artefato verificado foi obtido pela URL pública observada no Loci e enviado por
   `adb push` para `/sdcard/Download/Ternary-Bonsai-2-4B-Q2_0.gguf`.
4. APK debug assinado com a keystore release e `debuggable=true` foi instalado; `run-as` copiou o
   arquivo para `files/user-model.gguf`; o APK release foi reinstalado com a mesma assinatura,
   preservando os dados.

## Metodologia de runtime (pendente)

1. Ligar o tile "Glia" e falar uma frase de teste.
2. `prefill_ms`, `decode_ms` e `tps` vêm de `nativeLastGenerationStats()` (JNI), exibidos na UI.
3. `adb logcat -s GliaVoice GliaNative` registra `generate: prompt=... generated=... prefill_ms=... decode_ms=... tps=...`.
4. Pico de RAM: `adb shell dumpsys meminfo` filtrando o PID do Glia durante a geração.

## Limitações

- O Loci não exporta o arquivo de modelo por provider/AppFunctions; sem a etapa debug+`run-as`,
  a importação depende de SAF (toque na UI, bloqueado aqui por `INJECT_EVENTS`).
- `INJECT_EVENTS` indisponível: não foi possível desbloquear a tela nem tocar na UI por ADB.
- Mali-G57: backend CPU por decisão documentada; sem benchmark GPU real.
- Vosk STT model não embutido.
