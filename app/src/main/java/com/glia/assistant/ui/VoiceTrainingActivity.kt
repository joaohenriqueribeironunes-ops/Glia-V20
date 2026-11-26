package com.glia.assistant.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.glia.assistant.core.GliaPrefs
import com.glia.assistant.stt.AmplifiedMic
import com.glia.assistant.voice.VoicePrintLayout
import com.glia.assistant.voice.VoicePrintMath
import com.glia.assistant.voice.VoicePrintStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Fluxo de treino: três amostras da voz do dono, processadas no aparelho. */
class VoiceTrainingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GliaTheme { VoiceTrainingScreen(this) } }
    }
}

@Composable
fun VoiceTrainingScreen(activity: VoiceTrainingActivity) {
    val ctx = LocalContext.current
    val store = remember { VoicePrintStore(ctx) }
    val scope = rememberCoroutineScope()
    val assistantStatus by GliaState.assistantStatus.collectAsState()

    var samples by remember { mutableIntStateOf(store.samples().size) }
    var busy by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf(
            if (store.isEnrolled()) "Voz já treinada. Refaça se quiser substituir as amostras."
            else "Grave as três frases em um lugar silencioso."
        )
    }
    var enabled by remember { mutableStateOf(GliaPrefs.voiceLockEnabled(ctx)) }
    var threshold by remember { mutableFloatStateOf(GliaPrefs.voiceThreshold(ctx)) }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        status = if (granted) "Microfone permitido. Toque em gravar." else "Permissão de microfone negada."
    }

    fun recordSample() {
        if (busy || samples >= VoicePrintLayout.TRAINING_PHRASES.size) return
        if (ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        busy = true
        status = "Ouvindo... diga a frase em voz alta."
        scope.launch {
            val stored = withContext(Dispatchers.IO) {
                var source = runCatching { AmplifiedMic(ctx).capture(8_000L, 1f) }.getOrNull()
                try {
                    val audio = source ?: return@withContext false
                    val embedding = store.embeddingFromPcm(audio.file) ?: return@withContext false
                    store.addSample(embedding)
                } catch (_: Exception) {
                    false
                } finally {
                    source?.close()
                    source = null
                }
            }
            busy = false
            samples = store.samples().size
            if (stored && samples >= VoicePrintLayout.TRAINING_PHRASES.size) {
                GliaPrefs.setVoiceLockEnabled(ctx, true)
                enabled = true
                status = "Treino concluído. A Glia vai responder somente à sua voz."
            } else if (stored) {
                status = "Amostra $samples de ${VoicePrintLayout.TRAINING_PHRASES.size} guardada."
            } else {
                status = "Não foi possível extrair a voz. Tente novamente mais perto do microfone."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(GliaAmbientModifier())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Treinar minha voz", style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary)
        Text("Assistente: $assistantStatus", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "O modelo 3D-Speaker roda no aparelho. Nenhuma amostra sai do celular.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(status, style = MaterialTheme.typography.bodyMedium)

        SettingsCard("Amostras", "Diga cada frase como se estivesse chamando a Glia.") {
            VoicePrintLayout.TRAINING_PHRASES.forEachIndexed { index, phrase ->
                Text(
                    "${index + 1}. $phrase",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (index < samples) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { recordSample() },
                enabled = !busy && samples < VoicePrintLayout.TRAINING_PHRASES.size,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (busy) "Gravando..." else "Gravar amostra ${samples + 1}/${VoicePrintLayout.TRAINING_PHRASES.size}"
                )
            }
        }

        SettingsCard("Somente minha voz", "Exige similaridade com as amostras treinadas.") {
            HyperTile(
                icon = { GliaGlyph("mic", MaterialTheme.colorScheme.primary, Modifier) },
                label = if (enabled) "Ativo — somente minha voz" else "Desativado",
                caption = if (samples >= VoicePrintLayout.TRAINING_PHRASES.size)
                    "toque para ${if (enabled) "desativar" else "ativar"}"
                else "faça o treino para ativar",
                active = enabled,
                accent = GliaAccent.Teal,
                onClick = {
                    if (samples >= VoicePrintLayout.TRAINING_PHRASES.size) {
                        val next = !enabled
                        GliaPrefs.setVoiceLockEnabled(ctx, next)
                        enabled = next
                    } else {
                        status = "Grave as três amostras antes de ativar."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Rigor: ${String.format(Locale.ROOT, "%.2f", threshold)}",
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value = threshold,
                onValueChange = {
                    threshold = VoicePrintMath.clampThreshold(it)
                    GliaPrefs.setVoiceThreshold(ctx, threshold)
                },
                valueRange = VoicePrintMath.MIN_THRESHOLD..VoicePrintMath.MAX_THRESHOLD,
                steps = 8,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Mais rigor reduz falsos positivos, mas pode exigir falar mais perto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = {
                store.clear()
                samples = 0
                enabled = false
                threshold = VoicePrintMath.DEFAULT_THRESHOLD
                GliaPrefs.setVoiceThreshold(ctx, threshold)
                status = "Treino apagado."
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Apagar treino") }

        Button(onClick = { activity.finish() }, modifier = Modifier.fillMaxWidth()) {
            Text("Voltar")
        }
    }
}
