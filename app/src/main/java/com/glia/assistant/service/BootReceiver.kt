package com.glia.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * O Glia NAO pode iniciar sozinho na inicializacao do aparelho:
 * a escuta so existe quando o usuario liga o tile na central de controle
 * (Quick Settings) e depois usa a palavra-chave "Glia".
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Sem auto-start: o fluxo de ativacao e exclusivamente via tile + palavra-chave.
    }
}
