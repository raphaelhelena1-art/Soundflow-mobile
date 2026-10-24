package com.soundflow.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.soundflow.mobile.downloads.DownloadRecord
import com.soundflow.mobile.playlists.Playlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.dataStore by preferencesDataStore(name = "soundflow_store")

data class ListeningStats(
    val todaySeconds: Long = 0L,
    val weekSeconds: Long = 0L,
    val monthSeconds: Long = 0L,
    val allTimeSeconds: Long = 0L,
)

/**
 * Persistance locale simple (DataStore + JSON) pour les données utilisateur :
 * playlists, historique de recherche, téléchargements, morceaux téléchargés, langue et temps d'écoute.
 */
class LocalStore(private val context: Context) {

    private object Keys {
        val PLAYLISTS = stringPreferencesKey("playlists")
        val SEARCH_HISTORY = stringPreferencesKey("search_history")
        val DOWNLOADS = stringPreferencesKey("downloads")
        val DOWNLOADED_IDS = stringPreferencesKey("downloaded_ids")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val LISTENING_LOGS = stringPreferencesKey("listening_logs")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val downloadsFlow: Flow<List<DownloadRecord>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.DOWNLOADS] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<DownloadRecord>>(raw) }.getOrDefault(emptyList())
    }

    val listeningStatsFlow: Flow<ListeningStats> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.LISTENING_LOGS] ?: return@map ListeningStats()
        val logs = runCatching { json.decodeFromString<Map<String, Long>>(raw) }.getOrDefault(emptyMap())

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = sdf.format(Date())
        val nowMs = System.currentTimeMillis()

        var todaySec = 0L
        var weekSec = 0L
        var monthSec = 0L
        var allTimeSec = 0L

        val sevenDaysAgoMs = nowMs - (7L * 24 * 3600 * 1000)
        val thirtyDaysAgoMs = nowMs - (30L * 24 * 3600 * 1000)

        for ((dateStr, sec) in logs) {
            allTimeSec += sec
            if (dateStr == todayStr) todaySec += sec
            val dateMs = runCatching { sdf.parse(dateStr)?.time }.getOrNull() ?: 0L
            if (dateMs >= sevenDaysAgoMs) weekSec += sec
            if (dateMs >= thirtyDaysAgoMs) monthSec += sec
        }

        ListeningStats(
            todaySeconds = todaySec,
            weekSeconds = weekSec,
            monthSeconds = monthSec,
            allTimeSeconds = allTimeSec,
        )
    }

    suspend fun recordListeningSeconds(seconds: Long) {
        if (seconds <= 0) return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val currentLogs = (readJson<Map<String, Long>>(Keys.LISTENING_LOGS) ?: emptyMap()).toMutableMap()
        val currentSeconds = currentLogs[today] ?: 0L
        currentLogs[today] = currentSeconds + seconds
        writeJson(Keys.LISTENING_LOGS, currentLogs)
    }

    fun extractVideoId(rawId: String): String {
        val regex = Regex("(?:v=|/v/|embed/|youtu\\.be/|/shorts/|^)([a-zA-Z0-9_-]{11})")
        return regex.find(rawId)?.groupValues?.get(1) ?: rawId.trim()
    }

    suspend fun loadLanguageTag(): String? = context.dataStore.data.first()[Keys.LANGUAGE_TAG]
    suspend fun saveLanguageTag(tag: String) {
        context.dataStore.edit { it[Keys.LANGUAGE_TAG] = tag }
    }

    suspend fun loadPlaylists(): List<Playlist>? = readJson(Keys.PLAYLISTS)
    suspend fun savePlaylists(playlists: List<Playlist>) = writeJson(Keys.PLAYLISTS, playlists)

    suspend fun loadSearchHistory(): List<String>? = readJson(Keys.SEARCH_HISTORY)
    suspend fun saveSearchHistory(history: List<String>) = writeJson(Keys.SEARCH_HISTORY, history)

    suspend fun loadDownloads(): List<DownloadRecord>? = readJson(Keys.DOWNLOADS)
    suspend fun saveDownloads(downloads: List<DownloadRecord>) = writeJson(Keys.DOWNLOADS, downloads)

    suspend fun markDownloadCompleted(trackId: String, localUri: String) {
        val targetId = extractVideoId(trackId)
        val current = loadDownloads().orEmpty()
        val updated = current.map { record ->
            if (extractVideoId(record.id) == targetId || record.id == trackId) {
                record.copy(localUri = localUri, needsAudioExtraction = false)
            } else {
                record
            }
        }
        saveDownloads(updated)
    }

    suspend fun removeDownload(trackId: String) {
        val targetId = extractVideoId(trackId)
        val current = loadDownloads().orEmpty()
        val updated = current.filterNot { extractVideoId(it.id) == targetId || it.id == trackId }
        saveDownloads(updated)
    }

    suspend fun loadDownloadedIds(): Set<String>? = readJson(Keys.DOWNLOADED_IDS)
    suspend fun saveDownloadedIds(ids: Set<String>) = writeJson(Keys.DOWNLOADED_IDS, ids)

    private suspend inline fun <reified T> readJson(key: Preferences.Key<String>): T? {
        val raw = context.dataStore.data.first()[key] ?: return null
        return runCatching { json.decodeFromString<T>(raw) }.getOrNull()
    }

    private suspend inline fun <reified T> writeJson(key: Preferences.Key<String>, value: T) {
        val raw = json.encodeToString(value)
        context.dataStore.edit { it[key] = raw }
    }
}
