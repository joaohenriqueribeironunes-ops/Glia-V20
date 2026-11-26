# Diagnóstico — Glia V31

A V31 não tem LLM, Ternary, GGUF, llama.cpp, Vosk, JNI próprio nem tool calling JSON.
O fluxo é: `AudioRecord` contínuo → filtro passa-alta e ganho → janelas de 3 s com
500 ms de sobreposição via `EXTRA_AUDIO_SOURCE` → verificação opcional da voz com
3D-Speaker CAM++ → `SpeechRecognizer` do sistema → `SecondaryCommand` →
`CommandParser`/`CommandEngine` → voz neural VITS/Piper pt-BR feminina embarcada →
`AudioTrack`.

A instalação da voz é validada por SHA-256 e estrutura antes de ser publicada em
`filesDir/glia-neural-voice`. O modelo de impressão de voz segue o mesmo critério em
`filesDir/glia-voiceprint`; os embeddings ficam em `filesDir/voiceprint.bin`.
Se algum asset estiver ausente ou corrompido, a Glia avisa no status em vez de
falhar silenciosamente. Não há mais detector de fala: o microfone escuta 100% do
áudio e entrega janelas contínuas ao reconhecedor; transcrições repetidas pela
sobreposição são descartadas. Os códigos 5/6 acionam retry e a última tentativa
usa o microfone do sistema ao vivo.
O PCM passa por filtro passa-alta (~77 Hz) e ganho linear até 0,85 com limiter suave
só nos picos; o AGC nativo fica desligado quando há ganho de software para evitar
dupla compressão. A verificação de voz compara a maior similaridade entre as amostras
do treino. Falha interna nessa verificação é avisada uma vez e não silencia a assistente.
Não há palavra de ativação. Com o tile ligado, `SecondaryCommand` remove saudações,
cortesias e o prefixo opcional “Glia”, entrega a frase ao parser e executa somente
comandos reconhecidos. “Glia” sozinha, “olá” e conversas comuns são ignoradas em silêncio.

## Verificação rápida no aparelho

1. Instale o APK release e conceda microfone, calendário e notificações.
2. Abra o app e confirme os status: assistente, voz (“Dii”) e microfone.
3. Use “Testar voz” e confirme que a fala soa feminina e natural, sem rede.
4. Em Configurações → Microfone, ajuste o ganho (padrão 2x). Comece em 2x e suba
   apenas se os comandos não forem ouvidos à distância.
5. Em Configurações → Somente minha voz, grave as três frases em lugar silencioso,
   com o mesmo ganho usado no reconhecimento. Confirme “Treino concluído” e que o filtro
   está ativo. Peça a outra pessoa para dizer “Que horas são?”: a Glia não deve responder.
   Ajuste o rigor se houver falso positivo.
6. Toque no tile da central de controle para ligar a escuta; o tile deve mostrar
   “Ouvindo tudo”, a ilha e o orbe devem ficar roxos (sem modo azul) e o microfone
   deve permanecer ligado até você desligar o tile.
7. Com a tela bloqueada, diga “Que horas são?”; o app deve acender a tela e responder
   na primeira tentativa, sem precisar de “Glia”. O reconhecimento reinicia sozinho
   após cada fala.
8. Diga “Glia” sozinha, “olá” ou “conte uma piada”: a assistente não deve responder.
   O prefixo “Glia” é opcional; se usado, o comando seguinte ainda funciona.
9. Teste: “Que horas são?”, “Que dia é hoje?”, “Tenho compromissos hoje?”,
   “Ligar a lanterna”, “Abra a câmera”.
10. Diga “Quero diminuir a carga cognitiva”, fale um pensamento e confirme
   que a tela “Pensamentos” abre com o texto salvo.

## Logs úteis

```powershell
adb logcat -s GliaVoice GliaTts NeuralVoiceStore VoicePrintModel GliaApplication
```

## Limitações conhecidas

- O dispositivo de desenvolvimento estava desconectado na validação final;
  tela bloqueada, comandos diretos, lanterna, abertura de apps, calendário real
  e as taxas de aceitação/rejeição da voz dependem de teste manual.
- HyperOS bloqueia injeção de input por ADB (`INJECT_EVENTS`).
- O reconhecimento de fala é do sistema; o app pede modo offline, mas o Android
  pode usar rede conforme o fabricante e os idiomas instalados.
- `EXTRA_AUDIO_SOURCE` depende do fabricante. Se o reconhecedor rejeitar o PCM
  amplificado (ERROR_CLIENT/ERROR_AUDIO), a Glia recria o reconhecedor e tenta com
  o microfone padrão. Nesse caso o ganho do app não tem efeito.
- Ganho alto amplifica ruído; use o menor valor que atenda à distância desejada.
- O filtro de voz não é biometria forte: gravações da sua voz podem enganá-lo.
  Se a extração do embedding falhar, a Glia avisa uma vez e permite o comando,
  em vez de silenciar a assistente.
- A sobreposição de 500 ms evita cortar palavras na borda da janela, mas a
  deduplicação pode descartar um comando idêntico repetido em menos de 3 s.
- Treine perto do microfone e com o tile desligado; duas capturas simultâneas podem
  degradar a amostra. O modelo CAM++ não foi treinado especificamente para pt-BR
  (origem zh/en) — valide o rigor no seu aparelho.
- O microfone contínuo aumenta o consumo de bateria; ele só é liberado quando a
  Glia é desligada pelo tile da central de controle.
- A síntese é 100% local e não baixa vozes da rede; exige o asset embedado no APK.
- Para o HyperOS não encerrar a escuta, permita sobreposição e desative a
  otimização de bateria para a Glia.

Detalhes de build e testes: `README.md`, `docs/testes.md` e `docs/benchmarks.md`.
