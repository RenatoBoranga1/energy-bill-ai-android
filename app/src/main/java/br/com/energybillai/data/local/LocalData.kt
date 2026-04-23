package br.com.energybillai.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import br.com.energybillai.domain.model.AuthSession
import br.com.energybillai.domain.model.AuthTokens
import br.com.energybillai.domain.model.BillExtractionStatus
import br.com.energybillai.domain.model.BillSummary
import br.com.energybillai.domain.model.EnergyUser
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.sessionDataStore by preferencesDataStore(name = "energy_bill_ai_session")

private object SessionKeys {
    val userId = stringPreferencesKey("user_id")
    val userName = stringPreferencesKey("user_name")
    val userEmail = stringPreferencesKey("user_email")
    val userCreatedAt = stringPreferencesKey("user_created_at")
    val accessToken = stringPreferencesKey("access_token")
    val refreshToken = stringPreferencesKey("refresh_token")
    val tokenType = stringPreferencesKey("token_type")
    val expiresInSeconds = longPreferencesKey("expires_in_seconds")
    val refreshExpiresInSeconds = longPreferencesKey("refresh_expires_in_seconds")
    val issuedAt = longPreferencesKey("issued_at")
}

@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutableSession = MutableStateFlow<AuthSession?>(null)
    val session: StateFlow<AuthSession?> = mutableSession.asStateFlow()

    init {
        mutableSession.value = runBlocking {
            context.sessionDataStore.data
                .catch { emit(emptyPreferences()) }
                .map(::toSession)
                .first()
        }
    }

    fun observeSession(): Flow<AuthSession?> = session

    fun currentSession(): AuthSession? = mutableSession.value

    suspend fun saveSession(session: AuthSession) {
        context.sessionDataStore.edit { preferences ->
            preferences[SessionKeys.userId] = session.user.id
            preferences[SessionKeys.userName] = session.user.name
            preferences[SessionKeys.userEmail] = session.user.email
            preferences[SessionKeys.userCreatedAt] = session.user.createdAt
            preferences[SessionKeys.accessToken] = session.tokens.accessToken
            preferences[SessionKeys.refreshToken] = session.tokens.refreshToken
            preferences[SessionKeys.tokenType] = session.tokens.tokenType
            preferences[SessionKeys.expiresInSeconds] = session.tokens.expiresInSeconds.toLong()
            preferences[SessionKeys.refreshExpiresInSeconds] = session.tokens.refreshExpiresInSeconds.toLong()
            preferences[SessionKeys.issuedAt] = session.tokens.issuedAtEpochSeconds
        }
        mutableSession.value = session
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
        mutableSession.value = null
    }

    private fun toSession(preferences: Preferences): AuthSession? {
        val userId = preferences[SessionKeys.userId] ?: return null
        val userName = preferences[SessionKeys.userName] ?: return null
        val userEmail = preferences[SessionKeys.userEmail] ?: return null
        val userCreatedAt = preferences[SessionKeys.userCreatedAt] ?: return null
        val accessToken = preferences[SessionKeys.accessToken] ?: return null
        val refreshToken = preferences[SessionKeys.refreshToken] ?: return null
        return AuthSession(
            user = EnergyUser(
                id = userId,
                name = userName,
                email = userEmail,
                createdAt = userCreatedAt,
            ),
            tokens = AuthTokens(
                accessToken = accessToken,
                refreshToken = refreshToken,
                tokenType = preferences[SessionKeys.tokenType] ?: "bearer",
                expiresInSeconds = preferences[SessionKeys.expiresInSeconds]?.toInt() ?: 0,
                refreshExpiresInSeconds = preferences[SessionKeys.refreshExpiresInSeconds]?.toInt() ?: 0,
                issuedAtEpochSeconds = preferences[SessionKeys.issuedAt] ?: 0L,
            ),
        )
    }
}

@Entity(tableName = "bill_history")
data class BillSummaryEntity(
    @PrimaryKey val billId: String,
    val userId: String,
    val documentId: String,
    val referenceMonth: String?,
    val provider: String?,
    val consumptionKwh: Double?,
    val totalValue: Double?,
    val extractionStatus: String,
    val reviewRequired: Boolean,
)

@Dao
interface BillHistoryDao {
    @Query("SELECT * FROM bill_history WHERE userId = :userId ORDER BY referenceMonth DESC, billId DESC")
    fun observeHistory(userId: String): Flow<List<BillSummaryEntity>>

    @Query("DELETE FROM bill_history WHERE billId = :billId")
    suspend fun deleteByBillId(billId: String)

    @Query("DELETE FROM bill_history WHERE userId = :userId")
    suspend fun deleteByUser(userId: String)

    @Query("DELETE FROM bill_history")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BillSummaryEntity>)
}

@Database(
    entities = [BillSummaryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class EnergyBillDatabase : RoomDatabase() {
    abstract fun billHistoryDao(): BillHistoryDao
}

@Singleton
class DatabaseFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun create(): EnergyBillDatabase {
        return Room.databaseBuilder(
            context,
            EnergyBillDatabase::class.java,
            "energy_bill_ai.db",
        ).build()
    }
}

fun BillSummaryEntity.toDomain(): BillSummary {
    return BillSummary(
        billId = billId,
        documentId = documentId,
        referenceMonth = referenceMonth,
        provider = provider,
        consumptionKwh = consumptionKwh,
        totalValue = totalValue,
        extractionStatus = runCatching { BillExtractionStatus.valueOf(extractionStatus) }.getOrDefault(BillExtractionStatus.PENDING_REVIEW),
        reviewRequired = reviewRequired,
    )
}

fun BillSummary.toEntity(userId: String): BillSummaryEntity {
    return BillSummaryEntity(
        billId = billId,
        userId = userId,
        documentId = documentId,
        referenceMonth = referenceMonth,
        provider = provider,
        consumptionKwh = consumptionKwh,
        totalValue = totalValue,
        extractionStatus = extractionStatus.name,
        reviewRequired = reviewRequired,
    )
}

suspend fun EnergyBillDatabase.replaceHistory(
    userId: String,
    bills: List<BillSummary>,
) {
    withTransaction {
        billHistoryDao().deleteByUser(userId)
        billHistoryDao().insertAll(bills.map { it.toEntity(userId) })
    }
}
