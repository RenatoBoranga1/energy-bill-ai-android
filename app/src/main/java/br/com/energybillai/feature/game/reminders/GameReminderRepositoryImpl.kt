package br.com.energybillai.feature.game.reminders

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.energybillai.R
import br.com.energybillai.data.local.SessionStore
import br.com.energybillai.data.local.game.GameReminderStore
import br.com.energybillai.domain.game.repository.GameReminderRepository
import br.com.energybillai.domain.game.repository.GameRepository
import br.com.energybillai.domain.game.repository.MeterReadingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private const val ENERGY_GAME_REMINDER_WORK = "energy_game_reminder_work"

@Singleton
class DefaultGameReminderRepository @Inject constructor(
    private val workManager: WorkManager,
) : GameReminderRepository {
    override suspend fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<GameReminderWorker>(1, TimeUnit.DAYS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            ENERGY_GAME_REMINDER_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    override suspend fun cancelAll() {
        workManager.cancelUniqueWork(ENERGY_GAME_REMINDER_WORK)
    }
}

@HiltWorker
class GameReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionStore: SessionStore,
    private val meterReadingRepository: MeterReadingRepository,
    private val gameRepository: GameRepository,
    private val reminderStore: GameReminderStore,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        GameNotificationChannels.ensureCreated(applicationContext)

        val session = sessionStore.currentSession() ?: return Result.success()
        val userId = session.user.id
        val today = LocalDate.now()
        val reminderSnapshot = reminderStore.snapshot()

        val latestReading = meterReadingRepository.getLatestConfirmedReading(userId)
        val latestReadingDate = latestReading?.readingDate?.let(::parseDate)
        val weeklyReminderDue = latestReadingDate == null || ChronoUnit.DAYS.between(latestReadingDate, today) >= 7

        if (
            weeklyReminderDue &&
            reminderSnapshot.lastWeeklyReminderDate != today.toString()
        ) {
            notifyUser(
                notificationId = 4001,
                title = "Hora da leitura semanal",
                message = "Registre o medidor desta semana para manter seus desafios atualizados.",
            )
            reminderStore.markWeeklyReminder(today.toString())
        }

        val activeChallenge = gameRepository.observeActiveChallenge(userId).first()
        val nearingCompletion = activeChallenge != null &&
            activeChallenge.progressPercent in 70.0..99.9

        if (
            nearingCompletion &&
            reminderSnapshot.lastChallengeReminderDate != today.toString() &&
            reminderSnapshot.lastChallengeReminderId != activeChallenge?.id
        ) {
            val challengeId = activeChallenge?.id ?: return Result.success()
            notifyUser(
                notificationId = 4002,
                title = "Voce esta perto de concluir a meta",
                message = "Seu desafio esta quase completo. Uma nova leitura pode confirmar a economia desta semana.",
            )
            reminderStore.markChallengeReminder(
                date = today.toString(),
                challengeId = challengeId,
            )
        }

        return Result.success()
    }

    private fun notifyUser(
        notificationId: Int,
        title: String,
        message: String,
    ) {
        if (
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = ContextCompat.getSystemService(
            applicationContext,
            NotificationManager::class.java,
        ) ?: return

        val notification = NotificationCompat.Builder(
            applicationContext,
            GameNotificationChannels.ENERGY_GAME_CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_brand_mark_monochrome)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify(notificationId, notification)
    }

    private fun parseDate(rawValue: String): LocalDate? {
        return runCatching { LocalDate.parse(rawValue.take(10)) }.getOrNull()
    }
}
