package com.soundflow.mobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.media3.common.util.UnstableApi
import com.soundflow.core.player.Track
import com.soundflow.mobile.MainActivity
import com.soundflow.mobile.R

@UnstableApi
class SoundFlowAppWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.soundflow.mobile.widget.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.soundflow.mobile.widget.ACTION_NEXT"
        const val ACTION_PREV = "com.soundflow.mobile.widget.ACTION_PREV"

        private var lastWidgetPrevClickTime = 0L

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, track: Track?, isPlaying: Boolean) {
            val views = RemoteViews(context.packageName, R.layout.soundflow_widget)

            views.setTextViewText(R.id.widget_title, track?.title ?: "SoundFlow")
            views.setTextViewText(R.id.widget_artist, track?.artist ?: "Prêt à écouter")
            views.setImageViewResource(
                R.id.widget_btn_play_pause,
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )

            // Intent pour ouvrir MainActivity en cliquant sur le widget
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_album_art, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_title, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_artist, pendingIntent)

            // Intent pour Previous / Refresh
            val prevIntent = Intent(context, SoundFlowAppWidgetProvider::class.java).apply {
                action = ACTION_PREV
            }
            val prevPendingIntent = PendingIntent.getBroadcast(
                context,
                3,
                prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_prev, prevPendingIntent)

            // Intent pour Play/Pause
            val playPauseIntent = Intent(context, SoundFlowAppWidgetProvider::class.java).apply {
                action = ACTION_PLAY_PAUSE
            }
            val playPausePendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_play_pause, playPausePendingIntent)

            // Intent pour Morceau suivant
            val nextIntent = Intent(context, SoundFlowAppWidgetProvider::class.java).apply {
                action = ACTION_NEXT
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context,
                2,
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_next, nextPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            updateWidget(context, appWidgetManager, appWidgetId, null, false)
        }

        fun updateWidgets(context: Context, track: Track?, isPlaying: Boolean) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, SoundFlowAppWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (id in appWidgetIds) {
                updateWidget(context, appWidgetManager, id, track, isPlaying)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, null, false)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PREV -> {
                val now = System.currentTimeMillis()
                val delta = now - lastWidgetPrevClickTime
                lastWidgetPrevClickTime = now

                if (delta in 1..1200L) {
                    val skipIntent = Intent("com.soundflow.mobile.ACTION_SKIP_PREVIOUS").apply {
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(skipIntent)
                } else {
                    val refreshIntent = Intent("com.soundflow.mobile.ACTION_REFRESH_TRACK").apply {
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(refreshIntent)
                }
            }
            ACTION_PLAY_PAUSE -> {
                // Relaye vers togglePlayPause via broadcast ou ouvre l'app
                val ppIntent = Intent("com.soundflow.mobile.ACTION_PLAY_PAUSE").apply {
                    setPackage(context.packageName)
                }
                context.sendBroadcast(ppIntent)
            }
            ACTION_NEXT -> {
                val nextIntent = Intent("com.soundflow.mobile.ACTION_SKIP_NEXT").apply {
                    setPackage(context.packageName)
                }
                context.sendBroadcast(nextIntent)
            }
        }
    }
}
