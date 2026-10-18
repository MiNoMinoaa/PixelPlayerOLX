package com.minoppol.music.presentation.components

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.minoppol.music.BottomNavItem
import com.minoppol.music.presentation.components.player.MorphingPlayPauseIcon
import com.minoppol.music.presentation.navigation.Screen
import com.minoppol.music.presentation.navigation.navigateToTopLevelSafely
import com.minoppol.music.presentation.utils.LocalAppHapticsConfig
import com.minoppol.music.presentation.utils.performAppHapticFeedback
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

object FloatingNavBarMetrics {
    val BarHeight = 62.dp
    val CompactBarHeight = 54.dp
    val BarPadding = 4.dp
    val EdgeMargin = 21.dp
    val ShadowRoom = 8.dp
    val MiniAlignedMargin = 14.dp
    val MiniAlignedMarginCompact = 12.dp
    val FabGap = 8.dp
    val FabSize = 54.dp
    val MaxItemExtent = 72.dp
    val MaxItemExtentCompact = 56.dp
    val IconSize = 24.dp
    val TrailingIconSize = 26.dp
    val LabelGap = 2.dp
    val LabelInset = 2.dp
    val LabelSize = 10.sp
    val LensGrowth = 14.dp
    val SlotBlur = 6.dp
    val ExitTranslate = 48.dp
    val DockElevation = 3.dp
    const val MaxPressGrowthPx = 16f

    fun dockTotalMaxWidth(itemCount: Int, compactMode: Boolean): Dp {
        val pillMax = if (compactMode) MaxItemExtentCompact else MaxItemExtent
        return pillMax * itemCount + BarPadding * 2 + FabSize + FabGap
    }
}

private val LensSettleSpring = spring<Float>(dampingRatio = 0.68f, stiffness = 158f)
private val LensLiftSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 504f)
private val LensTrackSpring = spring<Float>(dampingRatio = 1f, stiffness = 2742f)
private val SlotSizeSpring = spring<Dp>(dampingRatio = 1f, stiffness = 224f)
private val SlotPopSpring = spring<Float>(dampingRatio = 0.7f, stiffness = 247f)
private val FabLiftOnSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 504f)
private val FabLiftOffSpring = spring<Float>(dampingRatio = 0.68f, stiffness = 158f)
private val SlotRevealEasing = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val SlotExitEasing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

private const val JellySpeed = 8f
private const val JellyStretch = 0.25f
private const val LensMagnify = 0.12f
private const val BarPressGrowth = 1f / 8f
private const val PullLimit = 7f / 32f
private const val PullStretch = 0.5f
private const val LensOverdrag = 0.35f
private const val FlingProjectionSec = 0.1f

private class DockSpring(
    scope: CoroutineScope,
    initial: Float
) {
    var value by mutableFloatStateOf(initial)
        private set
    var velocity by mutableFloatStateOf(0f)
        private set
    var targetValue by mutableFloatStateOf(initial)
        private set
    private var spec: SpringSpec<Float> = LensSettleSpring
    private var animation: TargetBasedAnimation<Float, AnimationVector1D>? = null
    private var startNanos = 0L
    private var retargetPending = false
    private var finished = true

    init {
        scope.launch {
            while (true) {
                withFrameNanos { frameNanos ->
                    val active = animation
                    if (!finished && active != null) {
                        val playTime = frameNanos - startNanos
                        value = active.getValueFromNanos(playTime)
                        velocity = active.getVelocityVectorFromNanos(playTime).value
                        finished = active.isFinishedFromNanos(playTime)
                    }
                    if (retargetPending) {
                        retargetPending = false
                        animation = TargetBasedAnimation(
                            animationSpec = spec,
                            typeConverter = Float.VectorConverter,
                            initialValue = value,
                            targetValue = targetValue,
                            initialVelocity = velocity
                        )
                        startNanos = frameNanos
                        finished = false
                    }
                }
            }
        }
    }

    fun springTo(target: Float, spec: SpringSpec<Float>) {
        targetValue = target
        this.spec = spec
        retargetPending = true
    }
}
private const val RubberBandFactor = 0.55f
private const val SlotEnterScale = 0.5f
private const val SlotExitScale = 0.7f
private const val SlotEnterDuration = 500
private const val SlotExitDuration = 220

private fun rubberBand(overshoot: Float, limit: Float): Float {
    val pull = 1f - 1f / (abs(overshoot) * RubberBandFactor / limit + 1f)
    return limit * pull * overshoot.sign
}

private fun dockPillMaxWidth(itemCount: Int): Dp =
    FloatingNavBarMetrics.MaxItemExtent * itemCount + FloatingNavBarMetrics.BarPadding * 2

private fun dockPillMaxWidthCompact(itemCount: Int): Dp =
    FloatingNavBarMetrics.MaxItemExtentCompact * itemCount + FloatingNavBarMetrics.BarPadding * 2

@Composable
fun FloatingNavBar(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    compactMode: Boolean = false,
    hasMiniPlayer: Boolean = false,
    isPlaying: Boolean = false,
    exitDirectionSign: Float = 0f,
    cornerRadius: Dp = 32.dp,
    onPlayPauseClick: () -> Unit = {},
    onSearchIconDoubleTap: () -> Unit = {}
) {
    val barHeight = resolveFloatingNavBarHeight(compactMode)
    val tapState = remember { NavRowTapState() }
    val scope = rememberCoroutineScope()
    val systemNavBarInset = sanitizeNavigationBarBottomInset(
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    )
    val bottomMargin = maxOf(FloatingNavBarMetrics.EdgeMargin, systemNavBarInset)
    val startMargin = if (hasMiniPlayer) {
        if (compactMode) FloatingNavBarMetrics.MiniAlignedMarginCompact else FloatingNavBarMetrics.MiniAlignedMargin
    } else {
        FloatingNavBarMetrics.EdgeMargin
    }
    val endMargin = if (hasMiniPlayer) {
        if (compactMode) FloatingNavBarMetrics.MiniAlignedMarginCompact else FloatingNavBarMetrics.MiniAlignedMargin
    } else {
        FloatingNavBarMetrics.EdgeMargin
    }
    val latestCurrentRoute by rememberUpdatedState(currentRoute)
    val latestOnSearchIconDoubleTap by rememberUpdatedState(onSearchIconDoubleTap)

    val commitPage: (Int) -> Unit = remember(navItems, navController) {
        commit@{ index ->
            val item = navItems.getOrNull(index) ?: return@commit
            val route = latestCurrentRoute
            if (route == null) {
                tapState.lastSearchTapTimestamp = 0L
                return@commit
            }
            val itemRoute = item.screen.route
            val isAlreadySelected = route == itemRoute
            if (itemRoute == Screen.Search.route) {
                val now = SystemClock.elapsedRealtime()
                val isDoubleTap = now - tapState.lastSearchTapTimestamp <= 350L
                tapState.lastSearchTapTimestamp = now
                if (!isAlreadySelected) {
                    if (!navController.navigateToTopLevelSafely(itemRoute)) {
                        tapState.lastSearchTapTimestamp = 0L
                        return@commit
                    }
                    if (isDoubleTap) {
                        tapState.lastSearchTapTimestamp = 0L
                        scope.launch {
                            delay(160L)
                            latestOnSearchIconDoubleTap()
                        }
                    }
                } else if (isDoubleTap) {
                    tapState.lastSearchTapTimestamp = 0L
                    latestOnSearchIconDoubleTap()
                }
            } else if (!isAlreadySelected) {
                tapState.lastSearchTapTimestamp = 0L
                navController.navigateToTopLevelSafely(itemRoute)
            } else {
                tapState.lastSearchTapTimestamp = 0L
            }
        }
    }

    val selectedIndex = navItems.indexOfFirst { currentRoute != null && currentRoute == it.screen.route }
        .let { if (it < 0) 0 else it }

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = startMargin,
                    end = endMargin,
                    top = FloatingNavBarMetrics.ShadowRoom,
                    bottom = bottomMargin
                ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(barHeight),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxHeight()
                            .widthIn(
                                max = if (compactMode) dockPillMaxWidthCompact(navItems.size)
                                else dockPillMaxWidth(navItems.size)
                            )
                    ) {
                        FloatingDockPill(
                            navItems = navItems,
                            selectedIndex = selectedIndex,
                            compactMode = compactMode,
                            cornerRadius = cornerRadius,
                            onItemCommit = commitPage
                        )
                    }
                    DockTrailing(
                        visible = hasMiniPlayer,
                        barHeight = barHeight,
                        isPlaying = isPlaying,
                        exitDirectionSign = exitDirectionSign,
                        onPlayPauseClick = onPlayPauseClick
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingDockPill(
    navItems: ImmutableList<BottomNavItem>,
    selectedIndex: Int,
    compactMode: Boolean,
    cornerRadius: Dp,
    onItemCommit: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val lens = remember { DockSpring(scope, selectedIndex.toFloat()) }
    val lift = remember { Animatable(0f) }
    val swell = remember { Animatable(0f) }
    val stretch = remember { DockSpring(scope, 0f) }
    var pressedIndex by remember { mutableIntStateOf(-1) }
    val view = LocalView.current
    val hapticsConfig = LocalAppHapticsConfig.current
    val density = LocalDensity.current
    val lensGrowthPx = with(density) { FloatingNavBarMetrics.LensGrowth.toPx() }
    val colorScheme = MaterialTheme.colorScheme
    val dockShape = RoundedCornerShape(cornerRadius)
    val latestOnItemCommit by rememberUpdatedState(onItemCommit)

    LaunchedEffect(selectedIndex) {
        if (pressedIndex < 0) {
            lens.springTo(selectedIndex.toFloat(), LensSettleSpring)
        }
    }

    fun trackLens(target: Float) = lens.springTo(target, LensTrackSpring)
    fun settleLens(target: Float) = lens.springTo(target, LensSettleSpring)
    fun liftTo(target: Float, spec: SpringSpec<Float>) {
        scope.launch {
            lift.animateTo(target, spec)
        }
    }
    fun swellTo(target: Float, spec: SpringSpec<Float>) {
        scope.launch {
            swell.animateTo(target, spec)
        }
    }
    fun trackStretch(target: Float) = stretch.springTo(target, LensTrackSpring)
    fun settleStretch() = stretch.springTo(0f, LensSettleSpring)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val swellScale = 1f + swell.value * minOf(
                    BarPressGrowth * 2f,
                    FloatingNavBarMetrics.MaxPressGrowthPx / size.maxDimension
                )
                scaleX = swellScale * (1f + abs(stretch.value) / size.width * PullStretch)
                scaleY = swellScale
                translationX = stretch.value
            }
            .shadow(
                elevation = FloatingNavBarMetrics.DockElevation,
                shape = dockShape,
                clip = false
            )
            .background(colorScheme.surfaceContainer, dockShape)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(FloatingNavBarMetrics.BarPadding)
                .pointerInput(navItems.size, selectedIndex) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            pass = PointerEventPass.Main,
                            requireUnconsumed = false
                        )
                        val downX = down.position.x
                        val extentForTouch = size.width.toFloat() / navItems.size
                        val lastSlotIndex = navItems.size - 1
                        fun positionAt(x: Float): Float {
                            val raw = x / extentForTouch - 0.5f
                            return when {
                                raw < 0f -> rubberBand(raw, LensOverdrag)
                                raw > lastSlotIndex ->
                                    lastSlotIndex + rubberBand(raw - lastSlotIndex, LensOverdrag)
                                else -> raw
                            }
                        }
                        fun indexAtPosition(position: Float): Int =
                            position.roundToInt().coerceIn(0, lastSlotIndex)
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)
                        var currentIndex = indexAtPosition(positionAt(downX))
                        var dragging = false
                        pressedIndex = currentIndex
                        liftTo(1f, LensLiftSpring)
                        swellTo(1f, LensLiftSpring)
                        settleLens(currentIndex.toFloat())
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                            if (change.changedToUpIgnoreConsumed()) {
                                val committed = pressedIndex
                                if (dragging) {
                                    val velocityX = velocityTracker.calculateVelocity().x
                                    val lead = velocityX / extentForTouch * FlingProjectionSec
                                    val flingIndex = (lens.targetValue + lead)
                                        .roundToInt()
                                        .coerceIn(currentIndex - 1, currentIndex + 1)
                                        .coerceIn(0, lastSlotIndex)
                                    if (flingIndex != currentIndex) {
                                        view.performAppHapticFeedback(
                                            hapticsConfig,
                                            HapticFeedbackConstants.CLOCK_TICK
                                        )
                                        currentIndex = flingIndex
                                        pressedIndex = flingIndex
                                    }
                                }
                                liftTo(0f, LensSettleSpring)
                                swellTo(0f, LensSettleSpring)
                                settleStretch()
                                settleLens(currentIndex.toFloat())
                                pressedIndex = -1
                                if (committed >= 0) {
                                    latestOnItemCommit(currentIndex)
                                }
                                break
                            }
                            if (!change.pressed) {
                                liftTo(0f, LensSettleSpring)
                                swellTo(0f, LensSettleSpring)
                                settleStretch()
                                settleLens(selectedIndex.toFloat())
                                pressedIndex = -1
                                break
                            }
                            if (change.positionChanged()) {
                                change.consume()
                                if (!dragging &&
                                    abs(change.position.x - downX) >= viewConfiguration.touchSlop
                                ) {
                                    dragging = true
                                }
                                if (dragging) {
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    val overshoot = change.position.x -
                                        change.position.x.coerceIn(0f, size.width.toFloat())
                                    trackStretch(
                                        rubberBand(
                                            overshoot,
                                            minOf(size.width, size.height).toFloat() * PullLimit
                                        )
                                    )
                                    val position = positionAt(change.position.x)
                                    val index = indexAtPosition(position)
                                    trackLens(position)
                                    if (index != currentIndex) {
                                        view.performAppHapticFeedback(
                                            hapticsConfig,
                                            HapticFeedbackConstants.CLOCK_TICK
                                        )
                                        currentIndex = index
                                        pressedIndex = index
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            val extentPx = with(density) { maxWidth.toPx() } / navItems.size

            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawLensPill(
                            lensPosition = lens.value,
                            lensVelocity = lens.velocity,
                            lift = lift.value,
                            extentPx = extentPx,
                            lensGrowthPx = lensGrowthPx,
                            color = colorScheme.onSecondaryContainer.copy(
                                alpha = 0.08f * lift.value.coerceIn(0f, 1f)
                            ).compositeOver(colorScheme.secondaryContainer)
                        )
                    }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        val lensRect = dockLensRect(
                            lensPosition = lens.value,
                            lensVelocity = lens.velocity,
                            extentPx = extentPx,
                            height = size.height,
                            lift = lift.value,
                            lensGrowthPx = lensGrowthPx
                        )
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(
                                Rect(0f, 0f, size.width, size.height),
                                Paint()
                            )
                            drawContent()
                            drawRoundRect(
                                color = Color.Black,
                                topLeft = lensRect.topLeft,
                                size = lensRect.size,
                                cornerRadius = CornerRadius(
                                    lensRect.height / 2f,
                                    lensRect.height / 2f
                                ),
                                blendMode = BlendMode.DstOut
                            )
                            canvas.restore()
                        }
                    }
            ) {
                DockItemsRow(
                    navItems = navItems,
                    compactMode = compactMode,
                    tintColor = colorScheme.onSurfaceVariant,
                    lens = lens,
                    lift = lift
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        val lensRect = dockLensRect(
                            lensPosition = lens.value,
                            lensVelocity = lens.velocity,
                            extentPx = extentPx,
                            height = size.height,
                            lift = lift.value,
                            lensGrowthPx = lensGrowthPx
                        )
                        val layerPad = lensGrowthPx * 1.6f + 1f
                        val layerBounds = Rect(
                            -layerPad,
                            -layerPad,
                            size.width + layerPad,
                            size.height + layerPad
                        )
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(layerBounds, Paint())
                            drawRoundRect(
                                color = Color.White,
                                topLeft = lensRect.topLeft,
                                size = lensRect.size,
                                cornerRadius = CornerRadius(
                                    lensRect.height / 2f,
                                    lensRect.height / 2f
                                )
                            )
                            canvas.saveLayer(
                                layerBounds,
                                Paint().apply { blendMode = BlendMode.SrcIn }
                            )
                            drawContent()
                            canvas.restore()
                            canvas.restore()
                        }
                    }
            ) {
                DockItemsRow(
                    navItems = navItems,
                    compactMode = compactMode,
                    tintColor = colorScheme.primary,
                    lens = lens,
                    lift = lift
                )
            }
        }
    }
}

private fun dockLensRect(
    lensPosition: Float,
    lensVelocity: Float,
    extentPx: Float,
    height: Float,
    lift: Float,
    lensGrowthPx: Float
): Rect {
    val stretch = (abs(lensVelocity) / JellySpeed).coerceIn(0f, 1f) * JellyStretch
    val lensWidth = (extentPx + lensGrowthPx * 2f * lift) * (1f + stretch)
    val lensHeight = (height + lensGrowthPx * 2f * lift) * (1f - stretch / 2f)
    val lensLeft = (lensPosition + 0.5f) * extentPx - lensWidth / 2f
    val lensTop = (height - lensHeight) / 2f
    return Rect(lensLeft, lensTop, lensLeft + lensWidth, lensTop + lensHeight)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLensPill(
    lensPosition: Float,
    lensVelocity: Float,
    lift: Float,
    extentPx: Float,
    lensGrowthPx: Float,
    color: Color
) {
    val rect = dockLensRect(
        lensPosition = lensPosition,
        lensVelocity = lensVelocity,
        extentPx = extentPx,
        height = size.height,
        lift = lift,
        lensGrowthPx = lensGrowthPx
    )
    drawRoundRect(
        color = color,
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(rect.height / 2f, rect.height / 2f)
    )
}

@Composable
private fun DockItemsRow(
    navItems: ImmutableList<BottomNavItem>,
    compactMode: Boolean,
    tintColor: Color,
    lens: DockSpring,
    lift: Animatable<Float, *>
) {
    Row(modifier = Modifier.fillMaxSize()) {
        navItems.forEachIndexed { index, item ->
            val glyphRes = item.selectedIconResId ?: item.iconResId
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .graphicsLayer {
                        val emphasis = (1f - abs(lens.value - index)).coerceIn(0f, 1f)
                        val scale = 1f + LensMagnify * emphasis * lift.value
                        scaleX = scale
                        scaleY = scale
                    }
                    .padding(horizontal = FloatingNavBarMetrics.LabelInset),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = glyphRes),
                    contentDescription = stringResource(id = item.labelResId),
                    modifier = Modifier.size(FloatingNavBarMetrics.IconSize),
                    tint = tintColor
                )
                if (!compactMode) {
                    Spacer(modifier = Modifier.height(FloatingNavBarMetrics.LabelGap))
                    Text(
                        text = stringResource(id = item.labelResId),
                        color = tintColor,
                        fontSize = FloatingNavBarMetrics.LabelSize,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.sp,
                        maxLines = 1,
                        softWrap = false,
                        style = androidx.compose.ui.text.TextStyle(
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                includeFontPadding = false
                            )
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun DockTrailing(
    visible: Boolean,
    barHeight: Dp,
    isPlaying: Boolean,
    exitDirectionSign: Float,
    onPlayPauseClick: () -> Unit
) {
    var mounted by remember { mutableStateOf(visible) }
    val reveal = remember { Animatable(if (visible) 1f else 0f) }
    val popScale = remember { Animatable(if (visible) 1f else SlotEnterScale) }
    val exiting = remember { Animatable(1f) }
    val density = LocalDensity.current
    val exitTranslatePx = with(density) { FloatingNavBarMetrics.ExitTranslate.toPx() }

    var skipInitialEnter by remember { mutableStateOf(visible) }

    LaunchedEffect(visible) {
        if (visible) {
            if (skipInitialEnter) {
                skipInitialEnter = false
                return@LaunchedEffect
            }
            mounted = true
            launch {
                reveal.snapTo(0f)
                reveal.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = SlotEnterDuration / 2,
                        easing = SlotRevealEasing
                    )
                )
            }
            launch {
                popScale.snapTo(SlotEnterScale)
                popScale.animateTo(targetValue = 1f, animationSpec = SlotPopSpring)
            }
            exiting.snapTo(1f)
        } else if (mounted) {
            exiting.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = SlotExitDuration,
                    easing = SlotExitEasing
                )
            )
            mounted = false
            reveal.snapTo(0f)
            popScale.snapTo(SlotEnterScale)
            exiting.snapTo(1f)
        }
    }

    val slotWidth by animateDpAsState(
        targetValue = if (visible) {
            FloatingNavBarMetrics.FabSize + FloatingNavBarMetrics.FabGap
        } else {
            0.dp
        },
        animationSpec = SlotSizeSpring,
        label = "DockSlotWidth"
    )

    Box(
        modifier = Modifier
            .then(
                if (mounted || slotWidth > 0.dp) {
                    Modifier.size(width = slotWidth, height = barHeight)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.CenterEnd
    ) {
        if (mounted) {
            val revealValue = reveal.value * exiting.value
            val enterScale = popScale.value
            val exitScale = 1f - (1f - SlotExitScale) * (1f - exiting.value)
            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.CenterEnd, unbounded = true)
                    .padding(start = FloatingNavBarMetrics.FabGap)
                    .size(FloatingNavBarMetrics.FabSize)
            ) {
                DockFab(
                    isPlaying = isPlaying,
                    scale = enterScale * exitScale,
                    reveal = revealValue,
                    translationXPx = exitDirectionSign * exitTranslatePx * (1f - exiting.value),
                    blurRadiusPx = with(density) {
                        FloatingNavBarMetrics.SlotBlur.toPx() * (1f - revealValue)
                    },
                    onClick = onPlayPauseClick
                )
            }
        }
    }
}

@Composable
private fun DockFab(
    isPlaying: Boolean,
    scale: Float,
    reveal: Float,
    translationXPx: Float,
    blurRadiusPx: Float,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressLift by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (pressed) FabLiftOnSpring else FabLiftOffSpring,
        label = "DockFabLift"
    )
    val view = LocalView.current
    val hapticsConfig = LocalAppHapticsConfig.current
    LaunchedEffect(pressed) {
        if (pressed) {
            view.performAppHapticFeedback(hapticsConfig, HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val pressGrowth = minOf(
                    0.25f,
                    FloatingNavBarMetrics.MaxPressGrowthPx / size.maxDimension
                )
                val swell = 1f + pressLift * pressGrowth
                scaleX = scale * swell
                scaleY = scale * swell
                translationX = translationXPx
                alpha = reveal.coerceIn(0f, 1f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadiusPx > 0.5f) {
                    renderEffect = android.graphics.RenderEffect.createBlurEffect(
                        blurRadiusPx,
                        blurRadiusPx,
                        android.graphics.Shader.TileMode.DECAL
                    ).asComposeRenderEffect()
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 0.dp,
        shadowElevation = FloatingNavBarMetrics.DockElevation
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            MorphingPlayPauseIcon(
                isPlaying = isPlaying,
                tint = MaterialTheme.colorScheme.onPrimary,
                size = FloatingNavBarMetrics.TrailingIconSize
            )
        }
    }
}
