/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.composefragment

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

/** A media session read straight from the system (title, artist, art, its controller). */
internal class MiraActiveSession(
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val playing: Boolean,
    val controller: MediaController,
)

/**
 * The playing media session, read from the system's media sessions instead of media
 * notifications. Apps like YouTube in picture-in-picture play without a media notification, so
 * the media pipeline never sees them; the Quick Settings media card falls back to this.
 */
@Composable
internal fun rememberMiraActiveSession(): MiraActiveSession? {
    val context = LocalContext.current
    var info by remember { mutableStateOf<MiraActiveSession?>(null) }
    DisposableEffect(context) {
        val msm = context.getSystemService(MediaSessionManager::class.java)
        val handler = Handler(Looper.getMainLooper())
        var current: MediaController? = null
        lateinit var callback: MediaController.Callback

        fun publish() {
            info = current?.let { read(it) }
        }

        fun pick(list: List<MediaController>?) {
            val sessions = list.orEmpty()
            val best =
                sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                    ?: sessions.firstOrNull {
                        it.metadata != null && it.playbackState?.state == PlaybackState.STATE_PAUSED
                    }
            if (best?.sessionToken != current?.sessionToken) {
                runCatching { current?.unregisterCallback(callback) }
                current = best
                runCatching { best?.registerCallback(callback, handler) }
            }
            publish()
        }

        fun repick() {
            runCatching { pick(msm?.getActiveSessions(null)) }
        }

        callback =
            object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    repick()
                }

                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    publish()
                }

                override fun onSessionDestroyed() {
                    repick()
                }
            }
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }
        runCatching { msm?.addOnActiveSessionsChangedListener(listener, null, handler) }
        repick()
        onDispose {
            runCatching { msm?.removeOnActiveSessionsChangedListener(listener) }
            runCatching { current?.unregisterCallback(callback) }
        }
    }
    return info
}

private fun read(c: MediaController): MiraActiveSession? {
    val m = c.metadata ?: return null
    val title =
        (m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: m.getString(MediaMetadata.METADATA_KEY_TITLE)).orEmpty()
    if (title.isEmpty()) return null
    val artist =
        (m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                ?: m.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty()
    val bitmap =
        m.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    return MiraActiveSession(
        title = title,
        artist = artist,
        art = runCatching { bitmap?.asImageBitmap() }.getOrNull(),
        playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
        controller = c,
    )
}
