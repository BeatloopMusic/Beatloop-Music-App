package com.beatloop.music.controls

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.beatloop.music.MainActivity
import com.beatloop.music.R

class BeatloopAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        updateAllWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE ||
            intent.action == PlaybackControlContract.ACTION_REFRESH_CONTROLS
        ) {
            updateAllWidgets(context)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, BeatloopAppWidgetProvider::class.java)
            )
            ids.forEach { id ->
                updateWidget(context, manager, id)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val snapshot = PlaybackControlStateStore.read(context)
            val layoutRes = resolveWidgetLayout(appWidgetManager, appWidgetId)
            val views = RemoteViews(context.packageName, layoutRes)

            views.setTextViewText(R.id.widgetSongTitle, snapshot.title)
            views.setTextViewText(R.id.widgetSongArtist, snapshot.artist)
            views.setImageViewResource(
                R.id.widgetPlayPause,
                if (snapshot.isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )
            views.setImageViewResource(
                R.id.widgetLike,
                if (snapshot.isLiked) R.drawable.ic_heart_filled
                else R.drawable.ic_heart_outline
            )

            views.setOnClickPendingIntent(
                R.id.widgetRoot,
                activityPendingIntent(context)
            )
            views.setOnClickPendingIntent(
                R.id.widgetPrev,
                controlPendingIntent(context, PlaybackControlContract.ACTION_PREVIOUS)
            )
            views.setOnClickPendingIntent(
                R.id.widgetPlayPause,
                controlPendingIntent(context, PlaybackControlContract.ACTION_PLAY_PAUSE)
            )
            views.setOnClickPendingIntent(
                R.id.widgetNext,
                controlPendingIntent(context, PlaybackControlContract.ACTION_NEXT)
            )
            views.setOnClickPendingIntent(
                R.id.widgetLike,
                controlPendingIntent(context, PlaybackControlContract.ACTION_TOGGLE_LIKE)
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun resolveWidgetLayout(
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ): Int {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)

            val useCompact = (minWidth in 1 until COMPACT_WIDTH_THRESHOLD_DP) ||
                (minHeight in 1 until COMPACT_HEIGHT_THRESHOLD_DP)

            return if (useCompact) {
                R.layout.widget_playback_controls_compact
            } else {
                R.layout.widget_playback_controls
            }
        }

        private fun activityPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
            return PendingIntent.getActivity(
                context,
                2001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun controlPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, PlaybackControlReceiver::class.java).setAction(action)
            val requestCode = when (action) {
                PlaybackControlContract.ACTION_PREVIOUS -> 3001
                PlaybackControlContract.ACTION_PLAY_PAUSE -> 3002
                PlaybackControlContract.ACTION_NEXT -> 3003
                PlaybackControlContract.ACTION_TOGGLE_LIKE -> 3004
                else -> 3999
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private const val COMPACT_WIDTH_THRESHOLD_DP = 220
        private const val COMPACT_HEIGHT_THRESHOLD_DP = 100
    }
}
