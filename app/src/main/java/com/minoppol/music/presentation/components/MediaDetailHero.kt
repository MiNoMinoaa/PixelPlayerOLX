package com.minoppol.music.presentation.components

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.minoppol.music.R
import com.minoppol.music.ui.theme.RoundedSans
import com.minoppol.music.utils.formatSongCount
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** How the hero's main button relates to what is playing right now. */
enum class HeroPlayState {
    /** Nothing from this album/artist is loaded: the button starts it. */
    Idle,
    /** A song from this album/artist is playing: the button pauses it. */
    Playing,
    /** A song from this album/artist is loaded but paused: the button resumes it. */
    Paused,
}

/** A secondary action offered from the hero's "+" menu. */
data class DetailHeroMenuAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun heroTitleStyle(): TextStyle = MaterialTheme.typography.headlineMedium.copy(
    fontFamily = RoundedSans,
    fontWeight = FontWeight.Bold
)

private val PinnedActionsTopGap = 4.dp
private val PinnedActionsBottomGap = 12.dp

/** Height the pinned button group occupies under the collapsed bar, band included. */
val DetailHeroPinnedActionsHeight = PinnedActionsTopGap + 60.dp + PinnedActionsBottomGap

/**
 * Links the hero that scrolls inside the list with the layer that actually draws its title
 * and buttons, so the title can travel into the collapsed bar and the buttons can stick
 * under it.
 *
 * The hero only reserves space and reports where its title and buttons sit *inside itself*
 * (stable while scrolling). The hero's own top comes from [heroTopPx], computed by the screen
 * from the list and header state; reading it during placement keeps the floating layer in
 * the same frame as the list, with no measure-then-follow lag.
 */
@Stable
class DetailHeroDock internal constructor(
    private val heroTopPx: () -> Float?,
) {
    // Hero-local geometry, written from placement callbacks only when it changes.
    internal var titleOffset by mutableStateOf(Offset.Zero)
    internal var titleWidthPx by mutableIntStateOf(0)
    internal var titleTextInset by mutableFloatStateOf(0f)
    internal var titleFirstLineCenter by mutableFloatStateOf(0f)
    internal var titleLineWidth by mutableFloatStateOf(0f)
    internal var titleSingleLine by mutableStateOf(true)
    internal var actionsOffset by mutableStateOf(Offset.Zero)
    internal var actionsWidthPx by mutableIntStateOf(0)
    internal var subtitleOffset by mutableStateOf(Offset.Zero)
    internal var subtitleWidthPx by mutableIntStateOf(0)
    internal var subtitleHeightPx by mutableIntStateOf(0)

    // Screen geometry, refreshed on every composition of rememberDetailHeroDock.
    internal var dockStartHeroTopPx by mutableFloatStateOf(0f)
    internal var heroStartPx by mutableFloatStateOf(0f)
    internal var dockStartPx by mutableFloatStateOf(0f)
    internal var dockCenterYPx by mutableFloatStateOf(0f)
    internal var dockMaxWidthPx by mutableFloatStateOf(0f)
    internal var dockScale by mutableFloatStateOf(1f)
    internal var pinnedActionsTopPx by mutableFloatStateOf(0f)
    internal var collapsedBarBottomPx by mutableFloatStateOf(0f)

    internal fun heroTop(): Float? = heroTopPx()

    /** Where the title's pivot (left edge of its text, centre of its first line) sits unmoved. */
    internal fun naturalTitlePivotY(): Float? =
        heroTop()?.let { it + titleOffset.y + titleFirstLineCenter }

    /** 0 until the title starts docking, 1 once it has reached the bar. */
    internal fun titleProgress(): Float {
        val natural = naturalTitlePivotY() ?: return 1f
        val start = dockStartHeroTopPx + titleOffset.y + titleFirstLineCenter
        val span = start - dockCenterYPx
        if (span <= 0f) return 1f
        return ((start - natural) / span).coerceIn(0f, 1f)
    }

    /**
     * A single-line title that fits the bar docks there for good. A longer one wraps or would
     * need ellipsis/width compression, so it hands over to the bar's own title near the end.
     */
    internal val docksPermanently: Boolean
        get() = titleSingleLine && titleLineWidth * dockScale <= dockMaxWidthPx

    internal fun floatingTitleAlpha(progress: Float): Float =
        if (docksPermanently) 1f else 1f - ((progress - 0.75f) / 0.25f).coerceIn(0f, 1f)

    private val progress by derivedStateOf { titleProgress() }

    /** Alpha for the collapsed bar's own title: only shown when the floating one can't dock. */
    val barTitleAlpha: Float by derivedStateOf { 1f - floatingTitleAlpha(progress) }

    /**
     * Alpha for the collapsed bar's subtitle. The title crosses the subtitle's line on its
     * way up, so the subtitle only settles in over the last stretch, once the title is above it.
     */
    val barSubtitleAlpha: Float by derivedStateOf { ((progress - 0.9f) / 0.1f).coerceIn(0f, 1f) }

    /** The bar fills in as the title docks, so hero content scrolling under it stays hidden. */
    val barContainerAlpha: Float by derivedStateOf { ((progress - 0.85f) / 0.15f).coerceIn(0f, 1f) }
}

/**
 * @param heroTopPx top of the hero item in the screen's coordinates, or null once it has
 *   scrolled out of the list.
 * @param dockStartHeroTopPx the [heroTopPx] at which the title starts moving toward the bar;
 *   above it the title travels with the content unchanged.
 * @param collapsedTitleStartPadding / [collapsedBarHeight] / [collapsedTitleFontSize] must
 *   mirror the screen's CollapsibleCommonTopBar config, since the title docks into its slot.
 */
@Composable
fun rememberDetailHeroDock(
    heroTopPx: () -> Float?,
    dockStartHeroTopPx: Float,
    heroStartPadding: Dp,
    collapsedBarBottom: Dp,
    hasBarSubtitle: Boolean,
    collapsedTitleStartPadding: Dp = 68.dp,
    collapsedTitleEndPadding: Dp = 24.dp,
    collapsedBarHeight: Dp = 56.dp,
    collapsedTitleFontSize: TextUnit = 18.sp,
    titleStyle: TextStyle = heroTitleStyle(),
): DetailHeroDock {
    val dock = remember { DetailHeroDock { heroTopPx() } }
    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val subtitleLineHeight = MaterialTheme.typography.labelLarge.lineHeight
    val heroFontSize = titleStyle.fontSize
    with(density) {
        // ExpressiveTopBarContent: title line is 1.1× its font size, stacked over the
        // subtitle and centred as a column in the collapsed bar's height.
        val titleLine = collapsedTitleFontSize.toPx() * 1.1f
        val column = titleLine + if (hasBarSubtitle) subtitleLineHeight.toPx() else 0f
        dock.dockStartHeroTopPx = dockStartHeroTopPx
        dock.heroStartPx = heroStartPadding.toPx()
        dock.dockStartPx = collapsedTitleStartPadding.toPx()
        dock.dockCenterYPx = statusBarTop.toPx() + (collapsedBarHeight.toPx() - column) / 2f + titleLine / 2f
        dock.dockMaxWidthPx = (screenWidth - collapsedTitleStartPadding - collapsedTitleEndPadding).toPx()
        dock.dockScale = collapsedTitleFontSize.toPx() / heroFontSize.toPx()
        dock.pinnedActionsTopPx = (collapsedBarBottom + PinnedActionsTopGap).toPx()
        dock.collapsedBarBottomPx = collapsedBarBottom.toPx()
    }
    return dock
}

/**
 * Title block shared by the album and artist screens, placed right under the collapsing
 * artwork header: centred title, an optional linked chip (the album's artist), a meta line,
 * and a connected Shuffle | Play | + button group whose "+" opens the screen's extra actions.
 *
 * With a [dock], the title and buttons only reserve their space here; [DetailHeroFloatingLayer]
 * draws them so they can leave the list.
 */
@Composable
fun MediaDetailHero(
    title: String,
    metaLine: String?,
    onShuffle: () -> Unit,
    onPlay: () -> Unit,
    menuActions: ImmutableList<DetailHeroMenuAction>,
    modifier: Modifier = Modifier,
    playState: HeroPlayState = HeroPlayState.Idle,
    chip: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    dock: DetailHeroDock? = null,
    titleStyle: TextStyle = heroTitleStyle(),
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = titleStyle,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout ->
                if (dock != null) {
                    dock.titleTextInset = (0 until layout.lineCount).minOf { layout.getLineLeft(it) }
                    dock.titleFirstLineCenter = (layout.getLineTop(0) + layout.getLineBottom(0)) / 2f
                    dock.titleLineWidth = (0 until layout.lineCount)
                        .maxOf { layout.getLineRight(it) - layout.getLineLeft(it) }
                    dock.titleSingleLine = layout.lineCount == 1 && !layout.hasVisualOverflow
                }
            },
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .then(
                    if (dock != null) {
                        Modifier
                            .onPlaced { coordinates ->
                                dock.titleOffset = coordinates.positionInParent()
                                dock.titleWidthPx = coordinates.size.width
                            }
                            // Drawn by the floating layer; this copy keeps layout and semantics.
                            .graphicsLayer { alpha = 0f }
                    } else {
                        Modifier
                    }
                )
        )
        if (chip != null || !metaLine.isNullOrBlank()) {
            HeroSubtitleBlock(
                chip = chip,
                metaLine = metaLine,
                modifier = if (dock != null) {
                    Modifier
                        .onPlaced { coordinates ->
                            dock.subtitleOffset = coordinates.positionInParent()
                            dock.subtitleWidthPx = coordinates.size.width
                            dock.subtitleHeightPx = coordinates.size.height
                        }
                        // Drawn by the floating layer (outside the list's overscroll stretch);
                        // this copy only reserves the space.
                        .graphicsLayer { alpha = 0f }
                        .clearAndSetSemantics { }
                } else {
                    Modifier
                }
            )
        }
        Spacer(Modifier.height(20.dp))
        if (dock != null) {
            Spacer(
                modifier = Modifier
                    .heroActionGroupSize()
                    .onPlaced { coordinates ->
                        dock.actionsOffset = coordinates.positionInParent()
                        dock.actionsWidthPx = coordinates.size.width
                    }
            )
        } else {
            HeroActionGroup(
                onShuffle = onShuffle,
                onPlay = onPlay,
                playState = playState,
                menuActions = menuActions,
                enabled = enabled,
                modifier = Modifier.heroActionGroupSize()
            )
        }
    }
}

/** The linked chip and meta line under the title; one block so the hero and floating layer match. */
@Composable
private fun HeroSubtitleBlock(
    chip: (@Composable () -> Unit)?,
    metaLine: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (chip != null) {
            Spacer(Modifier.height(4.dp))
            chip()
        }
        if (!metaLine.isNullOrBlank()) {
            Spacer(Modifier.height(if (chip != null) 8.dp else 10.dp))
            Text(
                text = metaLine,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun Modifier.heroActionGroupSize(): Modifier =
    fillMaxWidth(0.86f)
        .widthIn(max = 380.dp)
        .height(HeroSegmentHeight)

/**
 * Draws the docking title and the sticky button group of a [MediaDetailHero] that was given
 * [dock]. Place it above the list and the top bar in the screen's root Box.
 *
 * - The title rides with the content until it meets the collapsed bar, sliding from centred
 *   to the bar's title slot and shrinking to its size along the way.
 * - The buttons ride with the content until they reach the bar, then hang under it on a
 *   surface band while the songs scroll beneath. Drags on them still scroll [listState].
 */
@Composable
fun BoxScope.DetailHeroFloatingLayer(
    title: String,
    dock: DetailHeroDock,
    listState: ScrollableState,
    onShuffle: () -> Unit,
    onPlay: () -> Unit,
    menuActions: ImmutableList<DetailHeroMenuAction>,
    playState: HeroPlayState,
    enabled: Boolean,
    titleStyle: TextStyle = heroTitleStyle(),
    metaLine: String? = null,
    chip: (@Composable () -> Unit)? = null,
) {
    val density = LocalDensity.current
    val surface = MaterialTheme.colorScheme.surface
    val bandTopGapPx = with(density) { PinnedActionsTopGap.toPx() }
    val bandFadePx = with(density) { 12.dp.toPx() }

    fun actionsTop(): Float {
        val natural = dock.heroTop()?.let { it + dock.actionsOffset.y }
        return if (natural == null) dock.pinnedActionsTopPx else maxOf(natural, dock.pinnedActionsTopPx)
    }

    fun bandAlpha(): Float {
        val natural = dock.heroTop()?.let { it + dock.actionsOffset.y } ?: return 1f
        return ((dock.pinnedActionsTopPx - natural) / bandFadePx).coerceIn(0f, 1f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, (actionsTop() - bandTopGapPx).roundToInt()) }
            .scrollable(
                state = listState,
                orientation = Orientation.Vertical,
                reverseDirection = ScrollableDefaults.reverseDirection(
                    LocalLayoutDirection.current,
                    Orientation.Vertical,
                    reverseScrolling = false
                )
            )
    ) {
        Column(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = bandAlpha() }
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(DetailHeroPinnedActionsHeight)
                    .background(surface)
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(Brush.verticalGradient(listOf(surface, surface.copy(alpha = 0f))))
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = PinnedActionsTopGap, bottom = PinnedActionsBottomGap + 12.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            if (dock.actionsWidthPx > 0) {
                HeroActionGroup(
                    onShuffle = onShuffle,
                    onPlay = onPlay,
                    playState = playState,
                    menuActions = menuActions,
                    enabled = enabled,
                    modifier = Modifier
                        .width(with(density) { dock.actionsWidthPx.toDp() })
                        .height(HeroSegmentHeight)
                )
            }
        }
    }

    // Chip and meta line live here rather than in the list so the list's overscroll stretch
    // leaves them alone, like the title and buttons. They ride with the content and fade out
    // as they slide under the collapsed bar.
    if ((chip != null || !metaLine.isNullOrBlank()) && dock.subtitleWidthPx > 0) {
        Box(
            modifier = Modifier
                .width(with(density) { dock.subtitleWidthPx.toDp() })
                .offset {
                    IntOffset(
                        (dock.heroStartPx + dock.subtitleOffset.x).roundToInt(),
                        ((dock.heroTop() ?: 0f) + dock.subtitleOffset.y).roundToInt()
                    )
                }
                .graphicsLayer {
                    val top = dock.heroTop()?.let { it + dock.subtitleOffset.y }
                    val height = dock.subtitleHeightPx.toFloat().coerceAtLeast(1f)
                    alpha = if (top == null) {
                        0f
                    } else {
                        ((top + height - dock.collapsedBarBottomPx) / height).coerceIn(0f, 1f)
                    }
                }
                .scrollable(
                    state = listState,
                    orientation = Orientation.Vertical,
                    reverseDirection = ScrollableDefaults.reverseDirection(
                        LocalLayoutDirection.current,
                        Orientation.Vertical,
                        reverseScrolling = false
                    )
                )
        ) {
            HeroSubtitleBlock(chip = chip, metaLine = metaLine)
        }
    }

    if (dock.titleWidthPx > 0) {
        Text(
            text = title,
            style = titleStyle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(with(density) { dock.titleWidthPx.toDp() })
                .clearAndSetSemantics { }
                .offset {
                    // Place the box so its pivot lands on the interpolated point; scaling then
                    // happens around that pivot and leaves it in place.
                    val t = dock.titleProgress()
                    val naturalX = dock.heroStartPx + dock.titleOffset.x + dock.titleTextInset
                    val pivotX = naturalX + (dock.dockStartPx - naturalX) * t
                    val pivotY = maxOf(dock.naturalTitlePivotY() ?: dock.dockCenterYPx, dock.dockCenterYPx)
                    IntOffset(
                        (pivotX - dock.titleTextInset).roundToInt(),
                        (pivotY - dock.titleFirstLineCenter).roundToInt()
                    )
                }
                .graphicsLayer {
                    val t = dock.titleProgress()
                    val scale = 1f + (dock.dockScale - 1f) * t
                    scaleX = scale
                    scaleY = scale
                    alpha = dock.floatingTitleAlpha(t)
                    transformOrigin = TransformOrigin(
                        pivotFractionX = if (size.width > 0f) dock.titleTextInset / size.width else 0f,
                        pivotFractionY = if (size.height > 0f) dock.titleFirstLineCenter / size.height else 0f
                    )
                }
        )
    }
}

/** Avatar + name pill linking to the artist, as in the album header reference. */
@Composable
fun DetailHeroArtistChip(
    name: String,
    imageUrl: String?,
    onClick: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(colors.secondary),
                contentAlignment = Alignment.Center
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    SmartImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp)
                    )
                } else {
                    Text(
                        text = name.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSecondary
                    )
                }
            }
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = RoundedSans),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 260.dp)
            )
        }
    }
    // A filled pill reads as a button, so the link to the artist is discoverable.
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = colors.secondaryContainer,
            contentColor = colors.onSecondaryContainer,
            content = content
        )
    } else {
        Surface(
            shape = CircleShape,
            color = colors.secondaryContainer,
            contentColor = colors.onSecondaryContainer,
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroActionGroup(
    onShuffle: () -> Unit,
    onPlay: () -> Unit,
    playState: HeroPlayState,
    menuActions: ImmutableList<DetailHeroMenuAction>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motionScheme = remember { MotionScheme.expressive() }
    var showMenu by remember { mutableStateOf(false) }
    val shuffleInteraction = remember { MutableInteractionSource() }
    val playInteraction = remember { MutableInteractionSource() }
    val addInteraction = remember { MutableInteractionSource() }

    // Material 3 Expressive connected button group: pressing a segment widens it while its
    // neighbours make room (ButtonGroup.animateWidth), and its corners morph toward the
    // pressed shape on the expressive spatial spring.
    ButtonGroup(
        overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        customItem(
            buttonGroupContent = {
                HeroSegment(
                    onClick = onShuffle,
                    enabled = enabled,
                    position = SegmentPosition.Leading,
                    container = colors.surfaceContainerHighest,
                    content = colors.onSurface,
                    interactionSource = shuffleInteraction,
                    motionScheme = motionScheme,
                    contentDescription = stringResource(R.string.detail_hero_shuffle),
                    modifier = Modifier
                        .weight(1f)
                        .animateWidth(shuffleInteraction)
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null)
                }
            },
            menuContent = {}
        )
        customItem(
            buttonGroupContent = {
                val isActive = playState == HeroPlayState.Playing
                // The active state trades the neutral inverse surface for the album's
                // primary colour, so "this album is playing" reads at a glance.
                val container by animateColorAsState(
                    targetValue = if (isActive) colors.primary else colors.inverseSurface,
                    animationSpec = motionScheme.defaultEffectsSpec(),
                    label = "heroPlayContainer"
                )
                val content by animateColorAsState(
                    targetValue = if (isActive) colors.onPrimary else colors.inverseOnSurface,
                    animationSpec = motionScheme.defaultEffectsSpec(),
                    label = "heroPlayContent"
                )
                HeroSegment(
                    onClick = onPlay,
                    enabled = enabled,
                    position = SegmentPosition.Middle,
                    container = container,
                    content = content,
                    interactionSource = playInteraction,
                    motionScheme = motionScheme,
                    // Fully round while playing: the shape itself signals the active state.
                    restingInnerCorner = if (isActive) HeroSegmentHeight / 2 else 8.dp,
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
                                // The default SizeTransform clips to the animating bounds, which
                                // chopped the label for a frame whenever "Play" and "Pause" differ
                                // in width. Both labels fit the segment, so nothing needs clipping.
                                .using(SizeTransform(clip = false))
                        },
                        label = "heroPlayContent"
                    ) { state ->
                        val isPause = state == HeroPlayState.Playing
                        HeroPlayLabel(
                            icon = if (isPause) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            glyphInsets = if (isPause) PauseGlyphInsets else PlayGlyphInsets,
                            text = stringResource(
                                when (state) {
                                    HeroPlayState.Idle -> R.string.detail_hero_play
                                    HeroPlayState.Playing -> R.string.detail_hero_pause
                                    HeroPlayState.Paused -> R.string.detail_hero_resume
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .animateWidth(addInteraction)
                ) {
                    HeroSegment(
                        onClick = { showMenu = true },
                        enabled = enabled && menuActions.isNotEmpty(),
                        position = SegmentPosition.Trailing,
                        container = colors.surfaceContainerHighest,
                        content = colors.onSurface,
                        interactionSource = addInteraction,
                        motionScheme = motionScheme,
                        contentDescription = stringResource(R.string.detail_hero_more_actions),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val rotation by animateFloatAsState(
                            targetValue = if (showMenu) 45f else 0f,
                            animationSpec = motionScheme.fastSpatialSpec(),
                            label = "heroAddRotation"
                        )
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.graphicsLayer { rotationZ = rotation }
                        )
                    }
                    ExpressiveDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        actions = menuActions,
                        vibrant = true
                    )
                }
            },
            menuContent = {}
        )
    }
}

private val HeroSegmentHeight = 60.dp

/**
 * Empty space inside a 24dp Material icon on its leading and trailing sides, in viewport
 * units. Centring the icon box leaves that space in the group's width, so the visible
 * content drifts toward the label. Measured from the Rounded icon paths.
 */
private class GlyphInsets(val start: Float, val end: Float)

// Icons.Rounded.PlayArrow spans x = 8 → 18.1 of 24.
private val PlayGlyphInsets = GlyphInsets(start = 8f, end = 5.9f)

// Icons.Rounded.Pause spans x = 6 → 18 of 24.
private val PauseGlyphInsets = GlyphInsets(start = 6f, end = 6f)

private const val IconViewport = 24f

/** Google Sans cap height as a fraction of the font size. */
private const val CapHeightRatio = 0.7f

/**
 * Icon + label laid out on what the eye sees rather than on layout boxes:
 * - horizontally, the icon's built-in side padding is trimmed so the visible glyph and the
 *   text are centred as one block, with a true [gap] between them;
 * - vertically, the icon is centred on the middle of the label's cap height instead of on
 *   the text line box, which sits lower than the letters because of the descender space.
 */
@Composable
private fun HeroPlayLabel(
    icon: ImageVector,
    glyphInsets: GlyphInsets,
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
        val insetStart = (iconSizePx * glyphInsets.start / IconViewport).roundToInt()
        val insetEnd = (iconSizePx * glyphInsets.end / IconViewport).roundToInt()
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
        val capCenter = baseline - (style.fontSize.toPx() * CapHeightRatio / 2f).roundToInt()

        // Make the block symmetric around the cap centre so the parent's vertical centring
        // lands exactly on it.
        val halfHeight = maxOf(
            iconPlaceable.height / 2,
            capCenter,
            textPlaceable.height - capCenter
        )
        val width = visibleIconWidth + gapPx + textPlaceable.width
        val height = halfHeight * 2
        layout(width, height) {
            // The icon's transparent leading padding hangs outside the block.
            iconPlaceable.placeRelative(-insetStart, halfHeight - iconPlaceable.height / 2)
            textPlaceable.placeRelative(visibleIconWidth + gapPx, halfHeight - capCenter)
        }
    }
}

/**
 * "12 songs · 48 min" line introducing the track list. It sits above the first song so the
 * size of the collection is visible without scrolling to the end.
 */
@Composable
fun DetailSongsSummary(
    songCount: Int,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val totalMinutes = (durationMs / 60_000L).toInt()
    val duration = when {
        durationMs <= 0L -> null
        totalMinutes >= 60 -> stringResource(R.string.detail_hero_duration_hours, totalMinutes / 60, totalMinutes % 60)
        else -> stringResource(R.string.detail_hero_duration_minutes, totalMinutes.coerceAtLeast(1))
    }
    Text(
        text = listOfNotNull(formatSongCount(songCount), duration).joinToString(" · "),
        style = MaterialTheme.typography.titleSmall.copy(fontFamily = RoundedSans),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

private enum class SegmentPosition { Leading, Middle, Trailing }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroSegment(
    onClick: () -> Unit,
    enabled: Boolean,
    position: SegmentPosition,
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
    // Connected-group corners: outer edges fully round, inner edges tight; on press every
    // corner converges on a medium radius (the expressive "squish").
    val outer by animateDpAsState(if (pressed) 18.dp else 30.dp, spatial, label = "heroOuterCorner")
    val inner by animateDpAsState(if (pressed) 18.dp else restingInnerCorner, spatial, label = "heroInnerCorner")
    val start = if (position == SegmentPosition.Leading) outer else inner
    val end = if (position == SegmentPosition.Trailing) outer else inner
    val shape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = container,
        contentColor = content,
        interactionSource = interactionSource,
        modifier = modifier
            .height(HeroSegmentHeight)
            .then(
                if (contentDescription != null) {
                    Modifier.semanticsDescription(contentDescription)
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

private fun Modifier.semanticsDescription(description: String): Modifier =
    semantics { contentDescription = description }

/** Standard "+" menu for a list of songs: add to playlist, play next, add to queue. */
@Composable
fun rememberSongCollectionMenuActions(
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
): ImmutableList<DetailHeroMenuAction> {
    val addToPlaylist = stringResource(R.string.detail_hero_add_to_playlist)
    val playNext = stringResource(R.string.detail_hero_play_next)
    val addToQueue = stringResource(R.string.detail_hero_add_to_queue)
    return remember(addToPlaylist, playNext, addToQueue, onAddToPlaylist, onPlayNext, onAddToQueue) {
        persistentListOf(
            DetailHeroMenuAction(addToPlaylist, Icons.AutoMirrored.Rounded.PlaylistAdd, onAddToPlaylist),
            DetailHeroMenuAction(playNext, Icons.Rounded.SkipNext, onPlayNext),
            DetailHeroMenuAction(addToQueue, Icons.AutoMirrored.Rounded.QueueMusic, onAddToQueue),
        )
    }
}
