/*
 * Copyright (C) 2026 MIRACLO
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.miraclo

import android.app.ActivityManager
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * MIRACLO split shade, always on. Pulling the shade down from the Quick Settings side of the
 * top edge opens Quick Settings only; pulling from the other side opens notifications only.
 * Quick Settings is on the right unless the sides are swapped (Mira Studio > Notification
 * shade). Main thread only.
 */
object MiraCLOSplitShade {
    const val KEY_SWAP = "miraclo_split_shade_swap"
    private const val KEY_QS_STYLE = "miraclo_qs_style"

    private const val enabled = true
    @Volatile private var swapped = false
    @Volatile private var qsStyle = 0
    private var appContext: Context? = null

    /** Whether split shade is on, as Compose state so Quick Settings follows the setting. */
    var enabledState by mutableStateOf(true)
        private set

    /** True while the shade shows notifications only. Read by Quick Settings to hide itself. */
    var notificationsOnly by mutableStateOf(false)
        private set

    @JvmStatic
    fun isEnabled(context: Context): Boolean {
        ensureWatching(context)
        return enabled
    }

    /** Whether [x] (in a view [width] wide) is on the Quick Settings side. */
    @JvmStatic
    fun isQsSide(context: Context, x: Float, width: Int): Boolean {
        ensureWatching(context)
        val right = x >= width / 2f
        return if (swapped) !right else right
    }

    @JvmStatic
    fun isNotificationsOnly(): Boolean = notificationsOnly

    /**
     * Whether the shade header steps aside: the ColorOS, iOS and One UI looks draw their own
     * header on the Quick Settings side.
     */
    @JvmStatic
    fun hideHeader(): Boolean = !notificationsOnly && qsStyle in MIRACLO_LOOKS

    /** Starts watching the settings (Quick Settings calls this once). */
    @JvmStatic
    fun watch(context: Context) = ensureWatching(context)

    /** Called when a pull starts from the top edge with the shade closed. */
    @JvmStatic
    fun onPullStarted(context: Context, x: Float, width: Int) {
        notificationsOnly = isEnabled(context) && !isQsSide(context, x, width)
    }

    /** Opens the given side without a touch: Quick Settings or notifications. */
    @JvmStatic
    fun setSide(quickSettings: Boolean) {
        notificationsOnly = !quickSettings
    }

    /** Called when the user switches to Quick Settings or the shade closes. */
    @JvmStatic
    fun showQuickSettings() {
        notificationsOnly = false
    }

    private fun ensureWatching(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext ?: context
        appContext = app
        try {
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean, uri: Uri?) = refresh()
                }
            for (key in listOf(KEY_SWAP, KEY_QS_STYLE)) {
                app.contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(key), false, observer, UserHandle.USER_ALL)
            }
        } catch (_: Exception) {
            // Without the observer the values are still read once below.
        }
        refresh()
    }

    private fun refresh() {
        val context = appContext ?: return
        swapped = read(context, KEY_SWAP, 0) != 0
        qsStyle = read(context, KEY_QS_STYLE, 0)
        enabledState = enabled
    }

    private fun read(context: Context, key: String, def: Int): Int =
        try {
            Settings.Secure.getIntForUser(
                context.contentResolver, key, def, ActivityManager.getCurrentUser())
        } catch (_: Exception) {
            def
        }
}

/** Quick Settings styles that bring their own header: ColorOS, iOS and One UI. */
private val MIRACLO_LOOKS = setOf(1, 2, 4)
