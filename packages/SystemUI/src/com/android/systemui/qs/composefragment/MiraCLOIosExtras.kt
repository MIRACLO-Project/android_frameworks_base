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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.compose.animation.scene.ContentScope
import com.android.settingslib.volume.domain.interactor.AudioVolumeInteractor
import com.android.systemui.qs.panels.ui.compose.TileListener
import com.android.systemui.qs.panels.ui.compose.infinitegrid.InfiniteGridLayout
import com.android.systemui.qs.panels.ui.compose.infinitegrid.LocalMiraCLOTileDetails
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel

/** The iOS look's extra state: the expanded connectivity module and the current page. */
internal class MiraCLOIosState {
    var expanded by mutableStateOf(false)
    var page by mutableIntStateOf(0)

    fun reset() {
        expanded = false
        page = 0
    }

    companion object {
        const val PAGES = 3
    }
}

/** Null outside the iOS look. */
internal val LocalMiraCLOIosState = staticCompositionLocalOf<MiraCLOIosState?> { null }

/**
 * Calls [onLongPress] when the finger rests on the element a little before the system long
 * press, so it wins over the long press of the tiles inside it. Watches the initial pass and
 * only consumes the rest of the gesture once it fired.
 */
internal fun Modifier.miracloEarlyLongPress(onLongPress: () -> Unit): Modifier =
    pointerInput(Unit) {
        val timeout = viewConfiguration.longPressTimeoutMillis * 3 / 4
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val released =
                withTimeoutOrNull(timeout) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if ((change.position - down.position).getDistance() > slop) break
                    }
                    true
                }
            if (released == null) {
                onLongPress()
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                    if (event.changes.none { it.pressed }) break
                }
            }
        }
    }

/**
 * The connectivity module grown out of its block: big round toggles with their names and state,
 * over a dimmed panel. A tap outside closes it.
 */
@Composable
internal fun ContentScope.MiraCLOIosConnectivityModule(
    tiles: List<TileViewModel>,
    infiniteGridLayout: InfiniteGridLayout,
    listening: () -> Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        MiraCLOIosConnectivityCard(tiles, infiniteGridLayout, listening, Modifier.padding(26.dp))
    }
}

@Composable
private fun ContentScope.MiraCLOIosConnectivityCard(
    tiles: List<TileViewModel>,
    infiniteGridLayout: InfiniteGridLayout,
    listening: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val glass = miracloGlass()
    TileListener(tiles, listening)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .glassSurface(glass, RoundedCornerShape(40.dp))
                // Taps inside the card stay inside it.
                .pointerInput(Unit) { detectTapGestures {} }
                .padding(start = 18.dp, end = 18.dp, top = 26.dp, bottom = 22.dp),
        verticalArrangement = spacedBy(22.dp),
    ) {
        CompositionLocalProvider(LocalMiraCLOTileDetails provides true) {
            tiles.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { tile ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            with(infiniteGridLayout) {
                                MiraCLOWideTile(
                                    tile = tile,
                                    listening = listening,
                                    modifier = Modifier.fillMaxWidth(),
                                    iconOnly = true,
                                )
                            }
                        }
                    }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * The iOS pages: the controls, Now Playing and Connectivity, one above the other. Swiping up
 * moves down a page, swiping down moves back; the column on the right shows where you are and
 * jumps to a page when tapped.
 */
@Composable
internal fun ContentScope.MiraCLOIosPages(
    state: MiraCLOIosState,
    connectivity: List<TileViewModel>,
    infiniteGridLayout: InfiniteGridLayout,
    audioVolumeInteractor: AudioVolumeInteractor,
    showMedia: Boolean,
    media: @Composable (Modifier) -> Unit,
    listening: () -> Boolean,
    modifier: Modifier = Modifier,
    controls: @Composable () -> Unit,
) {
    val view = LocalView.current
    val currentState by rememberUpdatedState(state)
    // The page column sits in the right margin (centred 22dp past the controls, 176dp down),
    // so the controls keep their full width.
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier.fillMaxWidth().pointerInput(Unit) {
                    var travelled = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            travelled = 0f
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            travelled += amount
                        },
                        onDragEnd = {
                            val threshold = 48.dp.toPx()
                            val s = currentState
                            if (travelled < -threshold && s.page < MiraCLOIosState.PAGES - 1) {
                                s.page++
                            } else if (travelled > threshold && s.page > 0) {
                                s.page--
                            }
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                        },
                        onDragCancel = {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                        },
                    )
                }
        ) {
            AnimatedContent(
                targetState = state.page,
                transitionSpec = {
                    val down = targetState > initialState
                    (slideInVertically(tween(320)) { h -> if (down) h / 3 else -h / 3 } +
                        fadeIn(tween(220)))
                        .togetherWith(
                            slideOutVertically(tween(320)) { h -> if (down) -h / 3 else h / 3 } +
                                fadeOut(tween(160))
                        )
                },
                label = "MiraCLOIosPage",
            ) { page ->
                when (page) {
                    0 -> controls()
                    1 ->
                        MiraCLOIosNowPlaying(
                            audioVolumeInteractor = audioVolumeInteractor,
                            showMedia = showMedia,
                            media = media,
                        )
                    else ->
                        MiraCLOIosConnectivityCard(
                            tiles = connectivity,
                            infiniteGridLayout = infiniteGridLayout,
                            listening = listening,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                }
            }
        }
        MiraCLOIosPageColumn(
            page = state.page,
            onPage = { state.page = it },
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 36.dp, y = 168.dp),
        )
    }
}

/** Now Playing: the media card large, with the volume slider underneath. */
@Composable
private fun MiraCLOIosNowPlaying(
    audioVolumeInteractor: AudioVolumeInteractor,
    showMedia: Boolean,
    media: @Composable (Modifier) -> Unit,
) {
    val glass = miracloGlass()
    val shape = RoundedCornerShape(40.dp)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = spacedBy(16.dp),
    ) {
        if (showMedia) {
            media(Modifier.fillMaxWidth().height(340.dp).clip(shape))
        } else {
            Box(
                Modifier.fillMaxWidth().height(340.dp).glassSurface(glass, shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Not Playing",
                    color = glass.secondaryText,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        MiraCLOVolumeSlider(
            audioVolumeInteractor = audioVolumeInteractor,
            glass = glass,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            vertical = false,
        )
    }
}

/** The page column: a heart for the controls, a note for Now Playing, a tower for connectivity. */
@Composable
private fun MiraCLOIosPageColumn(page: Int, onPage: (Int) -> Unit, modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier.width(28.dp),
        verticalArrangement = spacedBy(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MiraCLOIosPageIcons.forEachIndexed { index, (icon, label) ->
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier =
                    Modifier.size(28.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Tab, onClickLabel = label) { onPage(index) }
                        .padding(6.5.dp)
                        .alpha(if (index == page) 1f else 0.4f),
            )
        }
    }
}

private val MiraCLOIosPageIcons: List<Pair<ImageVector, String>> =
    listOf(
        pageIcon(
            "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 " +
                "4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 " +
                "-8.55,11.54L12,21.35z"
        ) to "Controls",
        pageIcon(
            "M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 " +
                "4,-4V7h4V3h-6z"
        ) to "Now Playing",
        pageIcon(
            "M7.3,14.7l1.2,-1.2c-1,-1 -1.5,-2.3 -1.5,-3.5 0,-1.3 0.5,-2.6 1.5,-3.5L7.3,5.3c-1.3,1.3 " +
                "-2,3 -2,4.7S6,13.4 7.3,14.7zM19.1,2.9l-1.2,1.2c1.6,1.6 2.4,3.8 2.4,5.9 0,2.1 -0.8," +
                "4.3 -2.4,5.9l1.2,1.2c2,-2 2.9,-4.5 2.9,-7.1 0,-2.6 -1,-5.1 -2.9,-7.1zM6.1,4.1L4.9," +
                "2.9C3,4.9 2,7.4 2,10c0,2.6 1,5.1 2.9,7.1l1.2,-1.2c-1.6,-1.6 -2.4,-3.8 -2.4,-5.9 0," +
                "-2.1 0.8,-4.3 2.4,-5.9zM16.7,14.7c1.3,-1.3 2,-3 2,-4.7 -0.1,-1.7 -0.7,-3.4 -2,-4.7l" +
                "-1.2,1.2c1,1 1.5,2.3 1.5,3.5 0,1.3 -0.5,2.6 -1.5,3.5l1.2,1.2zM14.5,10c0,-1.38 -1.12," +
                "-2.5 -2.5,-2.5S9.5,8.62 9.5,10c0,0.76 0.34,1.42 0.87,1.88L7,22h2l0.67,-2h4.67L15,22" +
                "h2l-3.37,-10.12c0.53,-0.46 0.87,-1.12 0.87,-1.88zM10.33,18L12,13l1.67,5h-3.34z"
        ) to "Connectivity",
    )

private fun pageIcon(path: String): ImageVector =
    ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
        .build()
