package com.forgebuild.forgehouse50.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PendingCompletion(
    val dayNumber: Int,
    val readingSeconds: Int,
    val firstOpenedAt: String,
    val finalChapterReached: Boolean = true,
    val enqueuedAt: Long = System.currentTimeMillis()
)

class OfflineCompletionQueue(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("fh50_offline_completions", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun enqueue(userId: String, completion: PendingCompletion) {
        val list = getPending(userId).toMutableList()
        list.removeAll { it.dayNumber == completion.dayNumber }
        list.add(completion)
        prefs.edit().putString("pending_$userId", json.encodeToString(list)).apply()
    }

    fun getPending(userId: String): List<PendingCompletion> {
        val raw = prefs.getString("pending_$userId", null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<PendingCompletion>>(raw) }.getOrDefault(emptyList())
    }

    fun remove(userId: String, dayNumber: Int) {
        val list = getPending(userId).filter { it.dayNumber != dayNumber }
        prefs.edit().putString("pending_$userId", json.encodeToString(list)).apply()
    }

    suspend fun processQueue(userId: String, api: ApiClient): List<Int> = withContext(Dispatchers.IO) {
        val pending = getPending(userId)
        val succeeded = mutableListOf<Int>()
        for (item in pending) {
            val res = runCatching {
                api.completeDay(
                    n = item.dayNumber,
                    readingSeconds = item.readingSeconds,
                    firstOpenedAt = item.firstOpenedAt,
                    finalChapterReached = item.finalChapterReached
                )
            }
            if (res.isSuccess) {
                remove(userId, item.dayNumber)
                succeeded.add(item.dayNumber)
            }
        }
        succeeded
    }
}
