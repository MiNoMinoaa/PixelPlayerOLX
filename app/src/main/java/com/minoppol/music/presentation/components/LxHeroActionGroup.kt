package com.minoppol.music.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.minoppol.music.R
import kotlin.math.roundToInt

internal enum class LxHeroPlayState { Idle, Playing, Paused }

internal data class LxHeroMenuAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

internal val LxHeroSegmentHeight = 60.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LxHeroActionGroup(
    onShuffle: () -> Unit,
    onPlay: () -> Unit,
    playState: LxHeroPlayState,
    menuActions: List<LxHeroMenuAction>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    shuffleIcon: ImageVector = Icons.Rounded.Shuffle,
    playIcon: ImageVector? = null,
    playLabel: String? = null,
    trailingIcon: ImageVector? = null,
    onTrailing: (() -> Unit)? = null,
    trailingEnabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val motionScheme = remember { MotionScheme.expressive() }
    var showMenu by remember { mutableStateOf(false) }
    val shuffleInteraction = remember { MutableInteractionSource() }
    val playInteraction = remember { MutableInteractionSource() }
    val addInteraction = remember { MutableInteractionSource() }
    ButtonGroup(
        overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
        modifier = modifier
            .fillMaxWidth(0.86f)
            .widthIn(max = 380.dp)
            .height(LxHeroSegmentHeight),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        customItem(
            buttonGroupContent = {
                LxHeroSegment(
                    onClick = onShuffle,
                    enabled = enabled,
                    position = LxHeroSegmentPosition.Leading,
                    container = colors.surfaceContainerHighest,
                    content = colors.onSurface,
                    interactionSource = shuffleInteraction,
                    motionScheme = motionScheme,
                    contentDescription = null,
                    modifier = Modifier
                        .weight(1f)
                        .animateWidth(shuffleInteraction)
                ) {
                    Icon(shuffleIcon, contentDescription = null)
                }
            },
            menuContent = {}
        )
        customItem(
            buttonGroupContent = {
                val isActive = playState == LxHeroPlayState.Playing
                val container by animateColorAsState(
                    targetValue = if (isActive) colors.primary else colors.inverseSurface,
                    animationSpec = motionScheme.defaultEffectsSpec(),
                    label = "onlineHeroPlayContainer"
                )
                val content by animateColorAsState(
                    targetValue = if (isActive) colors.onPrimary else colors.inverseOnSurface,
                    animationSpec = motionScheme.defaultEffectsSpec(),
                    label = "onlineHeroPlayContent"
                )
                LxHeroSegment(
                    onClick = onPlay,
                    enabled = enabled,
                    position = LxHeroSegmentPosition.Middle,
                    container = container,
                    content = content,
                    interactionSource = playInteraction,
                    motionScheme = motionScheme,
                    restingInnerCorner = if (isActive) LxHeroSegmentHeight / 2 else 8.dp,
                    contentDescription = null,
                    modifier = Modifier
                        .weight(2f)
                        .animateWidth(playInteraction)
                ) {
                    AnimatedContent(
                        targetState = playState,
                        contentAlignment = Alignment.Center,
                        transitionSpec = {
                            ((fadeIn(motionScheme.fastEffectsSpec()) +
                                scaleIn(motionScheme.fastSpatialSpec(), initialScale = 0.7f)) togetherWith
                                (fadeOut(motionScheme.fastEffectsSpec()) +
                                    scaleOut(motionScheme.fastSpatialSpec(), targetScale = 0.7f)))
                                .using(SizeTransform(clip = false))
                        },
                        label = "onlineHeroPlayLabel"
                    ) { state ->
                        val isPause = state == LxHeroPlayState.Playing
                        LxHeroPlayLabel(
                            icon = playIcon ?: if (isPause) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            glyphInsets = if (isPause) LxHeroPauseGlyphInsets else LxHeroPlayGlyphInsets,
                            text = playLabel ?: stringResource(
                                when (state) {
                                    LxHeroPlayState.Idle -> R.string.play_playback
                                    LxHeroPlayState.Playing -> R.string.cd_pause
                                    LxHeroPlayState.Paused -> R.string.play_playback
                                }
                            )
                        )
                    }
                }
            },
            menuContent = {}
        )
        customItem(
            buttonGroupContent = {
                if (trailingIcon != null && onTrailing != null) {
                    LxHeroSegment(
                        onClick = onTrailing,
                        enabled = enabled && trailingEnabled,
                        position = LxHeroSegmentPosition.Trailing,
                        container = colors.surfaceContainerHighest,
                        content = colors.onSurface,
                        interactionSource = addInteraction,
                        motionScheme = motionScheme,
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                            .animateWidth(addInteraction)
                    ) {
                        Icon(trailingIcon, contentDescription = null)
                    }
                } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .animateWidth(addInteraction)
                ) {
                    LxHeroSegment(
                        onClick = { showMenu = true },
                        enabled = enabled && menuActions.isNotEmpty(),
                        position = LxHeroSegmentPosition.Trailing,
                        container = colors.surfaceContainerHighest,
                        content = colors.onSurface,
                        interactionSource = addInteraction,
                        motionScheme = motionScheme,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val rotation by animateFloatAsState(
                            targetValue = if (showMenu) 45f else 0f,
                            animationSpec = motionScheme.fastSpatialSpec(),
                            label = "onlineHeroAddRotation"
                        )
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.graphicsLayer { rotationZ = rotation }
                        )
                    }
                    DropdownMenuPopup(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(
                            MenuAnchorPosition.Custom(
                                xCandidates = {
                                    androidx.collection.mutableIntListOf(
                                        anchorBounds.right - menuSize.width,
                                        anchorBounds.left
                                    )
                                },
                                yCandidates = {
                                    androidx.collection.mutableIntListOf(
                                        anchorBounds.bottom,
                                        anchorBounds.top - menuSize.height
                                    )
                                }
                            )
                        )
                    ) {
                        DropdownMenuGroup(
                            shapes = MenuDefaults.groupShape(index = 0, count = 1),
                            containerColor = MenuDefaults.groupVibrantContainerColor
                        ) {
                            menuActions.forEachIndexed { index, action ->
                                DropdownMenuItem(
                                    text = { Text(action.label) },
                                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                                    shape = MenuDefaults.itemShape(index = index, count = menuActions.size).shape,
                                    colors = MenuDefaults.itemColors(),
                                    onClick = {
                                        showMenu = false
                                        action.onClick()
                                    }
                                )
                            }
                        }
                    }
                }
                }
            },
            menuContent = {}
        )
    }
}

internal class LxHeroGlyphInsets(val start: Float, val end: Float)

internal val LxHeroPlayGlyphInsets = LxHeroGlyphInsets(start = 8f, end = 5.9f)

internal val LxHeroPauseGlyphInsets = LxHeroGlyphInsets(start = 6f, end = 6f)

internal const val LxHeroIconViewport = 24f

internal const val LxHeroCapHeightRatio = 0.7f

@Composable
internal fun LxHeroPlayLabel(
    icon: ImageVector,
    glyphInsets: LxHeroGlyphInsets,
    text: String,
    iconSize: Dp = 24.dp,
    gap: Dp = 8.dp,
) {
    val style = MaterialTheme.typography.titleMedium
    Layout(
        content = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
            Text(
                text = text,
                style = style,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    ) { measurables, constraints ->
        val iconPlaceable = measurables[0].measure(Constraints())
        val iconSizePx = iconPlaceable.width.toFloat()
        val insetStart = (iconSizePx * glyphInsets.start / LxHeroIconViewport).roundToInt()
        val insetEnd = (iconSizePx * glyphInsets.end / LxHeroIconViewport).roundToInt()
        val gapPx = gap.roundToPx()
        val visibleIconWidth = iconPlaceable.width - insetStart - insetEnd
        val textPlaceable = measurables[1].measure(
            constraints.copy(
                minWidth = 0,
                minHeight = 0,
                maxWidth = (constraints.maxWidth - visibleIconWidth - gapPx).coerceAtLeast(0)
            )
        )
        val baseline = textPlaceable[FirstBaseline].takeIf { it != AlignmentLine.Unspecified }
            ?: textPlaceable.height
        val capCenter = baseline - (style.fontSize.toPx() * LxHeroCapHeightRatio / 2f).roundToInt()
        val halfHeight = maxOf(
            iconPlaceable.height / 2,
            capCenter,
            textPlaceable.height - capCenter
        )
        val width = visibleIconWidth + gapPx + textPlaceable.width
        val height = halfHeight * 2
        layout(width, height) {
            iconPlaceable.placeRelative(-insetStart, halfHeight - iconPlaceable.height / 2)
            textPlaceable.placeRelative(visibleIconWidth + gapPx, halfHeight - capCenter)
        }
    }
}

internal enum class LxHeroSegmentPosition { Leading, Middle, Trailing }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LxHeroSegment(
    onClick: () -> Unit,
    enabled: Boolean,
    position: LxHeroSegmentPosition,
    container: Color,
    content: Color,
    interactionSource: MutableInteractionSource,
    motionScheme: MotionScheme,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    restingInnerCorner: Dp = 8.dp,
    body: @Composable RowScope.() -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val spatial = motionScheme.fastSpatialSpec<Dp>()
    val outer by animateDpAsState(if (pressed) 18.dp else 30.dp, spatial, label = "onlineHeroOuterCorner")
    val inner by animateDpAsState(if (pressed) 18.dp else restingInnerCorner, spatial, label = "onlineHeroInnerCorner")
    val start = if (position == LxHeroSegmentPosition.Leading) outer else inner
    val end = if (position == LxHeroSegmentPosition.Trailing) outer else inner
    val shape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = container,
        contentColor = content,
        interactionSource = interactionSource,
        modifier = modifier
            .height(LxHeroSegmentHeight)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = body
        )
    }
}
