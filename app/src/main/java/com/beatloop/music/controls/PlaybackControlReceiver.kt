package com.beatloop.music.controls

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.beatloop.music.playback.MusicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class PlaybackControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                handleAction(context, action)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleAction(context: Context, action: String) {
        if (action == PlaybackControlContract.ACTION_REFRESH_CONTROLS) {
            PlaybackControlStateStore.notifyStateChanged(context)
            return
        }

        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        try {
            val controller = controllerFuture.get(3, TimeUnit.SECONDS)
            val previous = PlaybackControlStateStore.read(context)
            var nextLikedState = previous.isLiked

            when (action) {
                PlaybackControlContract.ACTION_PLAY_PAUSE -> {
                    if (controller.isPlaying) controller.pause() else controller.play()
                }

                PlaybackControlContract.ACTION_NEXT -> {
                    controller.seekToNextMediaItem()
                }

                PlaybackControlContract.ACTION_PREVIOUS -> {
                    controller.seekToPreviousMediaItem()
                }

                PlaybackControlContract.ACTION_TOGGLE_LIKE -> {
                    controller.sendCustomCommand(
                        SessionCommand(PlaybackControlContract.ACTION_TOGGLE_LIKE, Bundle.EMPTY),
                        Bundle.EMPTY
                    ).get(2, TimeUnit.SECONDS)
                    nextLikedState = !previous.isLiked
                }
            }

            val currentItem = controller.currentMediaItem
            PlaybackControlStateStore.save(
                context = context,
                mediaId = currentItem?.mediaId ?: "",
                title = currentItem?.mediaMetadata?.title?.toString().orEmpty(),
                artist = currentItem?.mediaMetadata?.artist?.toString().orEmpty(),
                isPlaying = controller.isPlaying,
                isLiked = nextLikedState
            )
            PlaybackControlStateStore.notifyStateChanged(context)
        } catch (error: Exception) {
            Log.w(TAG, "Failed to handle playback control action: $action", error)
        } finally {
            MediaController.releaseFuture(controllerFuture)
        }
    }

    companion object {
        private const val TAG = "PlaybackControlReceiver"
    }
}
