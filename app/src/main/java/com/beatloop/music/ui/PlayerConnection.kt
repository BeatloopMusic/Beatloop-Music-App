package com.beatloop.music.ui

import android.os.Bundle
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.beatloop.music.playback.MusicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> { null }

class PlayerConnection(
    private val mediaController: MediaController,
    private val scope: CoroutineScope
) {
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _currentMediaItem = MutableStateFlow<MediaItem?>(null)
    val currentMediaItem: MediaItem? get() = _currentMediaItem.value
    val currentMediaItemFlow: StateFlow<MediaItem?> = _currentMediaItem.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    
    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled.asStateFlow()
    
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _currentQueueIndex = MutableStateFlow(0)
    val currentQueueIndex: StateFlow<Int> = _currentQueueIndex.asStateFlow()

    private val _playRequestVersion = MutableStateFlow(0)
    val playRequestVersion: StateFlow<Int> = _playRequestVersion.asStateFlow()
    
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }
        
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentMediaItem.value = mediaItem
            _currentQueueIndex.value = mediaController.currentMediaItemIndex.coerceAtLeast(0)
        }
        
        override fun onPlaybackStateChanged(playbackState: Int) {
            updateDuration()
        }
        
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _shuffleModeEnabled.value = shuffleModeEnabled
        }
        
        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = repeatMode
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            _currentQueueIndex.value = mediaController.currentMediaItemIndex.coerceAtLeast(0)
        }
    }
    
    init {
        mediaController.addListener(listener)
        _isPlaying.value = mediaController.isPlaying
        _currentMediaItem.value = mediaController.currentMediaItem
        _shuffleModeEnabled.value = mediaController.shuffleModeEnabled
        _repeatMode.value = mediaController.repeatMode
        _currentQueueIndex.value = mediaController.currentMediaItemIndex.coerceAtLeast(0)
        updateDuration()
        startPositionUpdater()
    }
    
    private fun startPositionUpdater() {
        scope.launch {
            while (isActive) {
                _currentPosition.value = mediaController.currentPosition
                delay(500)
            }
        }
    }
    
    private fun updateDuration() {
        val dur = mediaController.duration
        _duration.value = if (dur > 0) dur else 0
    }
    
    fun play() = mediaController.play()
    
    fun pause() = mediaController.pause()
    
    fun playPause() {
        if (mediaController.isPlaying) pause() else play()
    }
    
    fun seekTo(position: Long) = mediaController.seekTo(position)
    
    fun seekToNext() = mediaController.seekToNext()
    
    fun seekToPrevious() = mediaController.seekToPreviousMediaItem()
    
    // Aliases for compatibility
    fun skipToNext() = seekToNext()
    
    fun skipToPrevious() = seekToPrevious()
    
    fun togglePlayPause() = playPause()
    
    fun toggleShuffle() {
        mediaController.shuffleModeEnabled = !mediaController.shuffleModeEnabled
    }
    
    fun toggleRepeatMode() {
        mediaController.repeatMode = when (mediaController.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
    
    // Alias for toggleRepeatMode
    fun cycleRepeatMode() = toggleRepeatMode()
    
    fun seekToQueueItem(index: Int) {
        if (index in 0 until mediaController.mediaItemCount) {
            mediaController.seekTo(index, 0)
        }
    }

    fun removeQueueItem(index: Int) {
        if (index in 0 until mediaController.mediaItemCount) {
            mediaController.removeMediaItem(index)
        }
    }

    fun getCurrentQueueIndex(): Int = mediaController.currentMediaItemIndex.coerceAtLeast(0)
    
    fun setMediaItem(mediaItem: MediaItem) {
        _currentMediaItem.value = mediaItem
        _currentQueueIndex.value = 0
        _currentPosition.value = 0L
        _duration.value = 0L
        _playRequestVersion.value += 1

        mediaController.setMediaItem(mediaItem)
        mediaController.prepare()
        mediaController.play()
    }
    
    fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int = 0) {
        if (mediaItems.isEmpty()) {
            mediaController.clearMediaItems()
            return
        }

        val safeStartIndex = startIndex.coerceIn(0, mediaItems.lastIndex)
        _currentMediaItem.value = mediaItems[safeStartIndex]
        _currentQueueIndex.value = safeStartIndex
        _currentPosition.value = 0L
        _duration.value = 0L
        _playRequestVersion.value += 1

        mediaController.setMediaItems(mediaItems, safeStartIndex, 0)
        mediaController.prepare()
        mediaController.play()
    }
    
    fun addMediaItem(mediaItem: MediaItem) {
        mediaController.addMediaItem(mediaItem)
    }
    
    fun addMediaItemNext(mediaItem: MediaItem) {
        val nextIndex = mediaController.currentMediaItemIndex + 1
        mediaController.addMediaItem(nextIndex, mediaItem)
    }

    fun replaceCurrentMediaItem(mediaItem: MediaItem, preservePosition: Boolean = true) {
        val index = mediaController.currentMediaItemIndex
        if (index !in 0 until mediaController.mediaItemCount) return

        val position = if (preservePosition) mediaController.currentPosition else 0L
        val wasPlaying = mediaController.isPlaying

        mediaController.replaceMediaItem(index, mediaItem)
        mediaController.seekTo(index, position)
        mediaController.prepare()
        if (wasPlaying) {
            mediaController.play()
        }
    }
    
    fun clearQueue() {
        mediaController.clearMediaItems()
    }
    
    fun getQueue(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        for (i in 0 until mediaController.mediaItemCount) {
            items.add(mediaController.getMediaItemAt(i))
        }
        return items
    }

    fun setSleepTimer(minutes: Int) {
        val args = Bundle().apply {
            putInt(MusicService.EXTRA_SLEEP_TIMER_MINUTES, minutes)
        }
        mediaController.sendCustomCommand(
            SessionCommand(MusicService.ACTION_SET_SLEEP_TIMER, Bundle.EMPTY),
            args
        )
    }

    fun clearSleepTimer() {
        mediaController.sendCustomCommand(
            SessionCommand(MusicService.ACTION_CLEAR_SLEEP_TIMER, Bundle.EMPTY),
            Bundle.EMPTY
        )
    }
    
    val player: Player get() = mediaController
}
