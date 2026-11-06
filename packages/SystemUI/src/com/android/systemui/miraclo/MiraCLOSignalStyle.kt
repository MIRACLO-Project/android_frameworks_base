// SPDX-FileCopyrightText: MIRACLO
// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.miraclo

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.json.JSONObject

/**
 * The status bar signal style chosen in Settings › Icons (Android 17's own, ColorOS, iOS or
 * One UI). Settings stores it as the theme's icon pack; Android 17 draws the mobile signal in
 * code, so the bars read the style from here.
 */
object MiraCLOSignalStyle {
    const val STOCK = 0
    const val COLOROS = 1
    const val IOS = 2
    const val ONEUI = 3

    private const val CATEGORY = "android.theme.customization.icon_pack.android"

    fun read(context: Context): Int {
        val s =
            runCatching {
                    Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                    )
                }
                .getOrNull() ?: return STOCK
        val pack = runCatching { JSONObject(s).optString(CATEGORY, "") }.getOrDefault("")
        return when {
            pack.contains(".coloros.") -> COLOROS
            pack.contains(".ios.") -> IOS
            pack.contains(".oneui.") -> ONEUI
            else -> STOCK
        }
    }

    /** The current style, updated as soon as it changes in Settings. */
    @Composable
    fun current(): Int {
        val context = LocalContext.current
        val style = remember { mutableIntStateOf(read(context)) }
        DisposableEffect(context) {
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        style.intValue = read(context)
                    }
                }
            runCatching {
                context.contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES),
                    false,
                    observer,
                )
            }
            onDispose { runCatching { context.contentResolver.unregisterContentObserver(observer) } }
        }
        return style.intValue
    }
}
