# Glia — Assistente de voz determinística (Android)

Glia V22 é um assistente de voz determinístico, sem LLM e sem serviços de IA na nuvem.
Os comandos e a síntese de voz rodam no aparelho; o reconhecimento de fala usa o motor
do sistema Android, com preferência por processamento offline quando disponível.

## Stack

- Kotlin + Jetpack Compose (sem NDK, sem C++/JNI próprio).
- STT: `android.speech.SpeechRecognizer` (`pt-BR`, `EXTRA_PREFER_OFFLINE`); sem Vosk.
- Voz: modelo neural **VITS/Piper pt_BR "Dii" (feminina)** com runtime sherpa-onnx
  `1.12.40` estático (AAR local em `app/libs/`), 1 thread, taxa 1,03 e PCM float via
  `AudioTrack`. Nada é sintetizado na nuvem.
- Comandos: parser puro + `CommandEngine`, sem Ternary, sem GGUF e sem tool calling JSON.
- Notas: arquivo TSV + Base64 no armazenamento privado, com escrita atômica.

## Funcionalidades (V22)

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
8. **Voz neural feminina**: modelo pt-BR embarcado, com timbre mais natural que o TTS
   do sistema e inferência local de baixo custo.

## Compilar e testar

```powershell
cd Glia
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
.\gradlew.bat :app:assembleRelease --offline --console=plain
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_all_tests.ps1
```

APK release: `app/build/outputs/apk/release/app-release.apk`
(**68.237.708 bytes / ~65,1 MB**; R8, assinatura local, somente `arm64-v8a`).

Suíte oficial: **42 testes JVM**, `OK (42 tests)` + `ALL_TESTS_OK`.
O Gradle Test Executor deste ambiente falha com `ClassNotFoundException`; por isso
`scripts/run_all_tests.ps1` roda JUnitCore com classpath explícito. Detalhes em
`docs/testes.md`.

## Voz neural embarcada

- Asset: `app/src/main/assets/glia-neural-voice.zip` — 58.800.891 bytes,
  SHA-256 `813eb4e520f115bdb80337e4aa5b05d3d6999ec2e1e1541bf0137ca5ad2b7c38`.
- 11 entradas no zip, 64.254.245 bytes descomprimidos: `pt_BR-dii-high.onnx`,
  `.onnx.json`, `tokens.txt` e os arquivos essenciais de `espeak-ng-data/`.
- `NeuralVoiceStore.ensureModel()` valida hash/estrutura/tamanho, extrai em staging e
  publica por rename atômico; rejeita travessia de diretório e nomes absolutos.
- Runtime: `app/libs/sherpa-onnx-static-link-onnxruntime-1.12.40.aar`
  (38.120.299 bytes; `libsherpa-onnx-jni.so` ARM64 de 22.249.744 bytes).
- A voz Dii é distribuída sob CC BY-NC-ND 4.0 (TigreGotico Lda) e o pacote sherpa
  sob CC BY-NC-SA 4.0: uso não comercial, sem derivações da voz e com atribuição.

## Permissões MIUI / HyperOS

Microfone, calendário, notificações, `WAKE_LOCK`, `VIBRATE`, overlay e tile. Para o
HyperOS não encerrar a escuta, permita sobreposição e ignore a otimização de bateria
em Configurações. O app não inicia sozinho após boot: a escuta começa quando o tile é
ativado.

## Limitações conhecidas

- O reconhecimento de fala é do sistema; o app pede modo offline, mas o Android pode
  usar rede dependendo do fabricante e do idioma instalado. A síntese da voz é local.
- Ativação com tela bloqueada depende de o `SpeechRecognizer` do sistema conseguir
  captar áudio nesse estado; isso ainda precisa de validação em aparelho.
- Device de desenvolvimento desconectado na validação final: falta confirmar em runtime
  wake word com tela bloqueada, lanterna, abertura de apps e calendário real.
- HyperOS bloqueia injeção de input por ADB (`INJECT_EVENTS`); a validação de toque é manual.

## Documentação

- `docs/testes.md` — metodologia da suíte de testes.
- `docs/benchmarks.md` — versão, tamanho do APK e estado da medição.
- `docs/loci-analise.md` — registro histórico da análise que motivou a V20; a V21
  removeu completamente o LLM/Ternary e não copiou código ou assets do Loci.
- `DIAGNOSTICO.md` — checklist de validação em aparelho e logs da V22.
