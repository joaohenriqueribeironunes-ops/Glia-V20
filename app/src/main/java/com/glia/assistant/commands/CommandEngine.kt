package com.glia.assistant.commands

import android.content.Context
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Executa comandos determinísticos, sem LLM. Baixo consumo: apenas APIs do sistema
 * e leitura pontual do calendário/lista de apps quando o comando pede.
 */
class CommandEngine(context: Context) {
    private val appContext = context.applicationContext
    private val flashlight = FlashlightController(appContext)
    private val calendar = CalendarReader(appContext)
    private val appLauncher = AppLauncher(appContext)
    private val notes = CognitiveNotesStore(appContext)

    fun execute(command: VoiceCommand): CommandResult = when (command) {
        VoiceCommand.Time -> CommandResult(timeText())
        VoiceCommand.Date -> CommandResult(dateText())
        is VoiceCommand.Calendar -> {
            val answer = calendar.summary(command.daysAhead)
            CommandResult(answer.spoken, needsCalendarPermission = !answer.hasPermission)
        }
        is VoiceCommand.Flashlight -> flashlightResult(command.turnOn)
        is VoiceCommand.OpenApp -> {
            val launch = appLauncher.launch(command.query)
            CommandResult(launch.spoken, launchedApp = launch.launched)
        }
        VoiceCommand.ReadNotes -> CommandResult(notes.summary(), openNotes = true)
        VoiceCommand.Help -> CommandResult(HELP)
        VoiceCommand.StartCognitiveOffload -> CommandResult("")
        is VoiceCommand.Unknown -> CommandResult(
            "N\u00e3o entendi. Voc\u00ea pode pedir a hora, a data, seus compromissos, " +
                "ligar a lanterna, abrir um aplicativo ou guardar um pensamento."
        )
    }

    fun noteCount(): Int = notes.count()

    private fun flashlightResult(turnOn: Boolean?): CommandResult {
        val state = if (turnOn == null) flashlight.toggle() else if (flashlight.set(turnOn)) turnOn else null
        val spoken = when (state) {
            true -> "Lanterna ligada."
            false -> "Lanterna desligada."
            null -> "N\u00e3o consegui controlar a lanterna neste aparelho."
        }
        return CommandResult(spoken)
    }

    private fun timeText(): String {
        val now = LocalTime.now()
        val hour = if (now.hour == 1) "\u00c9 1 hora" else "S\u00e3o ${now.hour} horas"
        return if (now.minute == 0) "$hour." else "$hour e ${now.minute} minutos."
    }

    private fun dateText(): String {
        val locale = Locale("pt", "BR")
        val text = LocalDate.now().format(
            DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", locale)
        )
        return "Hoje \u00e9 ${text.replaceFirstChar { it.titlecase(locale) }}."
    }

    companion object {
        const val HELP =
            "Eu consigo dizer a hora e a data, consultar seus compromissos, ligar ou desligar " +
                "a lanterna, abrir aplicativos e guardar pensamentos para aliviar a carga cognitiva. " +
                "Diga: quero diminuir a carga cognitiva."
    }
}

data class CommandResult(
    val spoken: String,
    val openNotes: Boolean = false,
    val needsCalendarPermission: Boolean = false,
    val launchedApp: Boolean = false
)
