/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.compose.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.mutableIntStateOf

/**
 * Mira Studio › Look & feel › Animations: which Material 3 motion scheme System UI uses.
 *
 *  - Standard: calm and precise, almost no bounce (spatial spring 0.9 / 700).
 *  - Expressive (default): lively, with a soft overshoot (0.8 / 380).
 *  - Snappy: the standard scheme's fast springs everywhere (0.9 / 1400).
 *
 * The speed of every scheme follows the system animation scale, which the same page sets.
 * The choice is snapshot state, so every PlatformTheme picks up a change without a restart.
 */
object MiraMotion {
    const val KEY_STYLE = "miraclo_motion_style"
    const val STANDARD = 0
    const val EXPRESSIVE = 1
    const val SNAPPY = 2

    private val style = mutableIntStateOf(EXPRESSIVE)
    @Volatile private var observing = false

    private val Standard = MotionScheme.standard()
    private val Expressive = MotionScheme.expressive()
    private val Snappy: MotionScheme =
        object : MotionScheme by Standard {
            override fun <T> defaultSpatialSpec() = Standard.fastSpatialSpec<T>()

            override fun <T> slowSpatialSpec() = Standard.defaultSpatialSpec<T>()

            override fun <T> defaultEffectsSpec() = Standard.fastEffectsSpec<T>()

            override fun <T> slowEffectsSpec() = Standard.defaultEffectsSpec<T>()
        }

    /** The default spatial spring of the chosen style (for code that is not composable). */
    fun <T> spatialSpec(context: Context): androidx.compose.animation.core.FiniteAnimationSpec<T> =
        scheme(context).defaultSpatialSpec()

    /** The scheme to use now. Reading it inside a composable recomposes on a change. */
    fun scheme(context: Context): MotionScheme {
        if (!observing) observe(context.applicationContext ?: context)
        return when (style.intValue) {
            STANDARD -> Standard
            SNAPPY -> Snappy
            else -> Expressive
        }
    }

    @Synchronized
    private fun observe(context: Context) {
        if (observing) return
        observing = true
        val resolver = context.contentResolver
        val main = Handler(Looper.getMainLooper())
        fun read() {
            style.intValue =
                runCatching {
                        Settings.Secure.getIntForUser(
                            resolver, KEY_STYLE, EXPRESSIVE, UserHandle.USER_CURRENT)
                    }
                    .getOrDefault(EXPRESSIVE)
        }
        runCatching {
            resolver.registerContentObserver(
                Settings.Secure.getUriFor(KEY_STYLE),
                false,
                object : ContentObserver(main) {
                    override fun onChange(selfChange: Boolean) = read()
                },
                UserHandle.USER_ALL,
            )
        }
        if (Looper.myLooper() == Looper.getMainLooper()) read() else main.post { read() }
    }
}
