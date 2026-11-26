# Diagnóstico — Glia V22

A V22 não tem LLM, Ternary, GGUF, llama.cpp, Vosk, JNI próprio nem tool calling JSON.
O fluxo é: microfone → `SpeechRecognizer` do sistema → `CommandParser`/`CommandEngine`
→ voz neural VITS/Piper pt-BR feminina embarcada → `AudioTrack`.

A instalação da voz é validada por SHA-256 e estrutura antes de ser publicada em
`filesDir/glia-neural-voice`. Se o asset estiver ausente ou corrompido, a Glia avisa
no status em vez de falhar silenciosamente.

## Verificação rápida no aparelho

1. Instale o APK release e conceda microfone, calendário e notificações.
2. Abra o app e confirme os status: assistente, voz (“Dii”) e microfone.
3. Use “Testar voz” e confirme que a fala soa feminina e natural, sem rede.
4. Toque no tile da central de controle para ligar a escuta; o tile deve mostrar
   “Ouvindo Olá Glia”.
5. Com a tela bloqueada, diga “Olá Glia”; o app deve acender a tela e responder.
6. Teste: “Que horas são?”, “Que dia é hoje?”, “Tenho compromissos hoje?”,
   “Ligar a lanterna”, “Abra a câmera”.
7. Diga “Glia, quero diminuir a carga cognitiva”, fale um pensamento e confirme
   que a tela “Pensamentos” abre com o texto salvo.

## Logs úteis

```powershell
adb logcat -s GliaVoice GliaTts NeuralVoiceStore GliaApplication
```

## Limitações conhecidas

- O dispositivo de desenvolvimento estava desconectado na validação final;
  tela bloqueada, lanterna, abertura de apps, calendário real e a ativação via
  `SpeechRecognizer` dependem de teste manual.
- HyperOS bloqueia injeção de input por ADB (`INJECT_EVENTS`).
- O reconhecimento de fala é do sistema; o app pede modo offline, mas o Android
  pode usar rede conforme o fabricante e os idiomas instalados.
- A síntese é 100% local e não baixa vozes da rede; exige o asset embedado no APK.
- Para o HyperOS não encerrar a escuta, permita sobreposição e desative a
  otimização de bateria para a Glia.

Detalhes de build e testes: `README.md`, `docs/testes.md` e `docs/benchmarks.md`.
