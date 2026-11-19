package com.soundflow.core.extraction

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Flux résolus pour la lecture : audio seul, ou combiné vidéo+audio si YouTube en fournit un. */
data class PlaybackStreams(val audioUrl: String?, val combinedVideoUrl: String?)

/** Flux résolus pour le téléchargement. isVideoCombinedWithAudio=false = vidéo sans son. */
data class DownloadStreams(val audioUrl: String?, val videoUrl: String?, val isVideoCombinedWithAudio: Boolean)

/**
 * Accès à YouTube sans passer par l'API officielle (donc sans clé, sans quota).
 * NewPipeExtractor parse directement les pages/endpoints internes de YouTube.
 */
class YouTubeRepository {

    init {
        NewPipeInitializer.init()
    }

    private val youtubeService by lazy { NewPipe.getService("YouTube") }

    // État de pagination de la dernière recherche, pour permettre le défilement infini.
    private var lastQueryHandler: SearchQueryHandler? = null
    private var lastNextPage: Page? = null

    val hasMoreResults: Boolean get() = lastNextPage != null

    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val handler = youtubeService.searchQHFactory.fromQuery(query, listOf("videos"), null)
        val searchInfo = SearchInfo.getInfo(youtubeService, handler)
        lastQueryHandler = handler
        lastNextPage = searchInfo.nextPage
        searchInfo.relatedItems.filterIsInstance<StreamInfoItem>().map(::toSearchResult)
    }

    /**
     * Recherche la meilleure correspondance pour une piste issue de Spotify
     * en appliquant un filtrage strict par correspondance exacte de durée,
     * chaîne officielle (- Topic, VEVO, Official) et type de piste (studio vs live/remix).
     */
    suspend fun searchBestMatch(
        title: String,
        artist: String,
        durationMs: Long,
    ): SearchResult? = withContext(Dispatchers.IO) {
        val query = if (artist.isNotBlank()) "$artist - $title" else title
        val results = search(query)
        if (results.isEmpty()) return@withContext null

        val targetDurationSec = if (durationMs > 0) durationMs / 1000L else 0L
        val lowerTitle = title.lowercase()

        val isOriginalLive = lowerTitle.contains("live")
        val isOriginalRemix = lowerTitle.contains("remix")
        val isOriginalCover = lowerTitle.contains("cover")
        val isOriginalInstrumental = lowerTitle.contains("instrumental")

        fun calculateScore(item: SearchResult): Int {
            var score = 0
            val uploader = item.uploaderName.trim()
            val itemTitle = item.title.trim()
            val lowerItemTitle = itemTitle.lowercase()

            // 1. Filtrage par chaîne officielle
            when {
                uploader.endsWith("- Topic", ignoreCase = true) || uploader.contains("$artist - Topic", ignoreCase = true) -> score += 100
                uploader.contains("VEVO", ignoreCase = true) -> score += 80
                artist.isNotBlank() && uploader.equals(artist, ignoreCase = true) -> score += 70
                artist.isNotBlank() && uploader.contains(artist, ignoreCase = true) -> score += 50
                uploader.contains("Official", ignoreCase = true) || itemTitle.contains("Official", ignoreCase = true) -> score += 30
            }

            // 2. Filtrage par correspondance de durée exacte
            if (targetDurationSec > 0 && item.durationSeconds > 0) {
                val diff = Math.abs(item.durationSeconds - targetDurationSec)
                when {
                    diff == 0L || diff == 1L -> score += 70 // Correspondance parfaite 1:1
                    diff <= 3 -> score += 50
                    diff <= 7 -> score += 30
                    diff <= 15 -> score += 10
                    diff > 25 -> score -= 80 // Pénalise fortement les clips avec intros/outros longues ou remixes
                }
            }

            // 3. Pénalisation des variations non souhaitées (Live, Remix, Cover, Instrumental) si l'original ne l'est pas
            if (!isOriginalLive && lowerItemTitle.contains("live")) score -= 30
            if (!isOriginalRemix && lowerItemTitle.contains("remix")) score -= 30
            if (!isOriginalCover && lowerItemTitle.contains("cover")) score -= 40
            if (!isOriginalInstrumental && lowerItemTitle.contains("instrumental")) score -= 40

            // 4. Correspondance du titre et de l'artiste
            if (itemTitle.contains(title, ignoreCase = true)) score += 20
            if (artist.isNotBlank() && itemTitle.contains(artist, ignoreCase = true)) score += 10

            return score
        }

        results.sortedByDescending { calculateScore(it) }.firstOrNull()
    }

    /** Récupère la page suivante de résultats pour la dernière recherche effectuée. */
    suspend fun loadMoreResults(): List<SearchResult> = withContext(Dispatchers.IO) {
        val handler = lastQueryHandler ?: return@withContext emptyList()
        val page = lastNextPage ?: return@withContext emptyList()
        val nextPage = SearchInfo.getMoreItems(youtubeService, handler, page)
        lastNextPage = nextPage.nextPage
        nextPage.items.filterIsInstance<StreamInfoItem>().map(::toSearchResult)
    }

    private fun toSearchResult(item: StreamInfoItem) = SearchResult(
        id = item.url,
        title = item.name,
        uploaderName = item.uploaderName.orEmpty(),
        thumbnailUrl = item.thumbnails.firstOrNull()?.url,
        durationSeconds = item.duration,
    )

    /** Suggestions de recherche en temps réel (autocomplétion), affichées pendant la frappe. */
    suspend fun getSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        youtubeService.suggestionExtractor?.suggestionList(query).orEmpty()
    }

    /** "Hits du moment" pour l'écran d'accueil — recherche filtrée sur la musique
     * (le kiosque "Tendances" généraliste de YouTube mélange trop de contenu non musical). */
    suspend fun getTrending(): List<SearchResult> = withContext(Dispatchers.IO) {
        val handler = youtubeService.searchQHFactory.fromQuery("top hits", listOf("music_songs"), null)
        val searchInfo = SearchInfo.getInfo(youtubeService, handler)
        searchInfo.relatedItems.filterIsInstance<StreamInfoItem>().map(::toSearchResult)
    }

    /**
     * Flux pour la LECTURE : privilégie un flux combiné vidéo+audio quand YouTube en fournit un
     * (permet l'affichage vidéo dans le mini-lecteur), avec repli sur l'audio seul sinon.
     */
    suspend fun resolvePlaybackStreams(videoUrl: String): PlaybackStreams = withContext(Dispatchers.IO) {
        val streamInfo = StreamInfo.getInfo(youtubeService, videoUrl)
        val audioUrl = streamInfo.audioStreams.maxByOrNull { it.averageBitrate }?.content
            ?: streamInfo.hlsUrl
        val combinedVideo = streamInfo.videoStreams.firstOrNull { !it.isVideoOnly }?.content
        PlaybackStreams(audioUrl = audioUrl, combinedVideoUrl = combinedVideo)
    }

    /**
     * Flux pour le TÉLÉCHARGEMENT. Pour la vidéo : privilégie un flux combiné (avec son),
     * avec repli sur vidéo seule (sans son) si YouTube ne fournit plus de flux combiné —
     * une vraie fusion audio/vidéo nécessiterait un muxage (pas encore implémenté).
     */
    suspend fun getDownloadStreams(videoUrl: String): DownloadStreams = withContext(Dispatchers.IO) {
        val streamInfo = StreamInfo.getInfo(youtubeService, videoUrl)
        val audioUrl = streamInfo.audioStreams.maxByOrNull { it.averageBitrate }?.content
        val combinedVideo = streamInfo.videoStreams.firstOrNull { !it.isVideoOnly }
        val fallbackVideo = combinedVideo ?: streamInfo.videoOnlyStreams.firstOrNull()
        DownloadStreams(
            audioUrl = audioUrl,
            videoUrl = fallbackVideo?.content,
            isVideoCombinedWithAudio = combinedVideo != null,
        )
    }
}
