package com.glia.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.glia.assistant.BuildConfig
import com.glia.assistant.commands.CognitiveNotesStore
import com.glia.assistant.core.GliaPrefs
import com.glia.assistant.service.GliaVoiceService
import com.glia.assistant.stt.MicGain
import java.util.Locale

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GliaTheme { SettingsScreen(this) } }
    }
}

@Composable
fun SettingsScreen(activity: SettingsActivity) {
    val ctx = LocalContext.current
    val ttsStatus by GliaState.ttsStatus.collectAsState()
    val sttStatus by GliaState.sttStatus.collectAsState()
    val assistantStatus by GliaState.assistantStatus.collectAsState()
    val notes = CognitiveNotesStore(ctx)
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val calendarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun startService(action: String) {
        val intent = Intent(ctx, GliaVoiceService::class.java).setAction(action)
        ContextCompat.startForegroundService(ctx, intent)
    }

    fun openSystem(action: String, data: Uri? = null) {
        val intent = Intent(action)
        if (data != null) intent.data = data
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(GliaAmbientModifier())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Configurações",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Glia ${BuildConfig.VERSION_NAME} · voz neural local",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SettingsCard("Voz neural", "Voz pt-BR local, sem nuvem e com baixa carga de CPU.") {
            Text("Status: $ttsStatus", style = MaterialTheme.typography.bodySmall)
            Text("Microfone: $sttStatus", style = MaterialTheme.typography.bodySmall)
            Text("Assistente: $assistantStatus", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            HyperTile(
                icon = { GliaGlyph("spark", MaterialTheme.colorScheme.primary, Modifier.size(18.dp)) },
                label = "Testar voz",
                caption = "ouça a voz neural feminina",
                active = false,
                accent = GliaAccent.Teal,
                onClick = { startService(GliaVoiceService.ACTION_TEST_TTS) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingsCard("Ativação", "Tile na central de controle e palavra-chave \"Glia\".") {
            Text(
                "1. Abra a central de controle e adicione o tile Glia.\n" +
                    "2. Toque no tile para ligar a escuta.\n" +
                    "3. Diga apenas \"Glia\" mesmo com a tela bloqueada.\n" +
                    "4. Permita sobreposição e ignore a otimização de bateria para o HyperOS não encerrar a escuta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            HyperTile(
                icon = { GliaGlyph("settings", MaterialTheme.colorScheme.primary, Modifier.size(18.dp)) },
                label = "Permitir sobreposição",
                caption = "necessário para acordar sobre a tela bloqueada",
                active = false,
                accent = GliaAccent.Purple,
                onClick = {
                    openSystem(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${ctx.packageName}")
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            HyperTile(
                icon = { GliaGlyph("reload", MaterialTheme.colorScheme.primary, Modifier.size(18.dp)) },
                label = "Ignorar otimização de bateria",
                caption = "mantém a palavra-chave ativa",
                active = false,
                accent = GliaAccent.Lavender,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        openSystem(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${ctx.packageName}")
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingsCard("Permissões", "Microfone e calendário são usados somente no aparelho.") {
            val micGranted = ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            val calendarGranted = ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
            Text(
                "Microfone: ${if (micGranted) "permitido" else "pendente"}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Calendário: ${if (calendarGranted) "permitido" else "pendente"}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            HyperTile(
                icon = { GliaGlyph("mic", MaterialTheme.colorScheme.primary, Modifier.size(18.dp)) },
                label = "Permitir microfone",
                caption = "escuta da palavra-chave e comandos",
                active = micGranted,
                accent = GliaAccent.Purple,
                onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            HyperTile(
                icon = { GliaGlyph("brain", MaterialTheme.colorScheme.primary, Modifier.size(18.dp)) },
                label = "Permitir calendário",
                caption = "consultar compromissos",
                active = calendarGranted,
                accent = GliaAccent.Teal,
                onClick = { calendarLauncher.launch(Manifest.permission.READ_CALENDAR) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingsCard("Microfone", "Ganho para alcançar falas mais distantes.") {
            var gain by remember { mutableFloatStateOf(GliaPrefs.micGain(ctx)) }
            Text(
                "Ganho: ${String.format(Locale.ROOT, "%.1f", gain)}x",
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value = gain,
                onValueChange = { value ->
                    gain = MicGain.clamp(value)
                    GliaPrefs.setMicGain(ctx, gain)
                },
                valueRange = MicGain.MIN..MicGain.MAX,
                steps = 6,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Mais ganho aumenta o alcance, mas também amplifica o ruído do ambiente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsCard("Somente minha voz", "Treino local para responder apenas à sua voz.") {
            val voiceStore = remember { com.glia.assistant.voice.VoicePrintStore(ctx) }
            val enrolled = voiceStore.isEnrolled()
            val locked = GliaPrefs.voiceLockEnabled(ctx)
            Text(
                "Treino: ${if (enrolled) "${voiceStore.samples().size} amostras" else "não treinado"} · " +
                    "Filtro: ${if (locked) "ativo" else "inativo"}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))
            HyperTile(
                icon = { GliaGlyph("mic", MaterialTheme.colorScheme.primary, Modifier.size(18.dp)) },
                label = "Treinar minha voz",
                caption = "3 amostras · modelo local 3D-Speaker",
                active = locked,
                accent = GliaAccent.Lavender,
                onClick = { activity.startActivity(Intent(ctx, VoiceTrainingActivity::class.java)) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingsCard("Pensamentos", "Bloco de notas do descarregamento cognitivo.") {
            Text(
                "Você tem ${notes.count()} pensamentos guardados.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))
            HyperTile(
                icon = { GliaGlyph("brain", MaterialTheme.colorScheme.primary, Modifier.size(18.dp)) },
                label = "Abrir pensamentos",
                caption = "notas estilizadas no aplicativo",
                active = false,
                accent = GliaAccent.Lavender,
                onClick = { activity.startActivity(Intent(ctx, NotesActivity::class.java)) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingsCard("Comandos de voz", "Diga \"Glia\" e fale o comando — ou fale tudo na mesma frase.") {
            Text(
                "• Que horas são?\n" +
                    "• Que dia é hoje?\n" +
                    "• Tenho compromissos hoje/amanhã/esta semana?\n" +
                    "• Ligar a lanterna / desligar a lanterna\n" +
                    "• Abra o WhatsApp / abra a câmera / abra o ...\n" +
                    "• Quero diminuir a carga cognitiva\n" +
                    "• Quais são meus pensamentos?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsCard("Sobre", "Sem LLM. Voz neural feminina no aparelho.") {
            Text(
                "A Glia usa comandos determinísticos e a voz neural pt-BR feminina Dii direto no aparelho. O reconhecimento de fala usa o motor do sistema, com preferência por processamento offline quando disponível.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun SettingsCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}
