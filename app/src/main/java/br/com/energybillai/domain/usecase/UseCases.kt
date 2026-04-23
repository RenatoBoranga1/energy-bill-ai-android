package br.com.energybillai.domain.usecase

import br.com.energybillai.core.common.AppResult
import br.com.energybillai.domain.model.AuthSession
import br.com.energybillai.domain.model.BillAnalytics
import br.com.energybillai.domain.model.BillForecast
import br.com.energybillai.domain.model.BillReview
import br.com.energybillai.domain.model.BillSummary
import br.com.energybillai.domain.model.EnergyUser
import br.com.energybillai.domain.model.ReviewedBillData
import br.com.energybillai.domain.model.UploadPayload
import br.com.energybillai.domain.model.UploadedDocument
import br.com.energybillai.domain.repository.AuthRepository
import br.com.energybillai.domain.repository.BillRepository
import br.com.energybillai.domain.repository.SessionRepository
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    operator fun invoke(): Flow<AuthSession?> = sessionRepository.observeSession()
}

class GetCurrentSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    operator fun invoke(): AuthSession? = sessionRepository.currentSession()
}

class BootstrapSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(nowEpochSeconds: Long = Instant.now().epochSecond): AppResult<Unit> {
        val session = sessionRepository.currentSession() ?: return AppResult.Success(Unit)
        return when {
            session.tokens.isAccessTokenValid(nowEpochSeconds) -> AppResult.Success(Unit)
            !session.tokens.canRefresh(nowEpochSeconds) -> {
                sessionRepository.clearSession()
                AppResult.Success(Unit)
            }
            else -> when (authRepository.refresh(session.tokens.refreshToken)) {
                is AppResult.Success -> AppResult.Success(Unit)
                is AppResult.Error -> {
                    sessionRepository.clearSession()
                    AppResult.Success(Unit)
                }
            }
        }
    }
}

class RegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(name: String, email: String, password: String): AppResult<AuthSession> {
        return authRepository.register(name, email, password)
    }
}

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String): AppResult<AuthSession> {
        return authRepository.login(email, password)
    }
}

class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(refreshToken: String?): AppResult<Unit> = authRepository.logout(refreshToken)
}

class MeUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): AppResult<EnergyUser> = authRepository.me()
}

class ObserveHistoryUseCase @Inject constructor(
    private val billRepository: BillRepository,
) {
    operator fun invoke(userId: String): Flow<List<BillSummary>> = billRepository.observeHistory(userId)
}

class RefreshHistoryUseCase @Inject constructor(
    private val billRepository: BillRepository,
) {
    suspend operator fun invoke(userId: String): AppResult<List<BillSummary>> = billRepository.refreshHistory(userId)
}

class UploadDocumentUseCase @Inject constructor(
    private val billRepository: BillRepository,
) {
    suspend operator fun invoke(payload: UploadPayload): AppResult<UploadedDocument> = billRepository.uploadDocument(payload)
}

class ExtractBillUseCase @Inject constructor(
    private val billRepository: BillRepository,
) {
    suspend operator fun invoke(documentId: String): AppResult<BillReview> = billRepository.extractBill(documentId)
}

class GetBillUseCase @Inject constructor(
    private val billRepository: BillRepository,
) {
    suspend operator fun invoke(billId: String): AppResult<BillReview> = billRepository.getBill(billId)
}

class ConfirmBillUseCase @Inject constructor(
    private val billRepository: BillRepository,
) {
    suspend operator fun invoke(billId: String, data: ReviewedBillData): AppResult<BillReview> {
        return billRepository.confirmBill(billId, data)
    }
}

class GetAnalyticsUseCase @Inject constructor(
    private val billRepository: BillRepository,
) {
    suspend operator fun invoke(billId: String): AppResult<BillAnalytics> = billRepository.getAnalytics(billId)
}

class GetForecastUseCase @Inject constructor(
    private val billRepository: BillRepository,
) {
    suspend operator fun invoke(billId: String): AppResult<BillForecast> = billRepository.getForecast(billId)
}
