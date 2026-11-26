package com.glia.assistant

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.glia.assistant.commands.CognitiveNotesStore
import com.glia.assistant.core.GliaPrefs
import com.glia.assistant.service.GliaVoiceService
import com.glia.assistant.ui.GliaAccent
import com.glia.assistant.ui.GliaAmbientModifier
import com.glia.assistant.ui.GliaGlyph
import com.glia.assistant.ui.GliaMode
import com.glia.assistant.ui.GliaRadius
import com.glia.assistant.ui.GliaState
import com.glia.assistant.ui.GliaTheme
import com.glia.assistant.ui.GlassCard
import com.glia.assistant.ui.HyperIsland
import com.glia.assistant.ui.HyperTile
import com.glia.assistant.ui.NotesActivity
import com.glia.assistant.ui.SettingsActivity
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_ACTION = "glia_action"
        const val EXTRA_WAKE = "glia_wake"
    }

    private val tileActive = MutableStateFlow(false)

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) startGlia()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOnLockScreen()
        setContent { GliaTheme { MainScreen(this, tileActive.collectAsState().value) } }
        if (intent?.getBooleanExtra(EXTRA_WAKE, false) == true) dismissKeyguard()
        val action = intent?.getStringExtra(EXTRA_ACTION)
        if (action != null) {
            startGlia(normalizeAction(action))
        } else {
            requestPerms()
        }
    }

    override fun onResume() {
        super.onResume()
        tileActive.value = GliaPrefs.isTileActive(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_WAKE, false)) dismissKeyguard()
        val action = intent.getStringExtra(EXTRA_ACTION)
        if (action != null) startGlia(normalizeAction(action))
    }

    private fun showOnLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun dismissKeyguard() {
        val km = getSystemService(KeyguardManager::class.java)
        km?.requestDismissKeyguard(this, null)
    }

    private fun normalizeAction(raw: String): String = when (raw) {
        "START_LISTENING" -> GliaVoiceService.ACTION_START_LISTENING
        "STOP" -> GliaVoiceService.ACTION_STOP
        "TEST_TTS" -> GliaVoiceService.ACTION_TEST_TTS
        else -> raw
    }

    fun startGlia(action: String? = null) {
        val intent = Intent(this, GliaVoiceService::class.java)
        if (action != null) intent.action = action
        ContextCompat.startForegroundService(this, intent)
    }

    fun toggleTile() {
        val active = !GliaPrefs.isTileActive(this)
        GliaPrefs.setTileActive(this, active)
        tileActive.value = active
        if (active) {
            startGlia()
        } else {
            startGlia(GliaVoiceService.ACTION_STOP)
        }
    }

    fun openNotes() {
        startActivity(Intent(this, NotesActivity::class.java))
    }

    private fun requestPerms() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CALENDAR)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startGlia() else permLauncher.launch(missing.toTypedArray())
    }
}

@Composable
fun MainScreen(activity: MainActivity, tileActive: Boolean) {
    val mode by GliaState.mode.collectAsState()
    val amp by GliaState.amplitude.collectAsState()
    val partial by GliaState.partialText.collectAsState()
    val response by GliaState.responseText.collectAsState()
    val assistantStatus by GliaState.assistantStatus.collectAsState()
    val ttsStatus by GliaState.ttsStatus.collectAsState()
    val sttStatus by GliaState.sttStatus.collectAsState()
    val lastError by GliaState.lastError.collectAsState()
    val noteCount = CognitiveNotesStore(activity).count()

    val infinite = rememberInfiniteTransition(label = "orb")
    val pulse by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
        label = "orbPulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(GliaAmbientModifier())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Glia",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Assistente pessoal offline",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(GliaRadius.sm))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                    .clickable {
                        activity.startActivity(Intent(activity, SettingsActivity::class.java))
                    },
                contentAlignment = Alignment.Center
            ) {
                GliaGlyph(
                    kind = "settings",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        HyperIsland(
            label = when (mode) {
                GliaMode.IDLE -> if (tileActive) "Glia pronta" else "Glia desligada"
                GliaMode.LISTENING -> "Glia ouvindo"
                GliaMode.THINKING -> "Glia pensando"
                GliaMode.SPEAKING -> "Glia falando"
            },
            active = tileActive || mode != GliaMode.IDLE,
            amplitude = amp,
            onClick = { activity.toggleTile() }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp),
            contentAlignment = Alignment.Center
        ) {
            OrbView(amplitude = amp, pulse = pulse, mode = mode, tileActive = tileActive)
        }

        Text(
            text = when (mode) {
                GliaMode.IDLE -> if (tileActive) "Diga \"Olá Glia\"" else "Ative o tile para ouvir"
                GliaMode.LISTENING -> "Ouvindo..."
                GliaMode.THINKING -> "Preparando..."
                GliaMode.SPEAKING -> "Falando..."
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        if (partial.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                partial,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(14.dp))
        GlassCard(modifier = Modifier.fillMaxWidth(), highlighted = mode != GliaMode.IDLE) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusCard(assistantStatus, ttsStatus, sttStatus, lastError.orEmpty())
                if (response.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        response,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "Centro de controle",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        ControlGrid(activity = activity, tileActive = tileActive, noteCount = noteCount)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ControlGrid(activity: MainActivity, tileActive: Boolean, noteCount: Int) {
    val entries = listOf(
        ControlEntry("mic", "Escuta", if (tileActive) "ligada" else "desligada", tileActive, GliaAccent.Purple) {
            activity.toggleTile()
        },
        ControlEntry("chat", "Falar agora", "sem palavra-chave", false, GliaAccent.Teal) {
            activity.startGlia(GliaVoiceService.ACTION_START_LISTENING)
        },
        ControlEntry("spark", "Testar voz", "TTS PT-BR", false, GliaAccent.Coral) {
            activity.startGlia(GliaVoiceService.ACTION_TEST_TTS)
        },
        ControlEntry("brain", "Pensamentos", "$noteCount guardados", false, GliaAccent.Lavender) {
            activity.openNotes()
        },
        ControlEntry("stop", "Parar", "libera microfone", false, GliaAccent.Coral) {
            activity.startGlia(GliaVoiceService.ACTION_STOP)
        }
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        entries.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { entry ->
                    HyperTile(
                        icon = {
                            GliaGlyph(
                                kind = entry.glyph,
                                tint = if (entry.active) Color.White else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = entry.label,
                        caption = entry.caption,
                        active = entry.active,
                        accent = entry.accent,
                        onClick = entry.onClick,
                        modifier = with(this@Row) { Modifier.weight(1f) }
                    )
                }
            }
        }
        HyperTile(
            icon = {
                GliaGlyph(
                    kind = "settings",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            },
            label = "Configurações",
            caption = "voz, permissões, comandos",
            active = false,
            accent = GliaAccent.Purple,
            onClick = { activity.startActivity(Intent(activity, SettingsActivity::class.java)) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private data class ControlEntry(
    val glyph: String,
    val label: String,
    val caption: String,
    val active: Boolean,
    val accent: Color,
    val onClick: () -> Unit
)

@Composable
fun StatusCard(
    assistantStatus: String,
    ttsStatus: String,
    sttStatus: String,
    lastError: String
) {
    StatusLine("Assistente", assistantStatus, GliaAccent.Purple)
    StatusLine("Voz", ttsStatus, GliaAccent.Teal)
    StatusLine("Microfone", sttStatus, GliaAccent.Lavender)
    if (lastError.isNotBlank()) {
        Text(
            "Erro: $lastError",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun StatusLine(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "$label: $value",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun OrbView(amplitude: Float, pulse: Float, mode: GliaMode, tileActive: Boolean) {
    val core = when (mode) {
        GliaMode.LISTENING -> GliaAccent.Teal
        GliaMode.THINKING -> GliaAccent.Lavender
        GliaMode.SPEAKING -> GliaAccent.Purple
        GliaMode.IDLE -> if (tileActive) GliaAccent.PurpleDeep else Color(0xFF6E6A80)
    }
    Canvas(modifier = Modifier.fillMaxSize(0.86f)) {
        val center = Offset(size.width / 2, size.height / 2)
        val base = minOf(size.width, size.height) / 2.9f
        val scale = 1f + amplitude.coerceIn(0f, 1f) * 0.45f
        val radius = base * pulse * scale
        drawCircle(
            brush = Brush.radialGradient(
                listOf(core.copy(alpha = 0.30f), Color.Transparent),
                center = center,
                radius = radius * 1.9f
            ),
            radius = radius * 1.9f,
            center = center
        )
        drawCircle(color = core.copy(alpha = 0.20f), radius = radius * 1.28f, center = center)
        drawCircle(color = core, radius = radius, center = center)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
                center = Offset(center.x - radius * 0.25f, center.y - radius * 0.25f),
                radius = radius
            ),
            radius = radius,
            center = center
        )
        drawCircle(color = Color.White.copy(alpha = 0.22f), radius = radius * 0.26f, center = center)
    }
}
