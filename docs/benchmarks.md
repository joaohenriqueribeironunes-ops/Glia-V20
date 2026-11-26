# Benchmarks e Metodologia — Glia V20.6

## Medições realizadas

| Medida | Valor |
|---|---|
| Versão | release V20.6 (versionCode 20, versionName 2.0.0, tag `v20`) |
| Device usado | Redmi Note 11S, `product:miel_in`, `model:2201117SI`, serial `9XCAROHMCY65I7MB` |
| APK release | **39.772.730 bytes (37,9 MB)** — inclui o zip Vosk embutido |
| Build release | R8 + C++ release + assinatura local; `:app:assembleRelease --offline` OK |
| Modelo único | `Ternary-Bonsai-2-4B-Q2_0.gguf` |
| Tamanho do modelo | **1.212.103.520 bytes (1,13 GiB)** |
| SHA-256 | `64a5183b44921bdb5ac4a6f5f92007821d6ebe5c66997d3c147fd214ab9433d4` |
| Commit HF de origem | `50ebba61be85e5be07e8083db468ace0fc1bac43` |
| Push ADB do 4B | ~34,5 MB/s, ~33,5 s |
| Vosk embutido | `vosk-model-small-pt-0.3.zip`, 32.453.112 bytes, sha256 `6e1ce909032e1afa7a88e68a3d628ecafff302bdf195befab308826c395e93b7` |
| Vosk descomprimido | 16 entradas, 53.552.717 bytes, 14 arquivos obrigatórios |
| headroom de sistema | 1.342.177.280 bytes (1,25 GiB) |
| Reserva de runtime | 268.435.456 bytes (256 MiB) |
| Contexto adaptativo | 256 a 4096 tokens |
| KV | q8_0 com fallback F16 |
| Testes JVM | 37 testes, 0 falhas (JUnitCore) |
| Testes nativos | 400.015 verificações de budget KV, 0 falhas (g++ host) |
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

## Modelo Vosk embutido (V20.6)

`VoskModelStore.ensureModel()` copia o asset zipado para `filesDir/*.part`, valida
SHA-256 + estrutura (14 arquivos obrigatórios, 16 entradas) + tamanho total, extrai
em `.extract` com verificação de path traversal e publica por rename atômico.
`GliaVoiceService.initVosk()` usa esse diretório antes dos diretórios legados;
se falhar, reporta "modelo Vosk indisponivel (asset ausente ou corrompido)".

## Como medir no device

```powershell
adb shell cat /proc/meminfo
adb shell dumpsys meminfo com.glia.assistant
adb logcat -s GliaVoiceService GliaNative
```

As estatísticas nativas são publicadas no estado do app:
`prefill Xms · decode Yms · T tok/s · N tokens (prompt P, reuse R)`.

## Metodologia pendente / limitações

- Medições de abertura, pico de RAM, wake word e tokens/s dependem de abrir o app no device; o ambiente de build não consegue injetar toques por ADB (`INJECT_EVENTS` bloqueado pelo HyperOS). Device desconectado na verificação final.
- `testDebugUnitTest` do Gradle falha com `ClassNotFoundException` no Test Executor mesmo com as classes presentes no classpath; a suíte é executada por `scripts/run_all_tests.ps1` via JUnitCore com classpath explícito (37/37 OK).
- O Loci mantém o modelo em app-private inacessível (`run-as` falha, `allowBackup=0`); o arquivo foi obtido da mesma fonte verificada e instalado no Glia via build debug com `run-as`, depois substituído pelo release com a mesma assinatura.
- Mali-G57: backend CPU por decisão documentada; sem comparativo GPU confiável no device.
