package com.glia.assistant.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.glia.assistant.commands.VoiceCandidate
import com.glia.assistant.commands.VoiceRanking
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * TTS local com preferencia por voz neural pt-BR feminina e baixo custo:
 * usa o motor do sistema (sem rede, sem download dentro do app) e escolhe
 * a melhor voz local disponivel. O pitch levemente elevado reforca o timbre
 * feminino quando o motor nao expoe genero.
 */
class GliaTts(context: Context) {
    companion object {
        private const val TAG = "GliaTts"
        private const val MAX_FAILURES = 3
    }

    private val appContext = context.applicationContext
    private val queue = ConcurrentLinkedQueue<String>()
    private val utteranceCounter = AtomicInteger(0)

    private var tts: TextToSpeech? = null

    @Volatile private var ready = false
    @Volatile private var speaking = false
    @Volatile private var initError: String? = null
    @Volatile private var language = "indisponivel"
    @Volatile private var voiceName: String? = null
    private var consecutiveFailures = 0

    var onFinished: (() -> Unit)? = null
    var onTtsError: ((String) -> Unit)? = null
    var onReady: (() -> Unit)? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                initError = "motor de voz indisponivel"
                onTtsError?.invoke(initError!!)
                return@TextToSpeech
            }
            val engine = tts
            if (engine == null) {
                initError = "motor de voz indisponivel"
                return@TextToSpeech
            }
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )

            var result = engine.setLanguage(Locale("pt", "BR"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                result = engine.setLanguage(Locale.getDefault())
            }
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.US)
            }

            applyBestVoice(engine)
            engine.setSpeechRate(1.02f)
            engine.setPitch(1.06f)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    speaking = true
                }

                override fun onDone(utteranceId: String?) {
                    speakNext()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    handleUtteranceError("falha ao sintetizar")
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    handleUtteranceError("falha ao sintetizar ($errorCode)")
                }
            })

            ready = true
            initError = null
            onReady?.invoke()
            speakNext()
        }
    }

    private fun applyBestVoice(engine: TextToSpeech) {
        val voices = try {
            engine.voices
        } catch (_: Exception) {
            null
        } ?: return

        val candidates = voices.map { voice ->
            VoiceCandidate(
                name = voice.name,
                localeTag = voice.locale.toLanguageTag(),
                networkRequired = voice.isNetworkConnectionRequired,
                quality = voice.quality
            )
        }
        val best = VoiceRanking.pick(candidates) ?: return
        val voice = voices.firstOrNull { it.name == best.name } ?: return
        if (engine.setVoice(voice) == TextToSpeech.SUCCESS) {
            voiceName = voice.name
            language = voice.locale.toLanguageTag()
            Log.i(TAG, "Voz selecionada: ${voice.name} (${voice.locale.toLanguageTag()})")
        }
    }

    @Synchronized
    private fun speakNext() {
        val engine = tts ?: return
        if (!ready) return
        val next = queue.poll()
        if (next == null) {
            speaking = false
            onFinished?.invoke()
            return
        }
        speaking = true
        val id = "glia-${utteranceCounter.incrementAndGet()}"
        val result = engine.speak(next, TextToSpeech.QUEUE_ADD, Bundle(), id)
        if (result != TextToSpeech.SUCCESS) {
            consecutiveFailures++
            if (consecutiveFailures >= MAX_FAILURES) {
                queue.clear()
                speaking = false
                onTtsError?.invoke("falha ao falar")
            } else {
                speakNext()
            }
        } else {
            consecutiveFailures = 0
        }
    }

    private fun handleUtteranceError(message: String) {
        consecutiveFailures++
        speaking = false
        if (consecutiveFailures >= MAX_FAILURES) {
            queue.clear()
            onTtsError?.invoke(message)
        } else {
            speakNext()
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        splitSentences(text).forEach { sentence ->
            if (sentence.isNotBlank()) queue.add(sentence)
        }
        speakNext()
    }

    fun isReady(): Boolean = ready

    fun isSpeaking(): Boolean = speaking || queue.isNotEmpty()

    fun status(): String {
        initError?.let { return "erro: $it" }
        if (!ready) return "inicializando..."
        val voice = voiceName?.let { " · $it" } ?: ""
        return "pronta · $language$voice"
    }

    fun stop() {
        queue.clear()
        speaking = false
        try {
            tts?.stop()
        } catch (_: Exception) {
            // ignora
        }
    }

    fun shutdown() {
        queue.clear()
        speaking = false
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
            // ignora
        }
        tts = null
        ready = false
    }

    private fun splitSentences(text: String): List<String> {
        val result = ArrayList<String>()
        val builder = StringBuilder()
        for (ch in text) {
            builder.append(ch)
            if (ch == '.' || ch == '!' || ch == '?' || ch == '\n' || builder.length >= 180) {
                val sentence = builder.toString().trim()
                if (sentence.isNotEmpty()) result.add(sentence)
                builder.setLength(0)
            }
        }
        val tail = builder.toString().trim()
        if (tail.isNotEmpty()) result.add(tail)
        return result
    }
}
