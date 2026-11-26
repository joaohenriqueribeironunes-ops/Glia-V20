# Glia — Assistente de voz offline determinístico (Android)

Glia V21 é um assistente de voz 100% offline para Android, sem LLM e sem nuvem.
O reconhecimento usa Vosk PT-BR embutido; as respostas vêm de um motor determinístico
em Kotlin e a fala é sintetizada pela voz neural local do sistema.

## Stack

- Kotlin + Jetpack Compose (sem NDK, sem C++/JNI próprio).
- STT offline: `vosk-android 0.3.75`, modelo PT-BR `vosk-model-small-pt-0.3.zip` embutido.
- TTS local: `android.speech.tts.TextToSpeech`, voz pt-BR feminina preferida, sem rede.
- Comandos: parser puro + `CommandEngine`, sem Ternary, sem GGUF e sem tool calling JSON.
- Notas: arquivo TSV + Base64 no armazenamento privado, com escrita atômica.

## Funcionalidades (V21)

1. **Ativação**: tile na central de controle liga/desliga o loop de wake word; dizer
   **“Olá Glia”** (também aceita “Glia”/“Clia”) aciona o assistente mesmo com a tela
   bloqueada. O app usa `setShowWhenLocked`/`setTurnScreenOn`, wake lock parcial e volta
   ao modo de escuta após cada comando.
2. **Hora e data**: “Que horas são?”, “Que dia é hoje?”.
3. **Calendário**: “Tenho compromissos hoje/amanhã/esta semana?” usa `READ_CALENDAR`
   e responde com até três eventos próximos.
4. **Lanterna**: “Ligar a lanterna”, “Desligar a lanterna” ou apenas alternar.
5. **Abrir aplicativos**: “Abra o WhatsApp”, “Abra a câmera”, “Abra o ...”.
6. **Descarregamento cognitivo**: dizer **“Glia, quero diminuir a carga cognitiva”**,
   falar o pensamento e ouvir: **“Certo, armazenei seus pensamentos. Foi bom pensar com você.”**
   O pensamento aparece em um bloco de notas estilizado dentro do app.
7. **Notas estilizadas**: tela “Pensamentos” com data/hora, exclusão individual e limpeza total.
8. **Voz neural feminina leve**: ranking puro prioriza voz pt-BR local, sem rede,
   com indício feminino; pitch 1,06 e fala por frase para baixo custo de CPU.

## Compilar e testar

```powershell
cd Glia
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
.\gradlew.bat :app:assembleRelease --offline --console=plain
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_all_tests.ps1
```

APK release: `app/build/outputs/apk/release/app-release.apk`
(**36.757.472 bytes / ~35,1 MB**; R8, assinatura local, somente `arm64-v8a`).

Suíte oficial: **49 testes JVM**, `OK (49 tests)` + `ALL_TESTS_OK`.
O Gradle Test Executor deste ambiente falha com `ClassNotFoundException`; por isso
`scripts/run_all_tests.ps1` roda JUnitCore com classpath explícito. Detalhes em
`docs/testes.md`.

## Vosk PT-BR embutido

- Asset: `app/src/main/assets/vosk-model-small-pt-0.3.zip` — 32.453.112 bytes,
  SHA-256 `6e1ce909032e1afa7a88e68a3d628ecafff302bdf195befab308826c395e93b7`.
- 16 entradas no zip, 53.552.717 bytes descomprimidos, 14 arquivos obrigatórios
  (`final.mdl`, `Gr.fst`, `HCLr.fst`, `mfcc.conf`, `phones.txt`, `disambig_tid.int`,
  `word_boundary.int`, `README`, `ivector/*`).
- `VoskModelStore.ensureModel()` valida hash/estrutura/tamanho, extrai em staging e
  publica por rename atômico; rejeita travessia de diretório e nomes absolutos.

## Permissões MIUI / HyperOS

Microfone, calendário, notificações, `WAKE_LOCK`, `VIBRATE`, overlay e tile. Para o
HyperOS não encerrar a escuta, permita sobreposição e ignore a otimização de bateria
em Configurações. O app não inicia sozinho após boot: a escuta começa quando o tile é
ativado.

## Limitações conhecidas

- Device de desenvolvimento desconectado na validação final: falta confirmar em runtime
  wake word com tela bloqueada, lanterna, abertura de apps e calendário real.
- HyperOS bloqueia injeção de input por ADB (`INJECT_EVENTS`); a validação de toque é manual.
- A voz neural depende do motor TTS instalado no aparelho; o app escolhe a melhor voz
  pt-BR local disponível e não baixa nada da rede.

## Documentação

- `docs/testes.md` — metodologia da suíte de testes.
- `docs/benchmarks.md` — versão, tamanho do APK e estado da medição.
- `docs/loci-analise.md` — registro histórico da análise que motivou a V20; a V21
  removeu completamente o LLM/Ternary e não copiou código ou assets do Loci.
- `DIAGNOSTICO.md` — checklist de validação em aparelho e logs da V21.
