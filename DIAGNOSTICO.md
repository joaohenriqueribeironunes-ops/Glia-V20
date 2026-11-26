# Diagnóstico — Glia V29

A V29 não tem LLM, Ternary, GGUF, llama.cpp, Vosk, JNI próprio nem tool calling JSON.
O fluxo é: `AudioRecord` → filtro passa-alta e ganho → PCM amplificado via
`EXTRA_AUDIO_SOURCE` → verificação opcional da voz com 3D-Speaker CAM++ →
`SpeechRecognizer` do sistema → `CommandParser`/`CommandEngine` → voz neural
VITS/Piper pt-BR feminina embarcada → `AudioTrack`.

A instalação da voz é validada por SHA-256 e estrutura antes de ser publicada em
`filesDir/glia-neural-voice`. O modelo de impressão de voz segue o mesmo critério em
`filesDir/glia-voiceprint`; os embeddings ficam em `filesDir/voiceprint.bin`.
Se algum asset estiver ausente ou corrompido, a Glia avisa no status em vez de
falhar silenciosamente. A captura usa `SpeechGate` com 100 ms de calibração e ruído de
fundo adaptativo e não bloqueia o reconhecimento quando o VAD não encontra fala: os
códigos 5/6 acionam retry, e a última tentativa usa o microfone do sistema ao vivo.
O PCM passa por filtro passa-alta (~77 Hz) e ganho linear até 0,85 com limiter suave
só nos picos; o AGC nativo fica desligado quando há ganho de software para evitar
dupla compressão. A verificação de voz compara a maior similaridade entre as amostras
do treino. Falha interna nessa verificação é avisada uma vez e não silencia a assistente.
A ativação usa o token exato “Glia”: dizê-lo sozinho abre a escuta de comando na primeira
fala; se o comando vier na mesma frase, ele é executado imediatamente e não é descartado
como “Não entendi”. Variantes antigas (“Olá Glia”) continuam aceitas.

## Verificação rápida no aparelho

1. Instale o APK release e conceda microfone, calendário e notificações.
2. Abra o app e confirme os status: assistente, voz (“Dii”) e microfone.
3. Use “Testar voz” e confirme que a fala soa feminina e natural, sem rede.
4. Em Configurações → Microfone, ajuste o ganho (padrão 2x). Comece em 2x e suba
   apenas se “Glia” não for ouvida à distância.
5. Em Configurações → Somente minha voz, grave as três frases em lugar silencioso,
   com o mesmo ganho usado no reconhecimento. Confirme “Treino concluído” e que o filtro
   está ativo. Peça a outra pessoa para dizer “Glia”: a Glia não deve responder.
   Ajuste o rigor se houver falso positivo.
6. Toque no tile da central de controle para ligar a escuta; o tile deve mostrar
   “Ouvindo Glia” e o microfone deve permanecer ligado até você desligar o tile.
7. Com a tela bloqueada, diga apenas “Glia”; o app deve acender a tela, vibrar, entrar
   em “ouvindo comando” e aceitar a fala seguinte na primeira tentativa.
   O reconhecimento reinicia sozinho após cada fala, sem esperar nova ativação.
8. Repita dizendo “Glia, que horas são?” em uma única frase: a resposta deve vir sem
   um “Não entendi” intermediário.
9. Teste: “Que horas são?”, “Que dia é hoje?”, “Tenho compromissos hoje?”,
   “Ligar a lanterna”, “Abra a câmera”.
10. Diga “Glia, quero diminuir a carga cognitiva”, fale um pensamento e confirme
   que a tela “Pensamentos” abre com o texto salvo.

## Logs úteis

```powershell
adb logcat -s GliaVoice GliaTts NeuralVoiceStore VoicePrintModel GliaApplication
```

## Limitações conhecidas

- O dispositivo de desenvolvimento estava desconectado na validação final;
  tela bloqueada, lanterna, abertura de apps, calendário real, a ativação via
  `SpeechRecognizer` e as taxas de aceitação/rejeição da voz dependem de teste manual.
- HyperOS bloqueia injeção de input por ADB (`INJECT_EVENTS`).
- O reconhecimento de fala é do sistema; o app pede modo offline, mas o Android
  pode usar rede conforme o fabricante e os idiomas instalados.
- `EXTRA_AUDIO_SOURCE` depende do fabricante. Se o reconhecedor rejeitar o PCM
  amplificado (ERROR_CLIENT/ERROR_AUDIO), a Glia recria o reconhecedor e tenta com
  o microfone padrão. Nesse caso o ganho do app não tem efeito.
- Ganho alto amplifica ruído; use o menor valor que atenda à distância desejada.
- O filtro de voz não é biometria forte: gravações da sua voz podem enganá-lo.
  Se a extração do embedding falhar, o comando é negado enquanto o filtro estiver ativo.
- Treine perto do microfone e com o tile desligado; duas capturas simultâneas podem
  degradar a amostra. O modelo CAM++ não foi treinado especificamente para pt-BR
  (origem zh/en) — valide o rigor no seu aparelho.
- O microfone contínuo aumenta o consumo de bateria; ele só é liberado quando a
  Glia é desligada pelo tile da central de controle.
- A síntese é 100% local e não baixa vozes da rede; exige o asset embedado no APK.
- Para o HyperOS não encerrar a escuta, permita sobreposição e desative a
  otimização de bateria para a Glia.

Detalhes de build e testes: `README.md`, `docs/testes.md` e `docs/benchmarks.md`.
