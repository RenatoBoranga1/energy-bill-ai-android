package br.com.energybillai.data.local.game

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class MeterReadingDraft(
    val id: String = UUID.randomUUID().toString(),
    val imageUri: String,
    val extractedValue: Long?,
    val confidenceScore: Double?,
    val rawText: String,
    val warningMessage: String? = null,
    val createdAt: String = Instant.now().toString(),
)

@Singleton
class MeterReadingDraftStore @Inject constructor() {
    private val drafts = ConcurrentHashMap<String, MeterReadingDraft>()

    fun save(draft: MeterReadingDraft) {
        drafts[draft.id] = draft
    }

    fun get(draftId: String): MeterReadingDraft? = drafts[draftId]

    fun remove(draftId: String) {
        drafts.remove(draftId)
    }
}
