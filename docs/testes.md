# Testes rigorosos — Glia V27

Objeto: validar o motor determinístico de comandos, as notas, a correspondência de
apps, os assets da voz neural e da impressão de voz sem depender de toque na UI
(HyperOS bloqueia `INJECT_EVENTS`) nem de device conectado. Toda a lógica crítica foi
extraída para código puro (JVM) e é coberta por testes determinísticos. A V27 não tem
LLM, Vosk, C++ próprio nem testes nativos; a política de retry, a escuta contínua, o
ganho do microfone, o detector de fala adaptativo, o planejamento de captura, a decisão
de verificação de voz e a matemática da impressão de voz são testados isoladamente.

## Como executar

```powershell
cd Glia
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_all_tests.ps1
```

O script:

1. compila `:app:compileDebugUnitTestKotlin --offline`;
2. monta classpath explícito (classes de teste + classes do app + junit 4.13.2 +
   hamcrest 1.3 + kotlin-stdlib 2.0.21 + android.jar);
3. executa `org.junit.runner.JUnitCore` com as 13 classes de teste listadas no script.

Resultado observado: `BUILD SUCCESSFUL`, `OK (72 tests)`, `ALL_TESTS_OK`, exit 0.

## Suíte JVM (72 testes)

| Classe | O que cobre |
|---|---|
| `commands.CommandParserTest` | Normalização de acentos/pontuação, hora, data, calendário com período, lanterna com estado, “Abra o ...”, descarregamento cognitivo, leitura de notas, ajuda, texto vazio/desconhecido e “abra o” sem nome |
| `commands.CalendarSummaryTest` | Ausência de compromissos, horários cheios/meios, lista com até três eventos, evento de dia inteiro, eventos passados e indicação “os primeiros são” |
| `commands.CognitiveNotesStoreTest` | Ordem recente, texto vazio, limite de 4000 caracteres, acentos/quebras/tabs via Base64, exclusão, limpeza, resumo e linha corrompida ignorada |
| `commands.AppMatcherTest` | Nome exato sem acentos, prefixo, trecho, desempate pelo rótulo mais curto, ausência de correspondência e hierarquia de pontuação |
| `tts.NeuralVoiceAssetTest` | Asset embutido existe, hash e tamanho conhecidos, zip completo e seguro, `safeRelative` normaliza separadores do Windows e rejeita travessia/`..`/`:`, zip maligno; diretório exige todos os arquivos obrigatórios |
| `tts.NeuralVoiceStoreTest` | Extração real do zip (64.254.245 bytes em 11 entradas), idempotência, hash alterado rejeitado, zip malicioso rejeitado, alvo inválido substituído, zip inexistente |
| `stt.SttRetryPolicyTest` | `ERROR_CLIENT` (2) e `ERROR_AUDIO` (3) disparam retry; `ERROR_NO_MATCH` (5) e timeout (6) disparam retry somente após fonte amplificada; primeira tentativa é amplificada/offline, segunda amplificada/online e terceira com microfone padrão; erros silenciosos 5/6/8 não poluem o último erro |
| `stt.ContinuousListenPolicyTest` | Timeout de 30 s, reinício imediato (0 ms) e ignorar transcrições enquanto a Glia fala |
| `stt.MicGainTest` | Limites 1x–4x, amplificação sem clipping e RMS para separar silêncio de fala |
| `stt.SpeechGateTest` | Ruído adaptativo, fala fraca, dois frames para iniciar, fim por silêncio, limite máximo e reinício do estado |
| `stt.CapturePlannerTest` | Ganho do usuário nas tentativas amplificadas, ganho padrão no fallback, preferência offline só na primeira e clamp de tentativas |
| `voice.VoicePrintMathTest` | Limiar 0,35–0,75, média de embeddings, cosseno, `isOwner` e encode/decode VP10 com validação de NaN/Inf e corrupção |
| `voice.VoiceCheckTest` | `DENY` nunca autoriza; `SKIPPED` autoriza; `UNAVAILABLE` respeita a política fail-controlled |

## Limitação conhecida

`:app:testDebugUnitTest --offline` falha com `ClassNotFoundException` no
Gradle Test Executor (worker Java 17), embora as classes existam em
`app/build/tmp/kotlin-classes/debugUnitTest` e o mesmo classpath funcione via
`JUnitCore` manual. Por isso o contorno oficial é `scripts/run_all_tests.ps1`.

`androidx.test` não está no cache Gradle local e o build é `--offline`, então não há
testes instrumentados (`androidTest`); a validação em device depende de execução manual.

## Validação pendente em device

Com o Redmi Note 11S conectado, validar manualmente: treino das três frases em
Configurações → Somente minha voz, rejeição da voz de outra pessoa, wake word “Olá Glia”
com tela bloqueada (usando `SpeechRecognizer` do sistema, com e sem PCM amplificado),
queda automática para o microfone do sistema quando o fabricante ignora
`EXTRA_AUDIO_SOURCE`, ganho ajustável, lanterna, abertura de aplicativos por voz,
consulta real ao calendário,
qualidade da voz feminina neural e persistência das notas após reinício.
