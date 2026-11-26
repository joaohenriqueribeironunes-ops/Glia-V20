package com.glia.assistant.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.glia.assistant.llm.ModelStore
import com.glia.assistant.service.GliaVoiceService
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GliaTheme { SettingsScreen() } }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun PrefSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    format: (Float) -> String = { "%.2f".format(it) },
    onChange: (Float) -> Unit
) {
    Text("$label: ${format(value)}", style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = valueRange,
        steps = steps
    )
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var modelPath by remember { mutableStateOf(GliaPrefs.modelPath(ctx) ?: "") }
    var temperature by remember { mutableFloatStateOf(GliaPrefs.temperature(ctx)) }
    var topP by remember { mutableFloatStateOf(GliaPrefs.topP(ctx)) }
    var topK by remember { mutableIntStateOf(GliaPrefs.topK(ctx)) }
    var minP by remember { mutableFloatStateOf(GliaPrefs.minP(ctx)) }
    var presence by remember { mutableFloatStateOf(GliaPrefs.presencePenalty(ctx)) }
    var repeat by remember { mutableFloatStateOf(GliaPrefs.repeatPenalty(ctx)) }
    var maxTokens by remember { mutableIntStateOf(GliaPrefs.maxTokens(ctx)) }
    var contextSize by remember { mutableIntStateOf(GliaPrefs.contextSize(ctx)) }
    var threads by remember { mutableIntStateOf(GliaPrefs.threads(ctx)) }
    var status by remember { mutableStateOf("") }

    fun reloadModel() {
        ContextCompat.startForegroundService(
            ctx,
            Intent(ctx, GliaVoiceService::class.java)
                .setAction(GliaVoiceService.ACTION_RELOAD_MODEL)
        )
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            scope.launch {
                status = "Copiando modelo (pode demorar)..."
                try {
                    val out = withContext(Dispatchers.IO) {
                        val part = File(ctx.filesDir, "user-model.gguf.part")
                        val target = ModelStore.userModelFile(ctx)
                        ctx.contentResolver.openInputStream(it)?.use { input ->
                            part.outputStream().use { output -> input.copyTo(output) }
                        } ?: throw IllegalStateException("Nao foi possivel abrir o arquivo")
                        if (!ModelStore.isValid(part)) {
                            part.delete()
                            throw IllegalStateException("O arquivo selecionado nao e um GGUF valido")
                        }
                        if (target.exists()) target.delete()
                        if (!part.renameTo(target)) {
                            part.copyTo(target, overwrite = true)
                            part.delete()
                        }
                        target
                    }
                    modelPath = out.absolutePath
                    GliaPrefs.setModelPath(ctx, out.absolutePath)
                    status = "Modelo importado: ${out.length() / (1024 * 1024)} MB"
                    reloadModel()
                } catch (e: Exception) {
                    status = "Erro ao importar: ${e.message}"
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Configuracoes - Glia", style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary)

        SectionCard {
            Text("Modelo local", style = MaterialTheme.typography.titleMedium)
            Text(
                "Importe o arquivo Ternary Bonsai 2 4B (Q2_0, .gguf). " +
                    "O modelo NAO vem no APK e fica apenas neste dispositivo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (File(modelPath).isFile) "Modelo atual: presente" else "Modelo atual: ausente",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = modelPath,
                onValueChange = { modelPath = it },
                label = { Text("Caminho do modelo (.gguf)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(onClick = { picker.launch(arrayOf("*/*")) }) {
                Text("Importar modelo .gguf")
            }
            Button(onClick = {
                GliaPrefs.setModelPath(ctx, modelPath.trim())
                status = "Caminho salvo. Recarregando modelo..."
                reloadModel()
            }) {
                Text("Salvar caminho e recarregar")
            }
        }

        SectionCard {
            Text("Desempenho", style = MaterialTheme.typography.titleMedium)
            PrefSlider(
                "Tamanho do contexto", contextSize.toFloat(), 512f..4096f, steps = 6,
                format = { "${it.toInt()} tokens" }
            ) { contextSize = it.toInt(); GliaPrefs.setContextSize(ctx, contextSize) }
            PrefSlider(
                "Threads de geracao", threads.toFloat(), 2f..8f, steps = 5,
                format = { it.toInt().toString() }
            ) { threads = it.toInt().coerceIn(2, 8); GliaPrefs.setThreads(ctx, threads) }
            PrefSlider(
                "Maximo de tokens por resposta", maxTokens.toFloat(), 64f..1024f, steps = 5,
                format = { it.toInt().toString() }
            ) { maxTokens = it.toInt(); GliaPrefs.setMaxTokens(ctx, maxTokens) }
        }

        SectionCard {
            Text("Amostragem", style = MaterialTheme.typography.titleMedium)
            PrefSlider("Temperatura", temperature, 0f..1.5f) {
                temperature = it; GliaPrefs.setTemperature(ctx, it)
            }
            PrefSlider("Top-P", topP, 0.05f..0.98f) {
                topP = it; GliaPrefs.setTopP(ctx, it)
            }
            PrefSlider("Top-K", topK.toFloat(), 1f..100f, steps = 9,
                format = { it.toInt().toString() }
            ) { topK = it.toInt(); GliaPrefs.setTopK(ctx, topK) }
            PrefSlider("Min-P", minP, 0f..0.95f) {
                minP = it; GliaPrefs.setMinP(ctx, it)
            }
            PrefSlider("Penalidade de presenca", presence, -2f..2f) {
                presence = it; GliaPrefs.setPresencePenalty(ctx, it)
            }
            PrefSlider("Penalidade de repeticao", repeat, 0.8f..1.5f) {
                repeat = it; GliaPrefs.setRepeatPenalty(ctx, it)
            }
        }

        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)
        }

        SectionCard {
            Text("Permissoes criticas", style = MaterialTheme.typography.titleMedium)
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(ctx)) {
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${ctx.packageName}")
                        )
                    )
                }
            }) {
                Text("Sobreposicao de tela")
            }
            Button(onClick = {
                val i = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${ctx.packageName}")
                )
                ctx.startActivity(i)
            }) {
                Text("Ignorar otimizacao de bateria (MIUI)")
            }
            Button(onClick = {
                val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${ctx.packageName}"))
                ctx.startActivity(i)
            }) {
                Text("Abrir permissoes do app")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
