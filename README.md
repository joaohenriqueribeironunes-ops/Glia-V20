# Glia — Assistente de voz determinística (Android)

Glia V29 é um assistente de voz determinístico, sem LLM e sem serviços de IA na nuvem.
Os comandos e a síntese de voz rodam no aparelho; o reconhecimento de fala usa o motor
do sistema Android, alimentado por PCM condicionado e amplificado quando o fabricante
aceita essa entrada, com preferência por processamento offline quando disponível.
Opcionalmente, um modelo local de impressão de voz limita as respostas somente à voz treinada.

## Stack

- Kotlin + Jetpack Compose (sem NDK, sem C++/JNI próprio).
- STT: `android.speech.SpeechRecognizer` (`pt-BR`, `EXTRA_PREFER_OFFLINE`) com PCM
  capturado por `AudioRecord`, ganho ajustável 1x–4x e `EXTRA_AUDIO_SOURCE`; sem Vosk.
- Voz: modelo neural **VITS/Piper pt_BR "Dii" (feminina)** com runtime sherpa-onnx
  `1.12.40` estático (AAR local em `app/libs/`), 1 thread, taxa 1,03 e PCM float via
  `AudioTrack`. Nada é sintetizado na nuvem.
- Comandos: parser puro + `CommandEngine`, sem Ternary, sem GGUF e sem tool calling JSON.
- Notas: arquivo TSV + Base64 no armazenamento privado, com escrita atômica.
- Impressão de voz: 3D-Speaker CAM++ (`sherpa-onnx` 1.12.40), treino com 3 amostras
  e verificação pela maior similaridade de cosseno — tudo no aparelho.
- Condicionamento de PCM: filtro passa-alta (~77 Hz), ganho linear até 0,85 e
  limiter suave apenas nos picos; sem dupla compressão com o AGC nativo.

## Funcionalidades (V29)

1. **Microfone contínuo**: o tile na central de controle liga/desliga o assistente.
   Enquanto estiver ligado, o microfone fica ativo e o reconhecimento reinicia
   imediatamente após cada fala ou silêncio; o mic só é liberado quando você desliga
   o tile. Dizer apenas **“Glia”** aciona os comandos na primeira fala, mesmo com a tela
   bloqueada; “Glia, que horas são?” executa na hora, sem exigir uma segunda fala. O app
   usa `setShowWhenLocked`/`setTurnScreenOn` e wake lock parcial.
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
9. **Alcance do microfone**: ganho ajustável em Configurações (1x–4x, padrão 2x),
   filtro passa-alta para vento/manuseio, limiter suave nos picos e AGC nativo apenas
   quando o ganho é 1x — evita distorção e preserva a forma de onda da fala.
   Se o fabricante não aceitar o PCM amplificado, o app volta automaticamente ao
   microfone do sistema.
10. **Somente minha voz**: Configurações → Somente minha voz grava três frases suas,
    extrai embeddings com CAM++ e passa a ignorar fala de outras pessoas. A decisão usa
    a maior similaridade entre as amostras, então uma gravação ruim não invalida o dono.
    O rigor é ajustável (0,35–0,75; padrão 0,55) e o treino pode ser apagado ou desativado.
11. **Captura corrigida**: detector de fala com 100 ms de calibração e limiar
    adaptativo ao ruído, retry quando o fabricante ignora o PCM amplificado (inclusive
    nos códigos 5/6), sessão do reconhecedor reiniciada sem callbacks atrasados,
    100 ms de silêncio no fim da fala e verificação de voz desativada apenas no fallback
    com microfone ao vivo.
12. **Ativação em uma fala**: o detector usa o token exato “Glia” (normaliza acentos,
    caixa e pontuação), mantém “Olá Glia” apenas por compatibilidade e não dispara em
    palavras como “glioblastoma”. O comando que vem depois de “Glia” é aproveitado na
    mesma transcrição, sem cair em “Não entendi”.

## Compilar e testar

```powershell
cd Glia
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
.\gradlew.bat :app:assembleRelease --offline --console=plain
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_all_tests.ps1
```

APK release: `app/build/outputs/apk/release/app-release.apk`
(**94.074.272 bytes / ~89,7 MB**; R8, assinatura local, somente `arm64-v8a`).

Suíte oficial: **84 testes JVM**, `OK (84 tests)` + `ALL_TESTS_OK`.
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

## Impressão de voz (somente minha voz)

- Asset: `app/src/main/assets/glia-voiceprint.zip` — 25.801.265 bytes,
  SHA-256 `68666cccffcd26c7cc0d58fa1e2e1330850eab57394ce51aa0e28af3e5e4625e`.
- Modelo: `3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx`
  (28.281.165 bytes, SHA-256
  `53cab20ba8f2eb2f6e2d89ecc18e88ef3222169b61b4419cbc13666744a4574b`, 16 kHz).
- Origem: projeto [3D-Speaker](https://github.com/modelscope/3D-Speaker),
  licença Apache 2.0; exportação ONNX mantida por `csukuangfj/speaker-embedding-models`.
- `VoicePrintModelStore.ensureModel()` valida o zip e o modelo antes de publicar em
  `filesDir/glia-voiceprint`; `VoicePrintStore` guarda até 8 embeddings no
  armazenamento privado e usa `SpeakerEmbeddingExtractor` do AAR já embarcado.
- Cada escuta gera um embedding do PCM capturado. Se o filtro estiver ativo, apenas
  similaridade ≥ rigor autoriza wake word e comandos; falha de extração nega o comando.

## Permissões MIUI / HyperOS

Microfone, calendário, notificações, `WAKE_LOCK`, `VIBRATE`, overlay e tile. Para o
HyperOS não encerrar a escuta, permita sobreposição e ignore a otimização de bateria
em Configurações. O app não inicia sozinho após boot: a escuta começa quando o tile é
ativado.

## Limitações conhecidas

- O reconhecimento de fala é do sistema; o app pede modo offline, mas o Android pode
  usar rede dependendo do fabricante e do idioma instalado. A síntese da voz é local.
- O ganho amplifica ruído junto com a voz. O limite superior é 4x; valores altos
  podem causar fadiga em ambientes barulhentos. `EXTRA_AUDIO_SOURCE` não é obrigatório
  para todos os fabricantes — nesses casos a Glia usa o microfone padrão.
- Ativação com tela bloqueada depende de o `SpeechRecognizer` do sistema conseguir
  captar áudio nesse estado; isso ainda precisa de validação em aparelho.
- O filtro “somente minha voz” depende de treino com boa iluminação sonora e do
  microfone do aparelho; rigor muito alto pode rejeitar a voz do dono. Ele não é uma
  prova de identidade e pode ser burlado por gravações.
- O APK cresceu para ~89,7 MB por causa do modelo de voz (~25,8 MB comprimido).
- Device de desenvolvimento desconectado na validação final: falta confirmar em runtime
  wake word com tela bloqueada, lanterna, abertura de apps, calendário real e a taxa
  de aceitação/rejeição da impressão de voz.
- HyperOS bloqueia injeção de input por ADB (`INJECT_EVENTS`); a validação de toque é manual.

## Documentação

- `docs/testes.md` — metodologia da suíte de testes.
- `docs/benchmarks.md` — versão, tamanho do APK e estado da medição.
- `docs/loci-analise.md` — registro histórico da análise que motivou a V20; a V21
  removeu completamente o LLM/Ternary e não copiou código ou assets do Loci.
- `DIAGNOSTICO.md` — checklist de validação em aparelho e logs da V29.
