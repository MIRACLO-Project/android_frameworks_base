/*
 * Copyright (C) 2026 MiraCLO
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

package com.android.systemui.qs.composefragment

import android.content.Intent
import android.os.SystemProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.android.settingslib.volume.domain.interactor.AudioVolumeInteractor
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.qs.panels.ui.compose.TileListener
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import kotlinx.coroutines.delay

/** Everything a prototype screen needs from the shade. */
internal class MiraProtoEnv(
    val dark: Boolean,
    val tiles: List<TileViewModel>,
    val mediaData: MediaData?,
    private val onOpenMedia: (android.app.PendingIntent) -> Unit,
    val brightness: MiraProtoLevel,
    val volume: MiraProtoLevel,
    val audio: AudioVolumeInteractor,
    val open: (Intent) -> Unit,
    val power: () -> Unit,
    val clock: Pair<String, String>,
    val stockRound: Boolean,
    val stockTileSize: Int,
    val stockEdit: () -> Unit,
    val buildLabel: String,
    /** Opens this style's own editor (Android's editor underneath). */
    val styleEdit: () -> Unit = stockEdit,
) {
    fun tile(key: String): TileViewModel? = tiles.firstOrNull { it.protoKey() == key }

    @Composable
    fun media(idleSong: String, idleArtist: String): MiraProtoMedia =
        rememberProtoMedia(mediaData, onOpenMedia, idleSong, idleArtist)
}

/** Background of each style: the real blur behind, under a fixed tint at the prototype's opacity. */
@Composable
private fun miraProtoTint(style: Int, dark: Boolean): Pair<Color, Color> =
    when (style) {
        MiraProtoStyle.COLOROS -> MiraColorOSInk(dark).let { it.base.copy(alpha = 0.82f) to it.scrim }
        MiraProtoStyle.IOS -> MiraIosInk(dark).let { it.base.copy(alpha = 0.74f) to it.scrim }
        MiraProtoStyle.ONEUI -> MiraOneUiInk(dark).let { it.base.copy(alpha = 0.88f) to it.scrim }
        else -> miraStockBackground().copy(alpha = 0.94f) to Color.Transparent
    }

/**
 * The MiraCLO Quick Settings of the separate shade, full screen (no status row, no toolbar),
 * drawn by the style picked in Mira Studio.
 */
@Composable
internal fun MiraProtoShade(
    style: Int,
    tiles: List<TileViewModel>,
    mediaData: MediaData?,
    onOpenMedia: (android.app.PendingIntent) -> Unit,
    brightness: MiraProtoLevel,
    volume: MiraProtoLevel,
    audio: AudioVolumeInteractor,
    open: (Intent) -> Unit,
    power: () -> Unit,
    stockRound: Boolean,
    stockTileSize: Int,
    stockEdit: () -> Unit,
    listening: () -> Boolean,
    styleEdit: () -> Unit = stockEdit,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    backgroundAlpha: () -> Float = { 1f },
) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    var clock by remember { mutableStateOf(miraProtoClock(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = miraProtoClock(context)
            delay(1000L * (60 - (System.currentTimeMillis() / 1000 % 60)).coerceAtLeast(1))
        }
    }
    TileListener(tiles, listening)
    val env =
        MiraProtoEnv(
            dark = dark,
            tiles = tiles,
            mediaData = mediaData,
            onOpenMedia = onOpenMedia,
            brightness = brightness,
            volume = volume,
            audio = audio,
            open = open,
            power = power,
            clock = clock,
            stockRound = stockRound,
            stockTileSize = stockTileSize,
            stockEdit = stockEdit,
            styleEdit = styleEdit,
            buildLabel = "MiraCLO " + SystemProperties.get("ro.miraclo.version", "1.0"),
        )
    val (base, scrim) = miraProtoTint(style, dark)
    // The tint covers the whole screen and fades in with the pull; only the controls move.
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = backgroundAlpha() }
                .background(base)
                .background(scrim)
        )
        MiraProtoFrame(contentModifier) {
            when (style) {
                MiraProtoStyle.COLOROS -> MiraColorOSQs(env)
                MiraProtoStyle.IOS -> MiraIosQs(env)
                MiraProtoStyle.ONEUI -> MiraOneUiQs(env)
                else -> MiraStockQs(env)
            }
        }
    }
}
