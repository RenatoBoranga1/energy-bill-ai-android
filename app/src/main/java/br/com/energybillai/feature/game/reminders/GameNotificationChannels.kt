package br.com.energybillai.feature.game.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object GameNotificationChannels {
    const val ENERGY_GAME_CHANNEL_ID = "energy_game_updates"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            ENERGY_GAME_CHANNEL_ID,
            "Desafios de energia",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Lembretes semanais e avisos do Energy Game."
        }
        manager.createNotificationChannel(channel)
    }
}
