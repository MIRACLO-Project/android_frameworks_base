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

package com.android.systemui.qs.composefragment

import androidx.compose.runtime.remember
import com.android.systemui.qs.panels.ui.compose.infinitegrid.MiraTileSkin
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel

/** Stock palette: Material You roles (the only style that follows the theme picker). */
private class StockInk(
    val bg: Color,
    val fg: Color,
    val fg2: Color,
    val track: Color,
    val off: Color,
    val on: Color,
    val onFg: Color,
    val bubbleOff: Color,
)

@Composable
private fun stockInk(): StockInk {
    val s = MaterialTheme.colorScheme
    return StockInk(
        bg = s.surfaceContainerLowest,
        fg = s.onSurface,
        fg2 = s.onSurfaceVariant,
        track = s.surfaceContainerHigh,
        off = s.surfaceContainer,
        on = s.primary,
        onFg = s.onPrimary,
        bubbleOff = s.surfaceContainerHighest,
    )
}

/** Stock background colour for the panel (Material You). */
@Composable internal fun miraStockBackground(): Color = MaterialTheme.colorScheme.surfaceContainerLowest

/**
 * Stock Quick Settings, as in the approved prototype: clock and date on the left with the
 * carrier on the right, brightness, six wide tiles and the round tiles under them (Mira Studio:
 * round or square, 56-88dp), the build line with page dots and edit, and settings and power at
 * the bottom right. Edit opens the system tile editor.
 */
@Composable
internal fun MiraStockQs(env: MiraProtoEnv) {
    val c = stockInk()
    val size = env.stockTileSize.coerceIn(56, 88)
    val round = env.stockRound
    val gap = (6 + size / 18).dp
    val h = size.dp
    val tiles = env.tiles.filter { it.spec.spec !in MIRA_PROTO_EXCLUDED }
    val perPage = 14
    val pages = tiles.chunked(perPage).ifEmpty { listOf(emptyList()) }
    val pager = rememberPagerState(0) { pages.size }

    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.offset(18.dp, 28.dp).width(354.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(env.clock.first, color = c.fg, fontSize = 34.sp, fontWeight = FontWeight.Normal, lineHeight = 36.sp)
                Text(env.clock.second, color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Text(miraProtoCarrier(), color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }

        Column(
            Modifier.offset(8.dp, 104.dp).width(374.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            // Brightness.
            val level = env.brightness
            val local = rememberProtoLocal(level.value)
            val v = local.floatValue
            Box(
                Modifier.fillMaxWidth()
                    .height(40.dp)
                    .background(c.track, RoundedCornerShape(20.dp))
                    .protoSliderInput({ v }, false, { local.floatValue = it }, level.set)
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(v.coerceIn(0.001f, 1f)).background(c.on, RoundedCornerShape(20.dp)))
                androidx.compose.material3.Icon(
                    painterResource(BrightnessSliderViewModel.getIconForPercentage(v * 100f)),
                    null,
                    tint = if (v > 0.9f) c.onFg else c.fg,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(24.dp),
                )
            }

            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { p ->
                val page = pages[p]
                val wide = page.take(6)
                val small = page.drop(6)
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    wide.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            row.forEach { StockWideTile(c, it, Modifier.weight(1f), size, round) }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    small.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            row.forEach { t ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    StockSmallTile(c, t, size, round)
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(env.buildLabel, color = c.fg2, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(pages.size.coerceAtLeast(1)) { i ->
                        val sel = i == pager.currentPage
                        Box(Modifier.size(if (sel) 14.dp else 6.dp, 6.dp).background(if (sel) c.fg else c.fg2, RoundedCornerShape(3.dp)))
                    }
                }
                Box(Modifier.size(40.dp).protoTap(onTap = env.stockEdit), contentAlignment = Alignment.Center) {
                    MiraProtoGlyph(MiraCLOIcons.Edit, c.fg, 22.dp)
                }
            }
        }

        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 30.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(48.dp).background(c.off, CircleShape).protoTap { env.open(miraProtoIntent(Settings.ACTION_SETTINGS)) },
                contentAlignment = Alignment.Center,
            ) {
                MiraProtoGlyph(MiraCLOIcons.Settings, c.fg, 22.dp)
            }
            Box(
                Modifier.size(48.dp).background(c.on, CircleShape).protoTap(onTap = env.power),
                contentAlignment = Alignment.Center,
            ) {
                MiraProtoGlyph(MiraCLOIcons.Power, c.onFg, 22.dp)
            }
        }
    }
}

@Composable
private fun StockWideTile(c: StockInk, tile: TileViewModel, modifier: Modifier, size: Int, round: Boolean) {
    // Android's real tile (tap, long-press, details, dialogs), drawn in the Stock look.
    val shape = RoundedCornerShape(if (round) (size / 2).dp else (size * 0.28f).dp)
    val skin = remember(c, round, size) {
        MiraTileSkin(
            shape = shape, onBackground = c.off, offBackground = c.off,
            onContent = c.onFg, offContent = c.fg,
            bubbleOn = c.on, bubbleOff = c.bubbleOff, label = c.fg,
        )
    }
    MiraRealTile(tile, skin, iconOnly = false, modifier = modifier.height(size.dp))
}

@Composable
private fun StockSmallTile(c: StockInk, tile: TileViewModel, size: Int, round: Boolean) {
    val shape = if (round) CircleShape else RoundedCornerShape((size * 0.28f).dp)
    val skin = remember(c, round, size) {
        MiraTileSkin(shape = shape, onBackground = c.on, offBackground = c.off, onContent = c.onFg, offContent = c.fg)
    }
    MiraRealTile(
        tile, skin, iconOnly = true,
        modifier = if (round) Modifier.size(size.dp) else Modifier.fillMaxWidth().height(size.dp),
    )
}
