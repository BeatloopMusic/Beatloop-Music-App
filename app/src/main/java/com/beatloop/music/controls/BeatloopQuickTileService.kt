package com.beatloop.music.controls

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class BeatloopQuickTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        sendBroadcast(
            Intent(this, PlaybackControlReceiver::class.java)
                .setAction(PlaybackControlContract.ACTION_PLAY_PAUSE)
        )
    }

    private fun updateTile() {
        val snapshot = PlaybackControlStateStore.read(this)
        val tile = qsTile ?: return

        tile.label = if (snapshot.title.isBlank()) "Beatloop" else snapshot.title.take(26)
        // setSubtitle requires API 29
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (snapshot.artist.isBlank()) {
                if (snapshot.isPlaying) "Playing" else "Paused"
            } else {
                snapshot.artist.take(22)
            }
        }
        tile.state = if (snapshot.isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    companion object {
        fun requestUpdate(context: android.content.Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                requestListeningState(
                    context,
                    ComponentName(context, BeatloopQuickTileService::class.java)
                )
            }
        }
    }
}
