package br.com.energybillai

import android.app.Application
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import br.com.energybillai.feature.game.reminders.GameNotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class EnergyBillApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        GameNotificationChannels.ensureCreated(this)
    }
}
