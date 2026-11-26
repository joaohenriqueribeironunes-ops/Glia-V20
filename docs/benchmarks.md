# Benchmarks e Metodologia — Glia V20.5

## Medições realizadas

| Medida | Valor |
|---|---|
| Versão | 2.0.5 / versionCode 20 (tag `v20`) |
| Device usado | Redmi Note 11S, `product:miel_in`, `model:2201117SI`, serial `9XCAROHMCY65I7MB` |
| APK release (sem asset 2B) | **7.585.386 bytes (7,23 MB)** |
| Build release | R8 + C++ release + assinatura local; `:app:assembleRelease --offline` OK |
| Modelo único | `Ternary-Bonsai-2-4B-Q2_0.gguf` |
| Tamanho do modelo | **1.212.103.520 bytes (1,13 GiB)** |
| SHA-256 | `64a5183b44921bdb5ac4a6f5f92007821d6ebe5c66997d3c147fd214ab9433d4` |
| Commit HF de origem | `50ebba61be85e5be07e8083db468ace0fc1bac43` |
| Push ADB do 4B | ~34,5 MB/s, ~33,5 s |
| headroom de sistema | 1.342.177.280 bytes (1,25 GiB) |
| Reserva de runtime | 268.435.456 bytes (256 MiB) |
| Contexto adaptativo | 256 a 4096 tokens |
| KV | q8_0 com fallback F16 |
| Assinatura instalada no device | `3750aff5` (mesma da keystore release local) |

## Teto de memória (V20.4)

1. `GliaMemory.availableBytes()` lê `MemAvailable` em `/proc/meminfo`; se indisponível, usa `ActivityManager.availMem`.
2. `budget = MemAvailable - 1,25 GiB - 256 MiB - tamanho_do_modelo`.
3. O JNI calcula `bytes/token = 2 * n_layer * n_head_kv * (n_embd / n_head_kv) * 1,0625 + 512` para KV q8_0.
4. `n_ctx = clamp(requested, 256, min(4096, budget / bytes_per_token))`.
5. Se `budget / bytes_per_token < 256`, `nativeLoadModel` retorna `-4` e o app exibe erro claro; nunca carrega um contexto que estouraria o teto.
6. O status do app mostra contexto real, tipo de KV (`q8_0`/`F16`) e teto em MB.

## Descoberta automática do modelo (V20.5)

`ModelStore.discoverModel(context, savedPath)` procura o GGUF nesta ordem:

1. caminho salvo em `GliaPrefs.modelPath`;
2. `filesDir/user-model.gguf`;
3. `filesDir/Ternary-Bonsai-2-4B-Q2_0.gguf`;
4. diretório externo Download específico do app (`getExternalFilesDir(DIRECTORY_DOWNLOADS)`);
5. `/sdcard/Download`, `/sdcard/Documents` e `/storage/emulated/0/Download`.

Validação: magic `GGUF`, tamanho entre 1 KiB e 3 GB. Quando o arquivo encontrado tem
exatamente 1.212.103.520 bytes (o Q2_0 esperado), ele é copiado atomicamente para
`filesDir/user-model.gguf` via arquivo `.part` e renomeado; outro GGUF é usado como
fallback com aviso. O serviço chama `discoverModel` a cada carga e grava o caminho
resolvido; as Configurações exibem o modelo descoberto e mantêm a importação via SAF.

## Como medir no device

```powershell
adb shell cat /proc/meminfo
adb shell dumpsys meminfo com.glia.assistant
adb logcat -s GliaVoiceService GliaNative
```

As estatísticas nativas são publicadas no estado do app:
`prefill Xms · decode Yms · T tok/s · N tokens (prompt P, reuse R)`.

## Metodologia pendente / limitações

- Medições de abertura, pico de RAM e tokens/s dependem de abrir o app no device; o ambiente de build não consegue injetar toques por ADB (`INJECT_EVENTS` bloqueado pelo HyperOS).
- O Loci mantém o modelo em app-private inacessível (`run-as` falha, `allowBackup=0`); o arquivo foi obtido da mesma fonte verificada e instalado no Glia via build debug com `run-as`, depois substituído pelo release com a mesma assinatura.
- Mali-G57: backend CPU por decisão documentada; sem comparativo GPU confiável no device.
