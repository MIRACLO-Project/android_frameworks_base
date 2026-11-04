/*
 * Copyright (C) 2024 The Android Open Source Project
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

@file:OptIn(ExperimentalFoundationApi::class)

package com.android.systemui.qs.panels.ui.compose.infinitegrid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.android.systemui.miraclo.miracloRefraction
import android.content.Context
import android.content.res.Resources
import android.os.Trace
import android.service.quicksettings.Tile.STATE_ACTIVE
import android.service.quicksettings.Tile.STATE_INACTIVE
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.trace
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.compose.animation.Expandable
import com.android.compose.animation.bounceable
import com.android.compose.animation.rememberExpandableController
import com.android.compose.animation.scene.ContentScope
import com.android.compose.modifiers.thenIf
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.mechanics.compose.modifier.verticalFadeContentReveal
import com.android.mechanics.compose.modifier.verticalTactileSurfaceReveal
import com.android.mechanics.effects.VerticalTactileSurfaceRevealEffect
import com.android.systemui.Flags
import com.android.systemui.animation.Expandable
import com.android.systemui.animation.TransitionAnimator.Companion.dynamicTargetResolutionEnabled
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.haptics.msdl.qs.TileHapticsViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.qs.flags.QsDetailedView
import com.android.systemui.qs.panels.ui.compose.BounceableInfo
import com.android.systemui.qs.panels.ui.compose.Tooltip
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.ActiveIconCornerRadius
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.ActiveTileCornerRadius
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.InactiveIconCornerRadius
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.InactiveTileCornerRadius
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.TileHeight
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.ToggleTargetSize
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.longPressLabelMoreDetails
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.longPressLabelSettings
import com.android.systemui.qs.panels.ui.viewmodel.AccessibilityUiState
import com.android.systemui.qs.panels.ui.viewmodel.DetailsViewModel
import com.android.systemui.qs.panels.ui.viewmodel.IconProvider
import com.android.systemui.qs.panels.ui.viewmodel.TileUiState
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.toIconProvider
import com.android.systemui.qs.panels.ui.viewmodel.toUiState
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.ui.composable.QuickSettingsShade
import com.android.systemui.qs.ui.compose.borderOnFocus
import com.android.systemui.res.R
import kotlinx.coroutines.CoroutineScope
import platform.test.motion.compose.values.MotionTestValueKey
import platform.test.motion.compose.values.motionTestValues

private val TileViewModel.traceName
    get() = spec.toString().takeLast(Trace.MAX_SECTION_NAME_LEN)

/** MIRACLO: ColorOS-style Quick Settings (glass tiles, round icon tiles with labels below). */
internal val LocalMiraCLOQsStyle = compositionLocalOf { false }

/** MIRACLO: Liquid Glass (Settings > Display): clearer glass with a bright specular edge. */
internal val LocalMiraCLOLiquidGlass = compositionLocalOf { false }

/**
 * MIRACLO Quick Settings looks, the values of Settings.Secure "miraclo_qs_style" (Settings >
 * Display > Quick Settings style). Each look has its own tile colours and shapes.
 */
internal object MiraCLOLook {
    const val COLOROS = 1
    const val IOS = 2
    const val ONEUI = 4
}

/** MIRACLO: the Quick Settings look in use (see [MiraCLOLook]). */
internal val LocalMiraCLOLook = compositionLocalOf { MiraCLOLook.COLOROS }

/**
 * MIRACLO: stock Quick Settings with round tiles (Mira Studio > Quick Settings > Round tiles).
 * Small tiles become circles and large tiles become pills.
 */
internal val LocalMiraCLORoundTiles = compositionLocalOf { false }

/** MIRACLO: stock tile height chosen in Mira Studio, or null for the stock size. */
internal val LocalMiraCLOTileSize = compositionLocalOf<Dp?> { null }

/** MIRACLO: round tiles without the label underneath (iOS look, connectivity block). */
internal val LocalMiraCLOHideTileLabels = compositionLocalOf { false }

/** MIRACLO: round tiles show their label and state underneath (the iOS connectivity module). */
internal val LocalMiraCLOTileDetails = compositionLocalOf { false }

/**
 * MIRACLO: the look of a real QS tile inside a MIRACLO QS style (from the approved mocks). Only the
 * drawing changes; clicks, long-press, dialogs, haptics and accessibility stay the stock tile's.
 */
data class MiraTileSkin(
    val shape: androidx.compose.ui.graphics.Shape,
    val onBackground: Color,
    val offBackground: Color,
    val onContent: Color,
    val offContent: Color,
    val unavailableAlpha: Float = 0.45f,
    /** Large tiles: the icon's own bubble (null = same as the background). */
    val bubbleOn: Color? = null,
    val bubbleOff: Color? = null,
    /** Large tiles: label colour (null = the content colour). */
    val label: Color? = null,
    /** Large tiles: draw the round icon bubble on every tile, not only on dual-target ones. */
    val bubbleAlways: Boolean = false,
    /** Large tiles: the bubble's diameter (null = stock size). */
    val bubbleSize: Dp? = null,
    /** Large tiles: extra space between the tile's edge and the bubble. */
    val bubbleInset: Dp = 0.dp,
)

/** MIRACLO skins: the large tile's icon bubble is a circle. */
private val MiraSkinBubbleShape = RoundedCornerShape(percent = 50)

/** Set by a MIRACLO QS style around each tile it places. */
val LocalMiraTileSkin = compositionLocalOf<MiraTileSkin?> { null }

/** iOS-style system colours. */
private val MiraCLOIosBlue = Color(0xFF0A84FF)
private val MiraCLOIosGreen = Color(0xFF30D158)
private val MiraCLOIosOrange = Color(0xFFFF9F0A)
private val MiraCLOIosPurple = Color(0xFF5E5CE6)

/** One UI-style blue. */
private val MiraCLOOneUiBlue = Color(0xFF3E91FF)

private val MIRACLO_GREEN_TILES = setOf("cell", "mobile_data", "mobiledata", "hotspot")
private val MIRACLO_ORANGE_TILES = setOf("airplane", "battery", "flashlight")
private val MIRACLO_PURPLE_TILES = setOf("dnd", "modes")

/** Liquid Glass edge: brightest at the top-left and bottom-right, like light catching glass. */
internal val MiraCLOLiquidRim =
    Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.95f),
        0.22f to Color.White.copy(alpha = 0.25f),
        0.5f to Color.White.copy(alpha = 0.04f),
        0.78f to Color(0xFFBED7FF).copy(alpha = 0.35f),
        1f to Color.White.copy(alpha = 0.85f),
    )

/** Liquid Glass inner light along the top of a shape. */
internal val MiraCLOLiquidHighlight =
    Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.18f),
        0.5f to Color.Transparent,
    )

/** Glass rim of MIRACLO tiles. Created once instead of on every frame of the QS animation. */
private val MiraCLOTileRim =
    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.04f)))

private val MiraCLOBlue = Color(0xFF1A7CFF)

/** ColorOS 17 colours of the active toggles. */
private fun miracloColorOSAccent(spec: String): Color =
    when (spec) {
        "cell", "mobile_data", "mobiledata", "saver", "battery", "share" -> Color(0xFF2FB457)
        "airplane" -> Color(0xFFFF9500)
        "rotation", "rotate", "screenrecord", "record" -> Color(0xFFFF3B30)
        "dnd", "modes" -> Color(0xFF5E5CE6)
        "flashlight" -> Color(0xFFE69500)
        "hotspot" -> Color(0xFFC99400)
        "dark" -> Color(0xFF111214)
        else -> Color(0xFF1A7CFF)
    }

/** MIRACLO: solid tile colour of the dark QS without Liquid Glass. */
internal val MiraCLOSolidTile = Color(0xFF1E1F23)
private val MiraCLOSolidBubble = Color(0xFF2C2D32)

/**
 * This composable function is responsible for rendering a tile based on the provided
 * [TileViewModel]. It handles different states of the tile (e.g., available, unavailable),
 * interactions (click, long click), and visual styles (icon only or large tile).
 *
 * @param tile The [TileViewModel] containing the data and logic for the tile.
 * @param iconOnly A boolean indicating whether to display only the icon of the tile or the full
 *   tile content (false for large tiles).
 * @param squishiness The float value representing the current squishiness factor of the tile, used
 *   for animations.
 * @param coroutineScope The [CoroutineScope] to launch coroutines for animations.
 * @param tileHapticsViewModelFactory A factory for creating a [TileHapticsViewModel] instance, used
 *   for haptic feedback.
 * @param modifier An optional [Modifier] to be applied to the root composable of the tile.
 * @param isVisible Whether the tile is currently visible. Defaults to true.
 * @param requestToggleTextFeedback A lambda function that is invoked when a toggleable icon only
 *   tile is clicked, used to request the feedback text.
 * @param detailsViewModel An optional [DetailsViewModel] used to handle navigation to a detailed
 *   view when a tile is clicked, if applicable.
 * @param enableRevealEffect If `true`, the tiles will animate using the reveal animation.
 */
@Composable
fun ContentScope.Tile(
    tile: TileViewModel,
    iconOnly: Boolean,
    squishiness: () -> Float,
    coroutineScope: CoroutineScope,
    bounceableInfo: BounceableInfo,
    tileHapticsViewModelFactory: TileHapticsViewModel.Factory,
    modifier: Modifier = Modifier,
    isVisible: () -> Boolean = { true },
    requestToggleTextFeedback: (TileSpec) -> Unit = {},
    detailsViewModel: DetailsViewModel?,
    enableRevealEffect: Boolean = false,
) {
    trace(tile.traceName) {
        val currentBounceableInfo by rememberUpdatedState(bounceableInfo)
        val resources = resources()

        /*
         * Use produce state because [QSTile.State] doesn't have well defined equals (due to
         * inheritance). This way, even if tile.state changes, uiState may not change and lead to
         * recomposition.
         */
        val uiState by
            produceState(tile.currentState.toUiState(resources), tile, resources) {
                tile.state.collect { value = it.toUiState(resources) }
            }
        val isClickable = uiState.handlesMainClick

        val icon by
            produceState(tile.currentState.toIconProvider(), tile) {
                tile.state.collect { value = it.toIconProvider() }
            }

        val miracloStyle = LocalMiraCLOQsStyle.current
        val liquidGlass = LocalMiraCLOLiquidGlass.current
        // MIRACLO: the refracting lens is kept for the large iOS blocks; on every small tile it
        // turned the wallpaper into blobs, so tiles use the frosted glass instead.
        val miracloRefraction = false
        val miracloLightPanel = MaterialTheme.colorScheme.surface.luminance() >= 0.5f
        val miracloLookNow = LocalMiraCLOLook.current
        val miraSkin = LocalMiraTileSkin.current
        val colors =
            if (miraSkin != null) {
                val on = uiState.visualState == STATE_ACTIVE
                val off = uiState.visualState == android.service.quicksettings.Tile.STATE_UNAVAILABLE
                val bg = if (on) miraSkin.onBackground else miraSkin.offBackground
                val fg = (if (on) miraSkin.onContent else miraSkin.offContent)
                    .let { if (off) it.copy(alpha = it.alpha * miraSkin.unavailableAlpha) else it }
                val bubble = (if (on) miraSkin.bubbleOn else miraSkin.bubbleOff) ?: bg
                val lbl = miraSkin.label ?: fg
                TileColors(background = bg, iconBackground = bubble, label = lbl,
                    secondaryLabel = lbl.copy(alpha = lbl.alpha * 0.85f), icon = fg)
            } else if (miracloStyle) TileDefaults.miracloColorsForState(uiState, iconOnly, tile.spec.spec)
            else TileDefaults.getColorForState(uiState, iconOnly)
        val hapticsViewModel: TileHapticsViewModel =
            rememberViewModel(traceName = "TileHapticsViewModel") {
                tileHapticsViewModelFactory.create(tile)
            }

        // TODO(b/361789146): Draw the shapes instead of clipping
        val stockShape by TileDefaults.animateTileShapeAsState(uiState)
        val tileShape = miraSkin?.shape ?: stockShape
        // MIRACLO: SurfaceFlinger draws the refracting glass behind see-through tiles.
        val refractionModifier =
            Modifier.miracloRefraction(
                tileShape, miracloRefraction && colors.background.alpha < 0.99f)
        // MIRACLO style: icon-only tiles are drawn as perfect circles.
        val miracloRoundTile = (miracloStyle || LocalMiraCLORoundTiles.current) && iconOnly
        val animatedColor by animateColorAsState(colors.background, label = "QSTileBackgroundColor")
        val isDualTarget = uiState.handlesToggleClick
        val interactionSource = remember { MutableInteractionSource() }

        val surfaceRevealModifier: Modifier
        val contentRevealModifier: Modifier
        if (enableRevealEffect) {
            val marginBottom =
                with(LocalDensity.current) { QuickSettingsShade.Dimensions.VerticalPadding.toPx() }

            val animatedCornerRadius by animateDpAsState(TileDefaults.tileRadius(uiState))

            val inactiveCornerRadius = InactiveIconCornerRadius
            surfaceRevealModifier =
                Modifier.verticalTactileSurfaceReveal(
                    deltaY = marginBottom,
                    effectSpec =
                        remember(inactiveCornerRadius) {
                            VerticalTactileSurfaceRevealEffect(
                                maxCornerSize = { animatedCornerRadius },
                                phase1MarginX = inactiveCornerRadius,
                            )
                        },
                    label = tile.traceName,
                )

            contentRevealModifier =
                Modifier.verticalFadeContentReveal(deltaY = marginBottom, label = tile.traceName)
        } else {
            surfaceRevealModifier = Modifier
            contentRevealModifier = Modifier
        }

        val expandable =
            if (dynamicTargetResolutionEnabled()) tile.expandable
            else remember { Expandable(mutableSetOf()) }
        Tooltip(
            text = uiState.label,
            modifier = modifier,
            enabled = Flags.enableQsTileTooltips(),
        ) { tooltipModifier ->
            val tileBody: @Composable (Modifier) -> Unit = { modifier ->
            val roundTileSize = TileHeight
            TileExpandable(
                expandable = expandable,
                color = { animatedColor },
                shape = tileShape,
                squishiness = squishiness,
                hapticsViewModel = hapticsViewModel,
                modifier =
                    modifier
                        // MIRACLO: keep round tiles square, centred in the full grid slot.
                        .thenIf(miracloRoundTile) {
                            Modifier.fillMaxWidth()
                                .height(roundTileSize)
                                .wrapContentSize(Alignment.Center)
                                .widthIn(max = roundTileSize)
                                .aspectRatio(1f)
                        }
                        .then(surfaceRevealModifier)
                        .then(refractionModifier)
                        .thenIf(miracloStyle) {
                            if (miracloLookNow == MiraCLOLook.ONEUI) {
                                // One UI cards are flat tinted glass, with no rim at all.
                                Modifier
                            } else if (miracloLookNow == MiraCLOLook.COLOROS && liquidGlass) {
                                // ColorOS: frosted glass with a thin bright rim and a soft light
                                // along the top-left edge.
                                Modifier.background(MiraCLOLiquidHighlight, tileShape)
                                    .border(
                                        width = 1.dp,
                                        color =
                                            Color.White.copy(
                                                alpha = if (miracloLightPanel) 0.95f else 0.18f
                                            ),
                                        shape = tileShape,
                                    )
                            } else if (miracloRefraction) {
                                // The rim and its light come from the refraction shader.
                                Modifier
                            } else if (liquidGlass) {
                                Modifier.background(MiraCLOLiquidHighlight, tileShape)
                                    .border(width = 1.3.dp, brush = MiraCLOLiquidRim, shape = tileShape)
                            } else if (miracloLightPanel) {
                                Modifier.border(width = 1.dp, brush = MiraCLOTileRim, shape = tileShape)
                            } else {
                                // Dark, no Liquid Glass: flat solid tiles, no rim.
                                Modifier
                            }
                        }
                        .borderOnFocus(
                            color = MaterialTheme.colorScheme.secondary,
                            stockShape.topEnd,
                        )
                        .sysuiResTag("tile_expandable")
                        .fillMaxWidth()
                        .bounceable(
                            currentBounceableInfo.bounceable,
                            currentBounceableInfo.previousTile,
                            currentBounceableInfo.nextTile,
                            orientation = Orientation.Horizontal,
                            bounceEnd = currentBounceableInfo.bounceEnd,
                            interactionSource = interactionSource,
                        ),
            ) { expandable ->
                // Use main click on long press for small, available dual target tiles.
                // Open settings otherwise.
                val useLongClickToSettings = !(iconOnly && isDualTarget && isClickable)
                val longClick: (() -> Unit)? =
                    {
                            hapticsViewModel.setTileInteractionState(
                                TileHapticsViewModel.TileInteractionState.LONG_CLICKED
                            )

                            if (useLongClickToSettings) {
                                tile.settingsClick(expandable)
                            } else {
                                val hasDetails =
                                    QsDetailedView.isEnabled &&
                                        detailsViewModel?.onTileClicked(tile.spec) == true
                                if (!hasDetails) {
                                    tile.mainClick(expandable)
                                }
                            }
                        }
                        .takeIf { !useLongClickToSettings || uiState.handlesSettingsClick }

                // Bounce the tile's container if it is toggleable and is not a large
                // dual target tile. These don't toggle on main click.
                val bounceContainer = uiState.isToggleable && (iconOnly || !isDualTarget)
                TileContainer(
                    interactionSource = interactionSource.takeIf { bounceContainer },
                    onClick = onClick@{
                            if (!isClickable) return@onClick

                            if (iconOnly && isDualTarget) {
                                tile.toggleClick()
                            } else {
                                val hasDetails =
                                    QsDetailedView.isEnabled &&
                                        detailsViewModel?.onTileClicked(tile.spec) == true
                                if (hasDetails) return@onClick

                                // For those tile's who doesn't have a detailed view, process with
                                // their `onClick` behavior.
                                tile.mainClick(expandable)
                            }

                            // Side effects of the click
                            hapticsViewModel.setTileInteractionState(
                                TileHapticsViewModel.TileInteractionState.CLICKED
                            )

                            coroutineScope.launch {
                                // Bounce the content of the tile if we're not animating the
                                // container.
                                if (!bounceContainer) {
                                    currentBounceableInfo.bounceable.animateContentBounce(iconOnly)
                                }
                            }
                            if (uiState.isToggleable && iconOnly) {
                                // And show footer text feedback for icons
                                requestToggleTextFeedback(tile.spec)
                            }
                        },
                    onLongClick = longClick,
                    accessibilityUiState = uiState.accessibilityUiState,
                    iconOnly = iconOnly,
                    isDualTarget = isDualTarget,
                    modifier = contentRevealModifier,
                ) {
                    val iconProvider: Context.() -> Icon = { getTileIcon(icon = icon) }
                    if (iconOnly) {
                        SmallTileContent(
                            iconProvider = iconProvider,
                            color = colors.icon,
                            modifier =
                                Modifier.align(Alignment.Center).bounceScale {
                                    currentBounceableInfo.bounceable.iconBounceScale
                                },
                        )
                    } else {
                        val iconShape by TileDefaults.animateIconShapeAsState(uiState)
                        val secondaryClick: (() -> Unit)? =
                            {
                                    hapticsViewModel.setTileInteractionState(
                                        TileHapticsViewModel.TileInteractionState.CLICKED
                                    )
                                    tile.toggleClick()
                                }
                                .takeIf { isDualTarget }
                        LargeTileContent(
                            label = uiState.label,
                            secondaryLabel = uiState.secondaryLabel,
                            iconProvider = iconProvider,
                            sideDrawable = uiState.sideDrawable,
                            colors = colors,
                            iconShape = if (miraSkin != null) MiraSkinBubbleShape else iconShape,
                            alwaysBubble = miraSkin?.bubbleAlways == true,
                            iconBoxSize = miraSkin?.bubbleSize,
                            iconBoxStartInset = miraSkin?.bubbleInset ?: 0.dp,
                            toggleClick = secondaryClick,
                            onLongClick = longClick,
                            accessibilityUiState = uiState.accessibilityUiState,
                            squishiness = squishiness,
                            isVisible = isVisible,
                            textScale = { currentBounceableInfo.bounceable.textBounceScale },
                            modifier =
                                Modifier.largeTilePadding(
                                    isDualTarget = uiState.handlesSettingsClick
                                ),
                        )
                    }
                }
            }
            }
            if (miracloStyle && iconOnly && LocalMiraCLOTileDetails.current) {
                MiraCLOLabeledIconTile(
                    label = uiState.label,
                    labelColor = colors.label,
                    secondaryLabel = uiState.secondaryLabel,
                    secondaryLabelColor = colors.secondaryLabel,
                    modifier = tooltipModifier,
                ) {
                    tileBody(Modifier)
                }
            } else if (miracloStyle && iconOnly &&
                (LocalMiraCLOLook.current == MiraCLOLook.IOS || LocalMiraCLOHideTileLabels.current)
            ) {
                // iOS look: round toggles without labels.
                tileBody(tooltipModifier)
            } else if (miracloStyle && iconOnly) {
                MiraCLOLabeledIconTile(
                    label = uiState.label,
                    labelColor = colors.label,
                    modifier = tooltipModifier,
                    more = uiState.handlesSettingsClick,
                ) {
                    tileBody(Modifier)
                }
            } else {
                tileBody(tooltipModifier)
            }
        }
    }
}

/** MIRACLO: a round icon tile with its label underneath, as in the ColorOS layout. */
@Composable
private fun MiraCLOLabeledIconTile(
    label: String,
    labelColor: Color,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    secondaryLabelColor: Color = labelColor,
    more: Boolean = false,
    tile: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        tile()
        Spacer(Modifier.height(5.dp))
        // ColorOS 17: 11.5sp labels; tiles with more options carry a small up-down chevron.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = if (secondaryLabel == null) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (more) {
                Canvas(Modifier.padding(start = 2.dp, top = 2.dp).size(8.dp, 11.dp)) {
                    val sx = size.width / 8f
                    val sy = size.height / 12f
                    val path =
                        Path().apply {
                            moveTo(1.5f * sx, 4.5f * sy)
                            lineTo(4f * sx, 2f * sy)
                            lineTo(6.5f * sx, 4.5f * sy)
                            moveTo(1.5f * sx, 7.5f * sy)
                            lineTo(4f * sx, 10f * sy)
                            lineTo(6.5f * sx, 7.5f * sy)
                        }
                    drawPath(
                        path,
                        labelColor,
                        style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
        }
        if (!secondaryLabel.isNullOrEmpty()) {
            Text(
                text = secondaryLabel,
                color = secondaryLabelColor,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            )
        }
    }
}

@Composable
private fun TileExpandable(
    expandable: Expandable,
    color: () -> Color,
    shape: Shape,
    squishiness: () -> Float,
    hapticsViewModel: TileHapticsViewModel?,
    modifier: Modifier = Modifier,
    content: @Composable (Expandable) -> Unit,
) {
    Expandable(
        expandable = expandable,
        controller = rememberExpandableController(color = color, shape = shape),
        modifier =
            modifier
                .clip(shape)
                .motionTestValues { squishiness() exportAs TileMotionTestKeys.Squishness }
                .verticalSquish(squishiness),
        useModifierBasedImplementation = true,
    ) {
        content(hapticsViewModel?.createStateAwareExpandable(it) ?: it)
    }
}

@Composable
fun TileContainer(
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    accessibilityUiState: AccessibilityUiState,
    iconOnly: Boolean,
    isDualTarget: Boolean,
    interactionSource: MutableInteractionSource?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .height(TileHeight)
                .fillMaxWidth()
                .tileCombinedClickable(
                    onClick = onClick ?: {},
                    onLongClick = onLongClick,
                    accessibilityUiState = accessibilityUiState,
                    iconOnly = iconOnly,
                    isDualTarget = isDualTarget,
                    interactionSource = interactionSource,
                )
                .tileTestTag(iconOnly),
        content = content,
    )
}

@Composable
fun SmallStaticTile(
    uiState: TileUiState,
    iconProvider: IconProvider,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val colors = TileDefaults.getColorForState(uiState = uiState, iconOnly = true)

    Box(
        modifier
            .clip(TileDefaults.animateTileShapeAsState(uiState).value)
            .background(colors.background)
            .size(TileHeight)
            .clickable(onClick = onClick)
    ) {
        SmallTileContent(
            iconProvider = { getTileIcon(icon = iconProvider) },
            color = colors.icon,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
fun LargeStaticTile(
    uiState: TileUiState,
    iconProvider: IconProvider,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val colors = TileDefaults.getColorForState(uiState = uiState, iconOnly = false)

    Box(
        modifier
            .clip(TileDefaults.animateTileShapeAsState(uiState).value)
            .background(colors.background)
            .height(TileHeight)
            .clickable(onClick = onClick)
            .largeTilePadding()
    ) {
        LargeTileContent(
            label = uiState.label,
            secondaryLabel = "",
            iconProvider = { getTileIcon(icon = iconProvider) },
            sideDrawable = null,
            colors = colors,
            squishiness = { 1f },
        )
    }
}

private fun Context.getTileIcon(icon: IconProvider): Icon {
    return icon.icon?.let {
        if (it is QSTileImpl.ResourceIcon) {
            Icon.Resource(it.resId, null)
        } else {
            Icon.Loaded(it.getDrawable(this), null)
        }
    } ?: Icon.Resource(R.drawable.ic_error_outline, null)
}

fun tileHorizontalArrangement(): Arrangement.Horizontal {
    return spacedBy(space = CommonTileDefaults.TileArrangementPadding, alignment = Alignment.Start)
}

@Composable
fun Modifier.tileCombinedClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    accessibilityUiState: AccessibilityUiState,
    interactionSource: MutableInteractionSource?,
    iconOnly: Boolean,
    isDualTarget: Boolean,
): Modifier {
    val longPressLabel =
        if (iconOnly && isDualTarget) longPressLabelMoreDetails() else longPressLabelSettings()
    return combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
            onClickLabel = accessibilityUiState.clickLabel,
            onLongClickLabel = longPressLabel,
            hapticFeedbackEnabled = false, // Haptics handled separately
            interactionSource = interactionSource,
        )
        .semantics {
            val accessibilityRole =
                if (iconOnly && isDualTarget) {
                    Role.Switch
                } else {
                    accessibilityUiState.accessibilityRole
                }
            if (accessibilityRole == Role.Switch) {
                accessibilityUiState.toggleableState?.let { toggleableState = it }
            }
            role = accessibilityRole
            stateDescription = accessibilityUiState.stateDescription
        }
        .thenIf(iconOnly) {
            Modifier.semantics { contentDescription = accessibilityUiState.contentDescription }
        }
}

data class TileColors(
    val background: Color,
    val iconBackground: Color,
    val label: Color,
    val secondaryLabel: Color,
    val icon: Color,
)

@VisibleForTesting
object TileMotionTestKeys {
    val Squishness = MotionTestValueKey<Float>("tile_squishiness")
}

private object TileDefaults {
    /** An active tile uses the active color as background */
    @Composable
    @ReadOnlyComposable
    fun activeTileColors(): TileColors =
        TileColors(
            background = MaterialTheme.colorScheme.primary,
            iconBackground = MaterialTheme.colorScheme.primary,
            label = MaterialTheme.colorScheme.onPrimary,
            secondaryLabel = MaterialTheme.colorScheme.onPrimary,
            icon = MaterialTheme.colorScheme.onPrimary,
        )

    /** An active tile with dual target only show the active color on the icon */
    @Composable
    @ReadOnlyComposable
    fun activeDualTargetTileColors(): TileColors =
        TileColors(
            background = LocalAndroidColorScheme.current.surfaceEffect1,
            iconBackground = MaterialTheme.colorScheme.primary,
            label = MaterialTheme.colorScheme.onSurface,
            secondaryLabel = MaterialTheme.colorScheme.onSurface,
            icon = MaterialTheme.colorScheme.onPrimary,
        )

    @Composable
    @ReadOnlyComposable
    fun inactiveDualTargetTileColors(): TileColors =
        TileColors(
            background = LocalAndroidColorScheme.current.surfaceEffect1,
            iconBackground = LocalAndroidColorScheme.current.surfaceEffect2,
            label = MaterialTheme.colorScheme.onSurface,
            secondaryLabel = MaterialTheme.colorScheme.onSurface,
            icon = MaterialTheme.colorScheme.onSurface,
        )

    @Composable
    @ReadOnlyComposable
    fun inactiveTileColors(): TileColors =
        TileColors(
            background = LocalAndroidColorScheme.current.surfaceEffect1,
            iconBackground = Color.Transparent,
            label = MaterialTheme.colorScheme.onSurface,
            secondaryLabel = MaterialTheme.colorScheme.onSurface,
            icon = MaterialTheme.colorScheme.onSurface,
        )

    @Composable
    @ReadOnlyComposable
    fun unavailableTileColors(): TileColors {
        val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = .18f)
        val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .38f)
        return TileColors(
            background = surfaceColor,
            iconBackground = surfaceColor,
            label = onSurfaceVariantColor,
            secondaryLabel = onSurfaceVariantColor,
            icon = onSurfaceVariantColor,
        )
    }

    /**
     * MIRACLO style, modelled on ColorOS: translucent "glass" tiles that let the shade blur show
     * through. On a dark panel an active tile shows a white bubble with a coloured icon; on a
     * light panel the bubble is filled with the colour and the icon is white. Mobile data uses
     * green, everything else blue.
     */
    @Composable
    @ReadOnlyComposable
    fun miracloColorsForState(uiState: TileUiState, iconOnly: Boolean, spec: String): TileColors {
        when (LocalMiraCLOLook.current) {
            MiraCLOLook.IOS -> return miracloIosColors(uiState, iconOnly, spec)
            MiraCLOLook.ONEUI -> return miracloOneUiColors(uiState, iconOnly)
        }
        val onSurface = MaterialTheme.colorScheme.onSurface
        val darkPanel = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val accent = miracloColorOSAccent(spec)
        val liquid = LocalMiraCLOLiquidGlass.current
        val glass =
            if (darkPanel) {
                // Without Liquid Glass the dark panel is solid: opaque tiles, nothing shows through.
                if (liquid) Color.White.copy(alpha = 0.10f) else MiraCLOSolidTile
            } else {
                Color.White.copy(alpha = if (liquid) 0.55f else 0.72f)
            }
        val bubbleOff =
            if (darkPanel) {
                // The icon bubble of a wide tile stays a shade lighter than the tile itself.
                if (liquid) Color.White.copy(alpha = 0.12f) else MiraCLOSolidBubble
            } else {
                Color.Black.copy(alpha = 0.05f)
            }
        // ColorOS 17: dark = white circle with a coloured icon; light = coloured circle with a
        // white icon (Dark mode turns black).
        val bubbleOn = if (darkPanel) Color.White else accent
        val iconOn = if (darkPanel) accent else Color.White
        val secondary = onSurface.copy(alpha = 0.70f)
        return when (uiState.visualState) {
            STATE_ACTIVE ->
                TileColors(
                    background = if (iconOnly) bubbleOn else glass,
                    iconBackground = bubbleOn,
                    label = onSurface,
                    secondaryLabel = secondary,
                    icon = iconOn,
                )
            STATE_INACTIVE ->
                TileColors(
                    background = glass,
                    iconBackground = bubbleOff,
                    label = onSurface,
                    secondaryLabel = secondary,
                    icon = onSurface,
                )
            else -> unavailableTileColors()
        }
    }

    /**
     * iOS look: frosted round toggles; an active toggle fills with its system colour (blue,
     * green for data and hotspot, orange, purple for modes) and the icon turns white.
     */
    @Composable
    @ReadOnlyComposable
    private fun miracloIosColors(uiState: TileUiState, iconOnly: Boolean, spec: String): TileColors {
        val onSurface = MaterialTheme.colorScheme.onSurface
        val darkPanel = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val liquid = LocalMiraCLOLiquidGlass.current
        val accent =
            when (spec) {
                in MIRACLO_GREEN_TILES -> MiraCLOIosGreen
                in MIRACLO_ORANGE_TILES -> MiraCLOIosOrange
                in MIRACLO_PURPLE_TILES -> MiraCLOIosPurple
                else -> MiraCLOIosBlue
            }
        val glass =
            if (darkPanel) Color(0xFF787A86).copy(alpha = if (liquid) 0.30f else 0.36f)
            else Color.White.copy(alpha = if (liquid) 0.50f else 0.70f)
        val bubbleOff =
            if (darkPanel) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.07f)
        val secondary = onSurface.copy(alpha = 0.65f)
        return when (uiState.visualState) {
            STATE_ACTIVE ->
                TileColors(
                    background = if (iconOnly) accent else glass,
                    iconBackground = accent,
                    label = onSurface,
                    secondaryLabel = secondary,
                    icon = Color.White,
                )
            STATE_INACTIVE ->
                TileColors(
                    background = glass,
                    iconBackground = bubbleOff,
                    label = onSurface,
                    secondaryLabel = secondary,
                    icon = onSurface,
                )
            else -> unavailableTileColors()
        }
    }

    @Composable
    @ReadOnlyComposable
    private fun miracloOneUiColors(uiState: TileUiState, iconOnly: Boolean): TileColors {
        // One UI 9: frosted cards over the wallpaper; an active toggle is a white circle with a
        // dark icon, an inactive one a darker see-through circle with a white icon.
        val darkPanel = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val card = Color.White.copy(alpha = if (darkPanel) 0.13f else 0.22f)
        val offBubble = Color(0xFF080E28).copy(alpha = if (darkPanel) 0.24f else 0.16f)
        val text = Color.White
        return when (uiState.visualState) {
            STATE_ACTIVE ->
                TileColors(
                    background = if (iconOnly) Color.White else card,
                    iconBackground = Color.White,
                    label = text,
                    secondaryLabel = text.copy(alpha = 0.78f),
                    icon = Color(0xFF1D1D1F),
                )
            STATE_INACTIVE ->
                TileColors(
                    background = if (iconOnly) offBubble else card,
                    iconBackground = offBubble,
                    label = text,
                    secondaryLabel = text.copy(alpha = 0.78f),
                    icon = text,
                )
            else -> unavailableTileColors()
        }
    }

    @Composable
    @ReadOnlyComposable
    fun getColorForState(uiState: TileUiState, iconOnly: Boolean): TileColors {
        return when (uiState.visualState) {
            STATE_ACTIVE -> {
                if (uiState.handlesToggleClick && !iconOnly) {
                    activeDualTargetTileColors()
                } else {
                    activeTileColors()
                }
            }

            STATE_INACTIVE -> {
                if (uiState.handlesToggleClick && !iconOnly) {
                    inactiveDualTargetTileColors()
                } else {
                    inactiveTileColors()
                }
            }

            else -> unavailableTileColors()
        }
    }

    @Composable
    fun iconRadius(uiState: TileUiState): Dp {
        if (LocalMiraCLOQsStyle.current || LocalMiraCLORoundTiles.current) {
            return ToggleTargetSize / 2
        }
        return when (uiState.visualState) {
            STATE_ACTIVE -> ActiveIconCornerRadius
            STATE_INACTIVE -> InactiveIconCornerRadius
            else -> InactiveIconCornerRadius
        }
    }

    @Composable
    fun tileRadius(uiState: TileUiState): Dp {
        // MIRACLO: every look, and stock with round tiles, uses circles and pills.
        if (LocalMiraCLOQsStyle.current || LocalMiraCLORoundTiles.current) {
            return TileHeight / 2
        }
        return when (uiState.visualState) {
            STATE_ACTIVE -> ActiveTileCornerRadius
            STATE_INACTIVE -> InactiveTileCornerRadius
            else -> InactiveTileCornerRadius
        }
    }

    @Composable
    fun animateIconShapeAsState(uiState: TileUiState): State<RoundedCornerShape> {
        return animateShapeAsState(
            targetValue = iconRadius(uiState),
            label = "QSTileIconCornerRadius",
        )
    }

    @Composable
    fun animateTileShapeAsState(uiState: TileUiState): State<RoundedCornerShape> {
        return animateShapeAsState(targetValue = tileRadius(uiState), label = "QSTileCornerRadius")
    }

    @Composable
    fun animateShapeAsState(targetValue: Dp, label: String): State<RoundedCornerShape> {
        val animatedCornerRadius by animateDpAsState(targetValue = targetValue, label = label)

        return remember {
            val corner =
                object : CornerSize {
                    override fun toPx(shapeSize: Size, density: Density): Float {
                        return with(density) { animatedCornerRadius.toPx() }
                    }
                }
            mutableStateOf(RoundedCornerShape(corner))
        }
    }
}

/**
 * A composable function that returns the [Resources]. It will be recomposed when [Configuration]
 * gets updated.
 */
@Composable
@ReadOnlyComposable
private fun resources(): Resources {
    LocalConfiguration.current
    return LocalResources.current
}
