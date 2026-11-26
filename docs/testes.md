# Testes rigorosos — Glia V20.6

Objetivo: validar o Ternary Bonsai 2 4B e o pipeline de voz sem depender de toque na UI
(HyperOS bloqueia `INJECT_EVENTS`) nem do device conectado. Toda a lógica crítica foi
extraída para código puro (JVM e C++ host) e coberta por testes determinísticos.

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
3. executa `org.junit.runner.JUnitCore` com as 5 classes de teste;
4. compila e executa `app/src/test/native/memory_budget_test.cpp` com g++ (MinGW).

Resultado observado: `BUILD SUCCESSFUL`, `OK (37 tests)`, `budget nativo: 400015
verificacoes, 0 falhas`, `ALL_TESTS_OK`, exit 0.

## Suíte JVM (37 testes)

| Classe | O que cobre |
|---|---|
| `core.MemoryBudgetVectorTest` | Vetores de bytes/token idênticos ao nativo (q8_0/F16), limites de contexto (4096/1024/256), rejeição abaixo do mínimo, admissão, monotonicidade e 100.000 casos determinísticos (`Random(0x5DEECE66D)`) |
| `llm.ModelDiscoveryTest` | Magic GGUF byte a byte, validação de tamanho, precedência do caminho salvo, precedência do Download público, fallback com aviso, arquivo privado inválido, `.part` corrompido, ausência de candidatos, deduplicação e ordem dos 7 candidatos |
| `llm.GgufHeaderTest` | Header sintético com todos os tipos de metadado GGUF (UINT8..FLOAT64, ARRAY, STRING), rejeição de magic/versão/truncamento/string suspeita; teste opcional do arquivo real via `-Dglia.model.path=...` |
| `stt.VoskAssetTest` | Asset embutido existe, hash e tamanho conhecidos, zip completo e seguro, `safeRelative` rejeita travessia/separadores/`..`/`:`, zip maligno, diretório sintético; extração real opcional via `-Dglia.vosk.dir=...` |
| `stt.VoskExtractionTest` | Extração real do zip (53.552.717 bytes em 14 arquivos obrigatórios), idempotência, hash alterado rejeitado, zip malicioso rejeitado, alvo inválido substituído, zip inexistente |

## Suíte nativa (C++ host)

`app/src/test/native/memory_budget_test.cpp` inclui `app/src/main/cpp/memory_budget.h`
e valida os mesmos vetores do Kotlin, mais 100.000 invariantes com xorshift64:
`budget nativo: 400015 verificacoes, 0 falhas`.

Isso garante que Kotlin (decisão de admissão no app) e C++ (ctx real no JNI) usam a
mesma fórmula, evitando divergência entre o teto anunciado e o contexto aplicado.

## Limitação conhecida

`:app:testDebugUnitTest --offline` falha com `ClassNotFoundException` no
Gradle Test Executor (worker Java 17), embora as classes existam em
`app/build/tmp/kotlin-classes/debugUnitTest` e o mesmo classpath funcione via
`JUnitCore` manual. O daemon foi reiniciado (`gradlew --stop`) sem resolver. Por isso
o contorno oficial é `scripts/run_all_tests.ps1`.

`androidx.test` não está no cache Gradle local e o build é `--offline`, então não há
testes instrumentados (`androidTest`); a validação em device depende de execução manual.
