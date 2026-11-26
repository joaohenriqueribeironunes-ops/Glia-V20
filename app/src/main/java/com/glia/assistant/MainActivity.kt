package com.glia.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.glia.assistant.service.GliaVoiceService
import com.glia.assistant.ui.GliaMode
import com.glia.assistant.ui.GliaState
import com.glia.assistant.ui.GliaTheme
import com.glia.assistant.ui.SettingsActivity

class MainActivity : ComponentActivity() {
    companion object { private const val EXTRA_ACTION = "glia_action" }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) startGlia()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GliaTheme { MainScreen(this) } }
        val action = intent?.getStringExtra(EXTRA_ACTION)
        if (action != null) {
            startGlia(normalizeAction(action))
        } else {
            requestPerms()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val action = intent.getStringExtra(EXTRA_ACTION)
        if (action != null) startGlia(normalizeAction(action))
    }

    private fun normalizeAction(raw: String): String = when (raw) {
        "START_LISTENING" -> GliaVoiceService.ACTION_START_LISTENING
        "STOP" -> GliaVoiceService.ACTION_STOP
        "TEST_TTS" -> GliaVoiceService.ACTION_TEST_TTS
        "TEST_LLM" -> GliaVoiceService.ACTION_TEST_LLM
        "RELOAD_MODEL" -> GliaVoiceService.ACTION_RELOAD_MODEL
        else -> raw
    }

    fun startGlia(action: String? = null) {
        val intent = Intent(this, GliaVoiceService::class.java)
        if (action != null) intent.action = action
        ContextCompat.startForegroundService(this, intent)
    }

    private fun requestPerms() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startGlia() else permLauncher.launch(missing.toTypedArray())
    }
}

@Composable
fun MainScreen(activity: MainActivity) {
    val mode by GliaState.mode.collectAsState()
    val amp by GliaState.amplitude.collectAsState()
    val partial by GliaState.partialText.collectAsState()
    val response by GliaState.responseText.collectAsState()
    val modelStatus by GliaState.modelStatus.collectAsState()
    val ttsStatus by GliaState.ttsStatus.collectAsState()
    val sttStatus by GliaState.sttStatus.collectAsState()
    val lastError by GliaState.lastError.collectAsState()
    val generationStats by GliaState.generationStats.collectAsState()

    val infinite = rememberInfiniteTransition(label = "pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulse"
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            "Glia",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Assistente pessoal offline",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(260.dp),
            contentAlignment = Alignment.Center
        ) {
            OrbView(amplitude = amp, pulse = pulse, mode = mode)
        }
        Text(
            text = when (mode) {
                GliaMode.IDLE -> "Dormindo - diga \"Glia\""
                GliaMode.LISTENING -> "Ouvindo..."
                GliaMode.THINKING -> "Pensando..."
                GliaMode.SPEAKING -> "Falando..."
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Ative o tile Glia na central de controle para ouvir a palavra-chave.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (partial.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                partial,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (response.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                response,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
            )
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                StatusCard(modelStatus, ttsStatus, sttStatus, lastError, generationStats)
            }
        }
        Spacer(Modifier.height(12.dp))
        RowButtons(activity)
    }
}

@Composable
fun StatusCard(
    modelStatus: String,
    ttsStatus: String,
    sttStatus: String,
    lastError: String,
    generationStats: String
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Modelo: $modelStatus", style = MaterialTheme.typography.bodySmall)
        Text("Voz: $ttsStatus", style = MaterialTheme.typography.bodySmall)
        Text("Microfone: $sttStatus", style = MaterialTheme.typography.bodySmall)
        if (generationStats.isNotBlank()) {
            Text("Geracao: $generationStats", style = MaterialTheme.typography.bodySmall)
        }
        if (lastError.isNotBlank()) {
            Text(
                "Erro: $lastError",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun RowButtons(activity: MainActivity) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = { activity.startGlia(GliaVoiceService.ACTION_START_LISTENING) }) {
            Text("Iniciar escuta")
        }
        Button(onClick = { activity.startGlia(GliaVoiceService.ACTION_TEST_TTS) }) {
            Text("Testar voz")
        }
        Button(onClick = { activity.startGlia(GliaVoiceService.ACTION_TEST_LLM) }) {
            Text("Testar modelo")
        }
        Button(onClick = { activity.startGlia(GliaVoiceService.ACTION_RELOAD_MODEL) }) {
            Text("Recarregar modelo")
        }
        Button(onClick = { activity.startGlia(GliaVoiceService.ACTION_STOP) }) {
            Text("Parar")
        }
        Button(onClick = {
            activity.startActivity(Intent(activity, SettingsActivity::class.java))
        }) {
            Text("Configuracoes")
        }
    }
}

@Composable
fun OrbView(amplitude: Float, pulse: Float, mode: GliaMode) {
    val colorCore = when (mode) {
        GliaMode.LISTENING -> Color(0xFF4FD1C5)
        GliaMode.THINKING -> Color(0xFFA78BFA)
        GliaMode.SPEAKING -> Color(0xFF7C6CFF)
        GliaMode.IDLE -> Color(0xFF6D28D9)
    }
    Canvas(Modifier.fillMaxSize(0.6f)) {
        val center = Offset(size.width / 2, size.height / 2)
        val baseRadius = minOf(size.width, size.height) / 3
        val ampScale = 1f + (amplitude.coerceIn(0f, 1f) * 0.6f)
        val radius = baseRadius * pulse * ampScale
        drawCircle(
            brush = Brush.radialGradient(
                listOf(colorCore.copy(alpha = 0.35f), Color.Transparent)
            ),
            radius = radius * 1.6f,
            center = center
        )
        drawCircle(color = colorCore, radius = radius, center = center)
        drawCircle(color = Color.White.copy(alpha = 0.25f), radius = radius * 0.35f, center = center)
    }
}
