package com.glia.assistant.voice

import android.content.Context
import com.glia.assistant.core.GliaPrefs
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import java.io.File

/**
 * Treino e verificação da voz do dono, 100% no aparelho.
 *
 * O treino guarda até 8 embeddings (você falando frases curtas). Cada escuta
 * gera outro embedding; só quando a similaridade passa do limite o comando é
 * entregue ao motor determinístico.
 */
class VoicePrintStore(context: Context) {
    private val app = context.applicationContext
    private val profile = File(app.filesDir, VoicePrintLayout.PROFILE_FILE)

    @Volatile
    private var extractor: SpeakerEmbeddingExtractor? = null

    fun isEnrolled(): Boolean = samples().isNotEmpty()

    fun samples(): List<FloatArray> =
        if (!profile.isFile) emptyList()
        else VoicePrintMath.decode(runCatching { profile.readBytes() }.getOrDefault(ByteArray(0)))

    /** Adiciona uma amostra e publica o arquivo de forma atômica. */
    fun addSample(embedding: FloatArray): Boolean {
        val updated = ArrayList(samples())
        if (embedding.isEmpty() || updated.size >= VoicePrintMath.MAX_SAMPLES) return false
        updated.add(embedding)
        return write(updated)
    }

    fun clear() {
        if (profile.exists()) profile.delete()
        GliaPrefs.setVoiceLockEnabled(app, false)
    }

    private fun write(embeddings: List<FloatArray>): Boolean {
        val bytes = VoicePrintMath.encode(embeddings)
        val temporary = File(app.filesDir, VoicePrintLayout.PROFILE_FILE + ".tmp")
        return try {
            temporary.outputStream().use { it.write(bytes) }
            if (profile.exists() && !profile.delete()) return false
            if (!temporary.renameTo(profile)) {
                temporary.copyTo(profile, overwrite = true)
                temporary.delete()
            }
            true
        } catch (_: Exception) {
            temporary.delete()
            false
        }
    }

    /** Gera o embedding de um arquivo PCM16 mono a 16 kHz. */
    fun embeddingFromPcm(pcm: File): FloatArray? {
        val model = VoicePrintModelStore.ensureModel(app) ?: return null
        val engine = extractorFor(model) ?: return null
        val bytes = runCatching { pcm.readBytes() }.getOrNull() ?: return null
        if (bytes.size < 2) return null
        val samples = FloatArray(bytes.size / 2) { i ->
            val value = bytes[i * 2].toInt() or (bytes[i * 2 + 1].toInt() shl 8)
            value.toShort().toFloat() / 32768f
        }
        val stream = engine.createStream()
        return try {
            var offset = 0
            val chunk = 1600 // 100 ms
            while (offset < samples.size) {
                val length = minOf(chunk, samples.size - offset)
                stream.acceptWaveform(samples.copyOfRange(offset, offset + length), VoicePrintLayout.SAMPLE_RATE)
                offset += length
            }
            stream.inputFinished()
            if (engine.isReady(stream)) engine.compute(stream) else null
        } catch (_: Exception) {
            null
        } finally {
            runCatching { stream.release() }
        }
    }

    /**
     * Verificação usada pelo serviço. Falha interna do modelo não é tratada
     * como "voz estranha": o serviço recebe UNAVAILABLE e decide se continua
     * sem filtro, para não deixar a assistente muda por um erro técnico.
     */
    fun verify(pcm: File): VoiceCheck {
        if (!GliaPrefs.voiceLockEnabled(app) || !isEnrolled()) return VoiceCheck.SKIPPED
        val sample = embeddingFromPcm(pcm) ?: return VoiceCheck.UNAVAILABLE
        val reference = VoicePrintMath.average(samples()) ?: return VoiceCheck.UNAVAILABLE
        return if (VoicePrintMath.isOwner(reference, sample, GliaPrefs.voiceThreshold(app))) {
            VoiceCheck.ALLOW
        } else {
            VoiceCheck.DENY
        }
    }

    @Synchronized
    private fun extractorFor(model: File): SpeakerEmbeddingExtractor? {
        val current = extractor
        if (current != null) return current
        return try {
            val created = SpeakerEmbeddingExtractor(
                config = SpeakerEmbeddingExtractorConfig(
                    model = model.absolutePath,
                    numThreads = 1,
                    debug = false,
                    provider = "cpu"
                )
            )
            extractor = created
            created
        } catch (_: Exception) {
            null
        }
    }
}
