package com.glia.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.glia.assistant.service.GliaVoiceService

class GliaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                GliaVoiceService.CHANNEL_ID,
                "Glia Voice",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Servico de voz da Glia em segundo plano" }
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }
        // A extracao do Vosk embutido ocorre em background no GliaVoiceService.
    }
}