package br.com.energybillai.domain.repository

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
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun observeSession(): Flow<AuthSession?>
    fun currentSession(): AuthSession?
    suspend fun saveSession(session: AuthSession)
    suspend fun clearSession()
}

interface AuthRepository {
    suspend fun register(name: String, email: String, password: String): AppResult<AuthSession>
    suspend fun login(email: String, password: String): AppResult<AuthSession>
    suspend fun refresh(refreshToken: String): AppResult<AuthSession>
    suspend fun logout(refreshToken: String?): AppResult<Unit>
    suspend fun me(): AppResult<EnergyUser>
}

interface BillRepository {
    fun observeHistory(userId: String): Flow<List<BillSummary>>
    suspend fun refreshHistory(userId: String): AppResult<List<BillSummary>>
    suspend fun uploadDocument(payload: UploadPayload): AppResult<UploadedDocument>
    suspend fun extractBill(documentId: String): AppResult<BillReview>
    suspend fun getBill(billId: String): AppResult<BillReview>
    suspend fun confirmBill(billId: String, data: ReviewedBillData): AppResult<BillReview>
    suspend fun getAnalytics(billId: String): AppResult<BillAnalytics>
    suspend fun getForecast(billId: String): AppResult<BillForecast>
}
