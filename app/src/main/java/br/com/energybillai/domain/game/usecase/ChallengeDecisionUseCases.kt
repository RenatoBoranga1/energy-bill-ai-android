package br.com.energybillai.domain.game.usecase

import br.com.energybillai.domain.game.model.EnergyChallenge
import java.time.LocalDate
import javax.inject.Inject

class AcceptSuggestedChallengeUseCase @Inject constructor(
    private val gameEngine: GameEngine,
) {
    suspend operator fun invoke(
        userId: String,
        challengeId: String,
        today: LocalDate = LocalDate.now(),
    ): EnergyChallenge? {
        return gameEngine.acceptSuggestedChallenge(
            userId = userId,
            challengeId = challengeId,
            today = today,
        )
    }
}

class DeclineSuggestedChallengeUseCase @Inject constructor(
    private val gameEngine: GameEngine,
) {
    suspend operator fun invoke(
        userId: String,
        challengeId: String,
    ) {
        gameEngine.declineSuggestedChallenge(
            userId = userId,
            challengeId = challengeId,
        )
    }
}
