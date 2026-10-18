package com.minoppol.music.presentation.components

import com.minoppol.music.presentation.navigation.navigateToTopLevelSafely

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.minoppol.music.BottomNavItem
import com.minoppol.music.data.preferences.NavBarStyle
import com.minoppol.music.presentation.components.scoped.CustomNavigationBarItem
import com.minoppol.music.presentation.navigation.Screen
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val NavBarContentHeight = 90.dp
internal val NavBarCompactContentHeight = 64.dp
internal val NavBarContentHeightFullWidth = NavBarContentHeight
private val MainScreenBottomGradientExtraHeight = MiniPlayerHeight + MiniPlayerBottomSpacer + 8.dp
internal val MaxNavigationBarBottomInset = 96.dp

internal fun sanitizeNavigationBarBottomInset(systemNavBarInset: Dp): Dp {
    if (!systemNavBarInset.value.isFinite()) return 0.dp
    return systemNavBarInset.coerceIn(0.dp, MaxNavigationBarBottomInset)
}

internal fun isUsefulBottomInset(systemNavBarInset: Dp): Boolean = systemNavBarInset >= 8.dp

internal fun fallbackFloatingBottomInset(containerHeight: Dp): Dp =
    (containerHeight * 0.015f).coerceIn(8.dp, 16.dp)

internal fun calculatePlayerSheetCollapsedTargetY(
    containerHeightPx: Float,
    collapsedContentHeightPx: Float,
    bottomMarginPx: Float,
    bottomSpacerPx: Float
): Float {
    val safeContainerHeightPx = containerHeightPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeCollapsedContentHeightPx = collapsedContentHeightPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeBottomMarginPx = bottomMarginPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeBottomSpacerPx = bottomSpacerPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val maxTargetY = (safeContainerHeightPx - safeCollapsedContentHeightPx).coerceAtLeast(0f)

    return (safeContainerHeightPx - safeCollapsedContentHeightPx - safeBottomMarginPx - safeBottomSpacerPx)
        .coerceIn(0f, maxTargetY)
}

internal fun resolveNavBarContentHeight(compactMode: Boolean): Dp =
    if (compactMode) NavBarCompactContentHeight else NavBarContentHeight

internal fun resolveMainScreenBottomGradientHeight(compactMode: Boolean): Dp =
    resolveNavBarContentHeight(compactMode) + MainScreenBottomGradientExtraHeight

internal fun resolveNavBarSurfaceHeight(
    navBarStyle: String,
    systemNavBarInset: Dp,
    compactMode: Boolean
): Dp = if (navBarStyle == NavBarStyle.FLOATING) {
    resolveFloatingNavBarHeight(compactMode)
} else {
    resolveNavBarContentHeight(compactMode)
}

internal fun resolveFloatingNavBarOccupiedHeight(
    systemNavBarInset: Dp,
    compactMode: Boolean
): Dp = FloatingNavBarMetrics.ShadowRoom + resolveFloatingNavBarHeight(compactMode) +
    maxOf(FloatingNavBarMetrics.EdgeMargin, sanitizeNavigationBarBottomInset(systemNavBarInset))

internal fun resolveNavBarOccupiedHeight(
    systemNavBarInset: Dp,
    compactMode: Boolean
): Dp = resolveNavBarContentHeight(compactMode) + systemNavBarInset

internal fun resolveFloatingNavBarHeight(compactMode: Boolean): Dp =
    if (compactMode) FloatingNavBarMetrics.CompactBarHeight else FloatingNavBarMetrics.BarHeight

internal class NavRowTapState {
    var lastSearchTapTimestamp: Long by mutableStateOf(0L)
}

@Composable
internal fun RowScope.NavBarNavItem(
    navController: NavHostController,
    item: BottomNavItem,
    isSelected: Boolean,
    currentRoute: String?,
    compactMode: Boolean,
    tapState: NavRowTapState,
    scope: CoroutineScope,
    onSearchIconDoubleTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latestCurrentRoute by rememberUpdatedState(currentRoute)
    val latestOnSearchIconDoubleTap by rememberUpdatedState(onSearchIconDoubleTap)
    val latestNavigationEnabled by rememberUpdatedState(currentRoute != null)

    val selectedColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val indicatorColorFromTheme = MaterialTheme.colorScheme.secondaryContainer

    val iconPainterResId = if (isSelected && item.selectedIconResId != null && item.selectedIconResId != 0) {
        item.selectedIconResId
    } else {
        item.iconResId
    }
    val iconLambda: @Composable () -> Unit = remember(iconPainterResId, item.labelResId) {
        {
            Icon(
                painter = painterResource(id = iconPainterResId),
                contentDescription = stringResource(item.labelResId)
            )
        }
    }
    val selectedIconLambda: @Composable () -> Unit = remember(iconPainterResId, item.labelResId) {
        {
            Icon(
                painter = painterResource(id = iconPainterResId),
                contentDescription = stringResource(item.labelResId)
            )
        }
    }
    val labelLambda: (@Composable () -> Unit)? = if (compactMode) {
        null
    } else {
        remember(item.labelResId) {
            { Text(stringResource(item.labelResId)) }
        }
    }
    val onClickLambda: () -> Unit = remember(item.screen.route, navController, scope) {
        click@{
            if (!latestNavigationEnabled) {
                tapState.lastSearchTapTimestamp = 0L
                return@click
            }

            val itemRoute = item.screen.route
            val isSearchTab = itemRoute == Screen.Search.route
            val isAlreadySelected = latestCurrentRoute == itemRoute

            if (isSearchTab) {
                val now = SystemClock.elapsedRealtime()
                val isDoubleTap = now - tapState.lastSearchTapTimestamp <= 350L
                tapState.lastSearchTapTimestamp = now

                if (!isAlreadySelected) {
                    if (!navController.navigateToTopLevelSafely(itemRoute)) {
                        tapState.lastSearchTapTimestamp = 0L
                        return@click
                    }
                }

                if (isDoubleTap) {
                    tapState.lastSearchTapTimestamp = 0L
                    if (isAlreadySelected) {
                        latestOnSearchIconDoubleTap()
                    } else {
                        scope.launch {
                            delay(160L)
                            latestOnSearchIconDoubleTap()
                        }
                    }
                }
            } else if (!isAlreadySelected) {
                tapState.lastSearchTapTimestamp = 0L
                navController.navigateToTopLevelSafely(itemRoute)
            } else {
                tapState.lastSearchTapTimestamp = 0L
            }
        }
    }
    CustomNavigationBarItem(
        modifier = modifier,
        selected = isSelected,
        onClick = onClickLambda,
        enabled = currentRoute != null,
        compactMode = compactMode,
        icon = iconLambda,
        selectedIcon = selectedIconLambda,
        label = labelLambda,
        contentDescription = stringResource(item.labelResId),
        alwaysShowLabel = true,
        selectedIconColor = selectedColor,
        unselectedIconColor = unselectedColor,
        selectedTextColor = selectedColor,
        unselectedTextColor = unselectedColor,
        indicatorColor = indicatorColorFromTheme
    )
}

@Composable
private fun PlayerInternalNavigationItemsRow(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    navBarStyle: String,
    compactMode: Boolean,
    bottomBarPadding: Dp,
    onSearchIconDoubleTap: () -> Unit
) {
    val navBarInsetPadding = sanitizeNavigationBarBottomInset(
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    )
    val innerRowPadding = (navBarInsetPadding - bottomBarPadding).coerceAtLeast(0.dp)

    val rowModifier = if (navBarStyle == NavBarStyle.FULL_WIDTH) {
        modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = innerRowPadding, start = 12.dp, end = 12.dp)
    } else {
        modifier
            .padding(start = 10.dp, end = 10.dp, bottom = innerRowPadding)
            .fillMaxWidth()
    }
    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val scope = rememberCoroutineScope()
        val tapState = remember { NavRowTapState() }
        navItems.forEach { item ->
            val isSelected = currentRoute != null && currentRoute == item.screen.route
            NavBarNavItem(
                navController = navController,
                item = item,
                isSelected = isSelected,
                currentRoute = currentRoute,
                compactMode = compactMode,
                tapState = tapState,
                scope = scope,
                onSearchIconDoubleTap = onSearchIconDoubleTap,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun PlayerInternalNavigationBar(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    navBarStyle: String,
    compactMode: Boolean,
    bottomBarPadding: Dp = 0.dp,
    onSearchIconDoubleTap: () -> Unit = {}
) {
    PlayerInternalNavigationItemsRow(
        navController = navController,
        navItems = navItems,
        currentRoute = currentRoute,
        navBarStyle = navBarStyle,
        compactMode = compactMode,
        bottomBarPadding = bottomBarPadding,
        onSearchIconDoubleTap = onSearchIconDoubleTap,
        modifier = modifier
    )
}
