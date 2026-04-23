package br.com.energybillai.data.repository

import br.com.energybillai.core.common.AppResult
import br.com.energybillai.core.network.safeApiCall
import br.com.energybillai.data.local.BillHistoryDao
import br.com.energybillai.data.local.EnergyBillDatabase
import br.com.energybillai.data.local.SessionStore
import br.com.energybillai.data.local.replaceHistory
import br.com.energybillai.data.local.toDomain
import br.com.energybillai.data.remote.AuthLoginRequestDto
import br.com.energybillai.data.remote.AuthLogoutRequestDto
import br.com.energybillai.data.remote.AuthRefreshRequestDto
import br.com.energybillai.data.remote.AuthRegisterRequestDto
import br.com.energybillai.data.remote.AuthService
import br.com.energybillai.data.remote.BillService
import br.com.energybillai.data.remote.ConfirmBillRequestDto
import br.com.energybillai.data.remote.DocumentService
import br.com.energybillai.data.remote.ExtractBillRequestDto
import br.com.energybillai.data.remote.createUploadPart
import br.com.energybillai.data.remote.toDomain
import br.com.energybillai.data.remote.toDomainSession
import br.com.energybillai.data.remote.toDto
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
import com.google.gson.Gson
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DefaultSessionRepository @Inject constructor(
    private val sessionStore: SessionStore,
) : SessionRepository {
    override fun observeSession(): Flow<AuthSession?> = sessionStore.observeSession()

    override fun currentSession(): AuthSession? = sessionStore.currentSession()

    override suspend fun saveSession(session: AuthSession) {
        sessionStore.saveSession(session)
    }

    override suspend fun clearSession() {
        sessionStore.clear()
    }
}

@Singleton
class DefaultAuthRepository @Inject constructor(
    private val authService: AuthService,
    private val sessionStore: SessionStore,
    private val billHistoryDao: BillHistoryDao,
    private val gson: Gson,
) : AuthRepository {
    override suspend fun register(name: String, email: String, password: String): AppResult<AuthSession> {
        return safeApiCall(gson) {
            authService.register(AuthRegisterRequestDto(name = name, email = email, password = password))
                .toDomainSession()
                .also { sessionStore.saveSession(it) }
        }
    }

    override suspend fun login(email: String, password: String): AppResult<AuthSession> {
        return safeApiCall(gson) {
            authService.login(AuthLoginRequestDto(email = email, password = password))
                .toDomainSession()
                .also { sessionStore.saveSession(it) }
        }
    }

    override suspend fun refresh(refreshToken: String): AppResult<AuthSession> {
        return safeApiCall(gson) {
            authService.refresh(AuthRefreshRequestDto(refreshToken = refreshToken))
                .toDomainSession(issuedAtEpochSeconds = Instant.now().epochSecond)
                .also { sessionStore.saveSession(it) }
        }
    }

    override suspend fun logout(refreshToken: String?): AppResult<Unit> {
        val result = if (refreshToken.isNullOrBlank()) {
            AppResult.Success(Unit)
        } else {
            safeApiCall(gson) {
                authService.logout(AuthLogoutRequestDto(refreshToken = refreshToken))
                Unit
            }
        }
        sessionStore.clear()
        billHistoryDao.clearAll()
        return result
    }

    override suspend fun me(): AppResult<EnergyUser> {
        return safeApiCall(gson) { authService.me().toDomain() }
    }
}

@Singleton
class DefaultBillRepository @Inject constructor(
    private val documentService: DocumentService,
    private val billService: BillService,
    private val sessionStore: SessionStore,
    private val database: EnergyBillDatabase,
    private val billHistoryDao: BillHistoryDao,
    private val gson: Gson,
) : BillRepository {

    override fun observeHistory(userId: String): Flow<List<BillSummary>> {
        return billHistoryDao.observeHistory(userId).map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun refreshHistory(userId: String): AppResult<List<BillSummary>> {
        return safeApiCall(gson) {
            billService.getHistory(userId).toDomain().also { bills ->
                database.replaceHistory(userId, bills)
            }
        }
    }

    override suspend fun uploadDocument(payload: UploadPayload): AppResult<UploadedDocument> {
        return safeApiCall(gson) {
            documentService.upload(
                createUploadPart(
                    fileName = payload.fileName,
                    mimeType = payload.mimeType,
                    bytes = payload.bytes,
                ),
            ).toDomain()
        }
    }

    override suspend fun extractBill(documentId: String): AppResult<BillReview> {
        return safeApiCall(gson) {
            billService.extract(ExtractBillRequestDto(documentId = documentId)).toDomain()
        }
    }

    override suspend fun getBill(billId: String): AppResult<BillReview> {
        return safeApiCall(gson) { billService.getBill(billId).toDomain() }
    }

    override suspend fun deleteBill(billId: String): AppResult<Unit> {
        return safeApiCall(gson) {
            billService.deleteBill(billId)
            billHistoryDao.deleteByBillId(billId)
            Unit
        }
    }

    override suspend fun confirmBill(billId: String, data: ReviewedBillData): AppResult<BillReview> {
        return safeApiCall(gson) {
            billService.confirm(
                billId = billId,
                payload = ConfirmBillRequestDto(data = data.toDto()),
            ).toDomain().also {
                sessionStore.currentSession()?.user?.id?.let { userId ->
                    refreshHistory(userId)
                }
            }
        }
    }

    override suspend fun getAnalytics(billId: String): AppResult<BillAnalytics> {
        return safeApiCall(gson) { billService.getAnalytics(billId).toDomain() }
    }

    override suspend fun getForecast(billId: String): AppResult<BillForecast> {
        return safeApiCall(gson) { billService.getForecast(billId).toDomain() }
    }
}
