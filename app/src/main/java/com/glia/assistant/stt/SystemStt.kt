package com.glia.assistant.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.MainThread
import java.util.Locale

/**
 * Reconhecimento de fala do sistema, sem modelo embutido e sem código nativo.
 *
 * Todas as chamadas ao SpeechRecognizer acontecem na thread principal, como
 * exigido pelo Android. Cada escuta é one-shot: [onResult] é chamado quando o
 * sistema termina a captação, [onError] em caso de falha e [onPartial] com o
 * texto provisório. O serviço decide quando reiniciar a escuta.
 */
class SystemStt(context: Context) {

    interface Listener {
        fun onReady() {}
        fun onPartial(text: String) {}
        fun onRms(rms: Float) {}
        fun onResult(text: String) {}
        fun onError(code: Int, message: String) {}
    }

    private class Session(val id: Int, val listener: Listener)

    private val available = SpeechRecognizer.isRecognitionAvailable(context)
    private val recognizer: SpeechRecognizer? = if (available) {
        SpeechRecognizer.createSpeechRecognizer(context)
    } else {
        null
    }

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    private var nextId = 0
    private var active: Session? = null

    private val bridge = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            current()?.listener?.onReady()
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdb: Float) {
            current()?.listener?.onRms(rmsdb)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            val session = active
            active = null
            if (session != null) session.listener.onError(error, errorMessage(error))
        }

        override fun onResults(results: Bundle?) {
            val session = active
            active = null
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            session?.listener?.onResult(text.trim())
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            current()?.listener?.onPartial(text.trim())
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    fun isAvailable(): Boolean = available && recognizer != null

    @MainThread
    fun listen(listener: Listener): Boolean {
        val engine = recognizer ?: return false
        active = null
        val session = Session(++nextId, listener)
        active = session
        engine.setRecognitionListener(bridge)
        engine.startListening(intent)
        return true
    }

    /** Cancela a escuta atual; nenhum callback da sessão antiga será entregue. */
    @MainThread
    fun cancel() {
        active = null
        runCatching { recognizer?.cancel() }
    }

    @MainThread
    fun destroy() {
        active = null
        runCatching {
            recognizer?.setRecognitionListener(null)
            recognizer?.destroy()
        }
    }

    private fun current(): Session? = active

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "erro de áudio"
        SpeechRecognizer.ERROR_CLIENT -> "erro interno do reconhecedor"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "permissão de microfone negada"
        SpeechRecognizer.ERROR_NETWORK -> "rede indisponível"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "tempo limite de rede"
        SpeechRecognizer.ERROR_NO_MATCH -> "nenhuma fala reconhecida"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "reconhecedor ocupado"
        SpeechRecognizer.ERROR_SERVER -> "servidor de voz indisponível"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "nenhuma fala detectada"
        else -> "erro $code do reconhecedor"
    }.let { message -> String.format(Locale.ROOT, "%s (%d)", message, code) }
}
