# Diagnóstico — Glia V21

A V21 não tem LLM, Ternary, GGUF, llama.cpp, JNI próprio nem tool calling JSON.
O fluxo é: microfone → Vosk PT-BR → `CommandParser`/`CommandEngine` → TTS local.

## Verificação rápida no aparelho

1. Instale o APK release e conceda microfone, calendário e notificações.
2. Abra o app e confirme os status: assistente, voz e microfone.
3. Toque no tile da central de controle para ligar a escuta; o tile deve mostrar
   “Ouvindo Olá Glia”.
4. Com a tela bloqueada, diga “Olá Glia”; o app deve acender a tela e responder.
5. Teste: “Que horas são?”, “Que dia é hoje?”, “Tenho compromissos hoje?”,
   “Ligar a lanterna”, “Abra a câmera”.
6. Diga “Glia, quero diminuir a carga cognitiva”, fale um pensamento e confirme
   que a tela “Pensamentos” abre com o texto salvo.

## Logs úteis

```powershell
adb logcat -s GliaVoice GliaTts VoskStt AudioRecorder GliaApplication
```

## Limitações conhecidas

- O dispositivo de desenvolvimento estava desconectado na validação final;
  tela bloqueada, lanterna, abertura de apps e calendário real dependem de teste manual.
- HyperOS bloqueia injeção de input por ADB (`INJECT_EVENTS`).
- A voz neural depende do motor TTS instalado; o app escolhe a melhor voz pt-BR
  local disponível e nunca baixa vozes da rede.
- Para o HyperOS não encerrar a escuta, permita sobreposição e desative a
  otimização de bateria para a Glia.

Detalhes de build e testes: `README.md`, `docs/testes.md` e `docs/benchmarks.md`.
