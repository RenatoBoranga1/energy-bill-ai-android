package br.com.energybillai.domain.game.usecase

import br.com.energybillai.domain.game.repository.GameReminderRepository
import javax.inject.Inject

class EnsureGameRemindersUseCase @Inject constructor(
    private val repository: GameReminderRepository,
) {
    suspend operator fun invoke() {
        repository.ensureScheduled()
    }
}

class CancelGameRemindersUseCase @Inject constructor(
    private val repository: GameReminderRepository,
) {
    suspend operator fun invoke() {
        repository.cancelAll()
    }
}
