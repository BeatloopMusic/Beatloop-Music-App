package com.beatloop.music.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.beatloop.music.MainActivity
import com.beatloop.music.controls.PlaybackControlContract
import com.beatloop.music.controls.PlaybackControlStateStore
import com.beatloop.music.data.model.SearchFilter
import com.beatloop.music.data.model.SongItem
import com.beatloop.music.data.model.SponsorBlockSegment
import com.beatloop.music.data.model.SponsorBlockSettings
import com.beatloop.music.data.preferences.PreferencesManager
import com.beatloop.music.data.repository.MusicRepository
import com.beatloop.music.data.repository.SponsorBlockRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(UnstableApi::class)
class MusicService : MediaSessionService() {

    private data class MediaHints(
        val title: String,
        val artist: String
    )
    
    @Inject lateinit var musicRepository: MusicRepository
    @Inject lateinit var sponsorBlockRepository: SponsorBlockRepository
    @Inject lateinit var preferencesManager: PreferencesManager
    
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var cache: SimpleCache? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var sponsorBlockSegments: List<SponsorBlockSegment> = emptyList()
    private var sponsorBlockJob: Job? = null
    private var segmentSkipJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var lastTrackedMediaId: String? = null
    private var currentSongTitleHint: String = ""
    private var currentSongArtistHint: String = ""
    
    // Cache for resolved stream URLs with expiration time
    private val streamUrlCache = ConcurrentHashMap<String, Pair<String, Long>>()
    private val playbackRetryAttempts = ConcurrentHashMap<String, Int>()
    private val mediaHintsCache = ConcurrentHashMap<String, MediaHints>()
    private var queueAutoFillJob: Job? = null
    private var lastAutoFillSeedId: String? = null
    private var lastAutoFillAtMs: Long = 0L
    
    override fun onCreate() {
        super.onCreate()
        initializeCache()
        initializePlayer()
        initializeSession()
        refreshPlaybackControlState()
    }
    
    private fun initializeCache() {
        val cacheDir = File(cacheDir, "media_cache")
        val cacheEvictor = LeastRecentlyUsedCacheEvictor(512 * 1024 * 1024) // 512MB cache
        cache = SimpleCache(cacheDir, cacheEvictor)
    }
    
    private fun initializePlayer() {
        // Create the resolving data source factory that resolves URLs on-demand
        val dataSourceFactory = createResolvingDataSourceFactory()
        
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK) // Use NETWORK wake mode for streaming
            .build()
        
        player?.addListener(playerListener)
    }
    
    /**
     * Creates a ResolvingDataSource.Factory that resolves beatloop:// URIs to actual stream URLs
     * This is the proper way to handle URL resolution - it's done when data is actually needed,
     * not when media items are added to the player.
     */
    private fun createResolvingDataSourceFactory(): DataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setConnectTimeoutMs(30000)
            .setReadTimeoutMs(30000)
            .setAllowCrossProtocolRedirects(true)
        
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache!!)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        
        return ResolvingDataSource.Factory(cacheDataSourceFactory) { dataSpec ->
            resolveDataSpec(dataSpec)
        }
    }
    
    /**
     * Resolves a DataSpec by fetching the actual stream URL if needed.
     * This runs on the ExoPlayer's loading thread, not the main thread.
     */
    private fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        val mediaId = dataSpec.key ?: uri.lastPathSegment ?: return dataSpec
        
        // Check if this is a beatloop:// URI that needs resolution
        if (uri.scheme == "beatloop") {
            val videoId = uri.lastPathSegment ?: return dataSpec
            Log.d(TAG, "Resolving stream URL for: $videoId")
            
            // Check cache first (URLs expire after ~6 hours from YouTube)
            val cachedEntry = streamUrlCache[videoId]
            if (cachedEntry != null && cachedEntry.second > System.currentTimeMillis()) {
                Log.d(TAG, "Using cached stream URL for: $videoId")
                return dataSpec.withUri(cachedEntry.first.toUri())
            }
            
            // Resolve the stream URL synchronously (this is on ExoPlayer's loading thread)
            val streamUrl = runBlocking(Dispatchers.IO) {
                musicRepository.getStreamUrl(videoId).getOrNull()
            }
            
            if (streamUrl != null) {
                // Cache the URL with 5 hour expiration (YouTube URLs typically expire after 6 hours)
                val expiry = System.currentTimeMillis() + 5 * 60 * 60 * 1000
                streamUrlCache[videoId] = streamUrl to expiry
                if (mediaId != videoId) {
                    streamUrlCache[mediaId] = streamUrl to expiry
                }
                Log.d(TAG, "Stream URL resolved successfully for: $videoId")
                return dataSpec.withUri(streamUrl.toUri())
            } else {
                val cachedHints = mediaHintsCache[mediaId] ?: mediaHintsCache[videoId]
                val titleHint = cachedHints?.title.orEmpty().ifBlank { currentSongTitleHint }
                val artistHint = cachedHints?.artist.orEmpty().ifBlank { currentSongArtistHint }
                val alternativeUrl = runBlocking(Dispatchers.IO) {
                    tryResolveAlternativeStreamUrl(
                        videoId = videoId,
                        mediaId = mediaId,
                        titleHint = titleHint,
                        artistHint = artistHint
                    )
                }
                if (isLikelyStreamUrl(alternativeUrl)) {
                    val expiry = System.currentTimeMillis() + 5 * 60 * 60 * 1000
                    streamUrlCache[videoId] = alternativeUrl!! to expiry
                    if (mediaId != videoId) {
                        streamUrlCache[mediaId] = alternativeUrl to expiry
                    }
                    Log.d(TAG, "Resolved alternative stream URL for: $videoId")
                    return dataSpec.withUri(alternativeUrl.toUri())
                }

                Log.e(TAG, "Failed to resolve stream URL for: $videoId")
                throw PlaybackException(
                    "Failed to resolve stream URL",
                    null,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                )
            }
        }
        
        // Check if we have a cached URL that's about to expire and refresh it
        val cachedEntry = streamUrlCache[mediaId]
        if (cachedEntry != null && uri.toString() == cachedEntry.first) {
            // URL is from cache, check if still valid
            if (cachedEntry.second < System.currentTimeMillis()) {
                Log.d(TAG, "Cached URL expired, refreshing for: $mediaId")
                val newStreamUrl = runBlocking(Dispatchers.IO) {
                    musicRepository.getStreamUrl(mediaId).getOrNull()
                }
                if (newStreamUrl != null) {
                    streamUrlCache[mediaId] = newStreamUrl to (System.currentTimeMillis() + 5 * 60 * 60 * 1000)
                    return dataSpec.withUri(newStreamUrl.toUri())
                }
            }
        }
        
        return dataSpec
    }

    private suspend fun tryResolveAlternativeStreamUrl(
        videoId: String,
        mediaId: String,
        titleHint: String,
        artistHint: String
    ): String? {
        val title = titleHint.trim()
        val artist = artistHint.trim()
        if (title.isBlank()) {
            Log.d(TAG, "Skipping alternative stream search for $mediaId because title hint is blank")
            return null
        }

        val query = if (artist.isNotBlank()) "$title $artist" else title
        Log.d(TAG, "Trying alternative stream search for '$query' (original=$videoId)")

        val searchResult = musicRepository.search(query, SearchFilter.Songs).getOrNull() ?: return null
        val candidateIds = searchResult.songs
            .map { it.id }
            .filter { it.isNotBlank() && it != videoId }
            .distinct()
            .take(6)

        for (candidateId in candidateIds) {
            val candidateUrl = musicRepository.getStreamUrl(candidateId).getOrNull()
            if (isLikelyStreamUrl(candidateUrl)) {
                Log.d(TAG, "Alternative stream resolved via candidateId=$candidateId for original=$videoId")
                return candidateUrl
            }
        }

        return null
    }

    private fun isLikelyStreamUrl(url: String?): Boolean {
        return !url.isNullOrBlank() && (url.startsWith("https://") || url.startsWith("http://"))
    }

    private fun maybeAutoFillQueue(reasonLabel: String, force: Boolean) {
        if (queueAutoFillJob?.isActive == true) return

        queueAutoFillJob = serviceScope.launch {
            val playerInstance = player ?: return@launch
            val mediaCount = playerInstance.mediaItemCount
            val currentIndex = playerInstance.currentMediaItemIndex.coerceAtLeast(0)
            val remainingItems = (mediaCount - currentIndex - 1).coerceAtLeast(0)

            if (!force && remainingItems > 1) {
                return@launch
            }

            val seedMediaItem = playerInstance.currentMediaItem
            val seedId = seedMediaItem?.mediaId.orEmpty()
            val now = System.currentTimeMillis()

            if (!force && seedId.isNotBlank() && seedId == lastAutoFillSeedId && (now - lastAutoFillAtMs) < 30_000L) {
                return@launch
            }

            val existingIds = mutableSetOf<String>()
            for (index in 0 until playerInstance.mediaItemCount) {
                val existingId = playerInstance.getMediaItemAt(index).mediaId
                if (existingId.isNotBlank()) {
                    existingIds.add(existingId)
                }
            }

            val candidates = withContext(Dispatchers.IO) {
                buildQueueAutoFillCandidates(seedMediaItem, existingIds)
            }

            if (candidates.isEmpty()) {
                Log.d(TAG, "Queue auto-fill skipped ($reasonLabel): no candidates")
                return@launch
            }

            val mediaItems = candidates.map { song ->
                ensureMediaItemHasUri(
                    createMediaItem(
                        id = song.id,
                        title = song.title,
                        artist = song.artistsText,
                        thumbnailUrl = song.thumbnailUrl,
                        localPath = song.localPath
                    )
                )
            }

            val insertionIndex = (playerInstance.currentMediaItemIndex + 1)
                .coerceAtLeast(0)
                .coerceAtMost(playerInstance.mediaItemCount)

            playerInstance.addMediaItems(insertionIndex, mediaItems)
            lastAutoFillSeedId = seedId.ifBlank { mediaItems.firstOrNull()?.mediaId }
            lastAutoFillAtMs = System.currentTimeMillis()

            Log.d(TAG, "Queue auto-fill ($reasonLabel): added ${mediaItems.size} recommendation(s)")

            if (playerInstance.playbackState == Player.STATE_ENDED || playerInstance.playbackState == Player.STATE_IDLE) {
                val targetIndex = insertionIndex.coerceAtMost(playerInstance.mediaItemCount - 1).coerceAtLeast(0)
                playerInstance.seekTo(targetIndex, 0L)
                playerInstance.prepare()
                playerInstance.playWhenReady = true
            }
        }
    }

    private suspend fun buildQueueAutoFillCandidates(
        seedMediaItem: MediaItem?,
        existingIds: Set<String>
    ): List<SongItem> {
        val seedId = seedMediaItem?.mediaId.orEmpty()
        val seedTitle = seedMediaItem?.mediaMetadata?.title?.toString().orEmpty().ifBlank { currentSongTitleHint }
        val seedArtist = seedMediaItem?.mediaMetadata?.artist?.toString().orEmpty().ifBlank { currentSongArtistHint }

        val collected = mutableListOf<SongItem>()

        if (seedId.isNotBlank()) {
            collected += musicRepository.getRelatedSongs(seedId).getOrDefault(emptyList())
        }

        if (collected.size < 10) {
            val query = listOf(seedTitle, seedArtist)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .trim()

            if (query.isNotBlank()) {
                val searchSongs = musicRepository.search(query, SearchFilter.Songs)
                    .getOrNull()
                    ?.songs
                    .orEmpty()
                collected += searchSongs
            }
        }

        if (collected.size < 10) {
            val historySongs = runCatching { musicRepository.getPlayHistory().first() }
                .getOrDefault(emptyList())
            collected += historySongs
        }

        return collected
            .filter { candidate ->
                candidate.id.isNotBlank() && !existingIds.contains(candidate.id)
            }
            .distinctBy { it.id }
            .take(20)
    }
    
    private fun initializeSession() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(pendingIntent)
            .setCallback(sessionCallback)
            .build()
    }
    
    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.let { item ->
                currentSongTitleHint = item.mediaMetadata.title?.toString().orEmpty()
                currentSongArtistHint = item.mediaMetadata.artist?.toString().orEmpty()
                cacheMediaHints(item)
                playbackRetryAttempts.remove(item.mediaId)
                loadSponsorBlockSegments(item.mediaId)
                trackPlaybackAnalytics(item)
                refreshPlaybackControlState()
            }

            maybeAutoFillQueue(reasonLabel = "transition", force = false)
        }
        
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                startSegmentSkipping()
            } else {
                stopSegmentSkipping()
            }
            refreshPlaybackControlState()
        }

        override fun onPlayerError(error: PlaybackException) {
            val mediaId = player?.currentMediaItem?.mediaId.orEmpty()
            Log.e(TAG, "Playback error for mediaId=$mediaId", error)

            if (mediaId.isNotBlank()) {
                val attempt = playbackRetryAttempts[mediaId] ?: 0
                if (attempt < 2) {
                    playbackRetryAttempts[mediaId] = attempt + 1
                    streamUrlCache.remove(mediaId)

                    serviceScope.launch {
                        delay(250)
                        player?.prepare()
                        player?.playWhenReady = true
                    }
                } else {
                    val playerInstance = player
                    if (playerInstance != null && playerInstance.hasNextMediaItem()) {
                        Log.w(TAG, "Skipping unplayable mediaId=$mediaId after retries")
                        playbackRetryAttempts.remove(mediaId)
                        streamUrlCache.remove(mediaId)
                        serviceScope.launch {
                            playerInstance.seekToNextMediaItem()
                            delay(100)
                            playerInstance.prepare()
                            playerInstance.playWhenReady = true
                        }
                    } else {
                        maybeAutoFillQueue(reasonLabel = "error", force = true)
                    }
                }
            }

            refreshPlaybackControlState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                maybeAutoFillQueue(reasonLabel = "ended", force = true)
            }
        }
    }

    private fun trackPlaybackAnalytics(item: MediaItem) {
        val songId = item.mediaId
        if (songId.isBlank() || songId == lastTrackedMediaId) return

        lastTrackedMediaId = songId
        val title = item.mediaMetadata.title?.toString().orEmpty().ifBlank { "Unknown" }
        val artist = item.mediaMetadata.artist?.toString().orEmpty().ifBlank { "Unknown" }
        val artwork = item.mediaMetadata.artworkUri?.toString()

        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                musicRepository.recordPlayback(
                    songId = songId,
                    title = title,
                    artist = artist,
                    thumbnailUrl = artwork
                )
            }.onFailure { error ->
                Log.w(TAG, "Failed to track playback analytics for $songId", error)
            }
        }
    }
    
    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val likeCommand = SessionCommand(PlaybackControlContract.ACTION_TOGGLE_LIKE, Bundle.EMPTY)
            val setSleepTimerCommand = SessionCommand(ACTION_SET_SLEEP_TIMER, Bundle.EMPTY)
            val clearSleepTimerCommand = SessionCommand(ACTION_CLEAR_SLEEP_TIMER, Bundle.EMPTY)
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(likeCommand)
                        .add(SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_TOGGLE_REPEAT, Bundle.EMPTY))
                        .add(setSleepTimerCommand)
                        .add(clearSleepTimerCommand)
                        .build()
                )
                .setCustomLayout(
                    listOf(
                        CommandButton.Builder()
                            .setDisplayName("Like")
                            .setSessionCommand(likeCommand)
                            .setIconResId(android.R.drawable.btn_star_big_off)
                            .build()
                    )
                )
                .build()
        }
        
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                PlaybackControlContract.ACTION_TOGGLE_LIKE -> {
                    serviceScope.launch(Dispatchers.IO) {
                        toggleCurrentSongLike()
                        refreshPlaybackControlState()
                    }
                }
                ACTION_TOGGLE_SHUFFLE -> {
                    player?.shuffleModeEnabled = !(player?.shuffleModeEnabled ?: false)
                }
                ACTION_TOGGLE_REPEAT -> {
                    player?.let {
                        it.repeatMode = when (it.repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                    }
                }
                ACTION_SET_SLEEP_TIMER -> {
                    val minutes = args.getInt(EXTRA_SLEEP_TIMER_MINUTES, 0)
                    setSleepTimer(minutes)
                }
                ACTION_CLEAR_SLEEP_TIMER -> {
                    clearSleepTimer()
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            // Don't resolve URLs here - let the ResolvingDataSource handle it
            // Just ensure media items have the beatloop:// URI scheme for resolution
            val resolvedItems = mediaItems.map { item ->
                ensureMediaItemHasUri(item)
            }.toMutableList()
            return Futures.immediateFuture(resolvedItems)
        }
    }
    
    /**
     * Ensures a media item has a URI for playback.
     * If it doesn't have one, creates a beatloop:// URI that will be resolved later.
     */
    private fun ensureMediaItemHasUri(item: MediaItem): MediaItem {
        cacheMediaHints(item)

        val uri = item.localConfiguration?.uri
        // If no URI or empty URI, create a beatloop:// placeholder
        if (uri == null || uri.toString().isEmpty()) {
            val videoId = item.mediaId
            Log.d(TAG, "Creating beatloop URI for: $videoId")
            return item.buildUpon()
                .setUri("beatloop://song/$videoId")
                .build()
        }
        return item
    }

    private fun cacheMediaHints(item: MediaItem) {
        val mediaId = item.mediaId
        if (mediaId.isBlank()) return

        val title = item.mediaMetadata.title?.toString().orEmpty().trim()
        val artist = item.mediaMetadata.artist?.toString().orEmpty().trim()
        if (title.isBlank() && artist.isBlank()) return

        mediaHintsCache[mediaId] = MediaHints(title = title, artist = artist)
    }

    private suspend fun toggleCurrentSongLike() {
        val currentMediaId = withContext(Dispatchers.Main) {
            player?.currentMediaItem?.mediaId
        } ?: return
        if (currentMediaId.isBlank()) return

        val isLiked = runCatching { musicRepository.isSongLiked(currentMediaId) }
            .getOrDefault(false)

        runCatching {
            if (isLiked) {
                musicRepository.unlikeSong(currentMediaId)
            } else {
                musicRepository.likeSong(currentMediaId)
            }
        }.onFailure { error ->
            Log.w(TAG, "Failed to toggle like for $currentMediaId", error)
        }
    }

    private fun refreshPlaybackControlState() {
        val currentItem = player?.currentMediaItem
        val mediaId = currentItem?.mediaId ?: ""
        val title = currentItem?.mediaMetadata?.title?.toString().orEmpty().ifBlank { "Nothing playing" }
        val artist = currentItem?.mediaMetadata?.artist?.toString().orEmpty().ifBlank { "Beatloop" }
        val isPlaying = player?.isPlaying == true

        serviceScope.launch(Dispatchers.IO) {
            val isLiked = if (mediaId.isBlank()) {
                false
            } else {
                runCatching { musicRepository.isSongLiked(mediaId) }.getOrDefault(false)
            }

            PlaybackControlStateStore.save(
                context = this@MusicService,
                mediaId = mediaId,
                title = title,
                artist = artist,
                isPlaying = isPlaying,
                isLiked = isLiked
            )
            PlaybackControlStateStore.notifyStateChanged(this@MusicService)
        }
    }
    
    private fun loadSponsorBlockSegments(videoId: String) {
        sponsorBlockJob?.cancel()
        sponsorBlockJob = serviceScope.launch {
            val enabled = preferencesManager.sponsorBlockEnabled.first()
            if (!enabled) {
                sponsorBlockSegments = emptyList()
                return@launch
            }
            
            val settings = SponsorBlockSettings(
                enabled = true,
                skipSponsor = preferencesManager.skipSponsor.first(),
                skipSelfPromo = preferencesManager.skipSelfPromo.first(),
                skipIntro = preferencesManager.skipIntro.first(),
                skipOutro = preferencesManager.skipOutro.first()
            )
            
            val result = sponsorBlockRepository.getSkipSegments(videoId, settings)
            sponsorBlockSegments = result.getOrDefault(emptyList())
        }
    }
    
    private fun startSegmentSkipping() {
        segmentSkipJob?.cancel()
        segmentSkipJob = serviceScope.launch {
            while (isActive) {
                val currentPosition = player?.currentPosition ?: 0
                
                sponsorBlockSegments.forEach { segment ->
                    if (currentPosition in segment.startTime..segment.endTime) {
                        player?.seekTo(segment.endTime)
                    }
                }
                
                delay(500) // Check every 500ms
            }
        }
    }
    
    private fun stopSegmentSkipping() {
        segmentSkipJob?.cancel()
    }

    private fun setSleepTimer(minutes: Int) {
        clearSleepTimer()

        if (minutes <= 0) {
            Log.d(TAG, "Sleep timer ignored because duration was <= 0 minutes")
            return
        }

        val durationMs = minutes * 60 * 1000L
        sleepTimerJob = serviceScope.launch {
            Log.d(TAG, "Sleep timer started for $minutes minute(s)")
            delay(durationMs)
            Log.d(TAG, "Sleep timer elapsed. Pausing playback.")
            player?.pause()
            player?.playWhenReady = false
            refreshPlaybackControlState()
        }
    }

    private fun clearSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        Log.d(TAG, "Sleep timer cleared")
    }
    
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }
    
    override fun onDestroy() {
        clearSleepTimer()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        playbackRetryAttempts.clear()
        cache?.release()
        serviceScope.cancel()
        super.onDestroy()
    }
    
    companion object {
        private const val TAG = "MusicService"
        const val ACTION_TOGGLE_SHUFFLE = "com.beatloop.music.TOGGLE_SHUFFLE"
        const val ACTION_TOGGLE_REPEAT = "com.beatloop.music.TOGGLE_REPEAT"
        const val ACTION_SET_SLEEP_TIMER = "com.beatloop.music.SET_SLEEP_TIMER"
        const val ACTION_CLEAR_SLEEP_TIMER = "com.beatloop.music.CLEAR_SLEEP_TIMER"
        const val EXTRA_SLEEP_TIMER_MINUTES = "extra_sleep_timer_minutes"
    }
}
