package com.glia.assistant.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.glia.assistant.commands.CognitiveNote
import com.glia.assistant.commands.CognitiveNotesStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class NotesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GliaTheme { NotesScreen(this) } }
    }
}

@Composable
fun NotesScreen(activity: NotesActivity) {
    val store = remember { CognitiveNotesStore(activity) }
    var notes by remember { mutableStateOf(store.all()) }
    val formatter = remember {
        DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
    }

    fun reload() {
        notes = store.all()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(GliaAmbientModifier())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Pensamentos",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Descarregamento cognitivo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Voltar",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { activity.finish() }
            )
        }

        Spacer(Modifier.height(16.dp))

        if (notes.isEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Nenhum pensamento guardado ainda. Diga \"quero diminuir a carga cognitiva\" e fale o que está na sua mente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                )
            }
        } else {
            notes.forEach { note ->
                NoteCard(note = note, formatter = formatter, onDelete = {
                    store.delete(note.id)
                    reload()
                })
                Spacer(Modifier.height(12.dp))
            }
            TextButton(
                onClick = {
                    store.clear()
                    reload()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Limpar todos os pensamentos", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun NoteCard(
    note: CognitiveNote,
    formatter: DateTimeFormatter,
    onDelete: () -> Unit
) {
    val stamp = remember(note.createdAtMs) {
        Instant.ofEpochMilli(note.createdAtMs)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
    }
    GlassCard(modifier = Modifier.fillMaxWidth(), highlighted = true) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Apagar",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable(onClick = onDelete)
                )
            }
            Text(
                note.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
