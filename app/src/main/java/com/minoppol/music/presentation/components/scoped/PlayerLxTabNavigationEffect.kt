package com.minoppol.music.presentation.components.scoped

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.minoppol.music.presentation.navigation.Screen
import com.minoppol.music.presentation.navigation.navigateToTopLevelSafely
import com.minoppol.music.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun PlayerLxTabNavigationEffect(
    navController: NavHostController,
    sheetCollapsedTargetY: Float,
    sheetMotionController: SheetMotionController,
    playerViewModel: PlayerViewModel
) {
    val latestSheetCollapsedTargetY by rememberUpdatedState(sheetCollapsedTargetY)
    val showLxMusicTab by playerViewModel.showLxMusicTab.collectAsStateWithLifecycle()
    LaunchedEffect(navController, showLxMusicTab) {
        if (!showLxMusicTab) return@LaunchedEffect
        playerViewModel.lxTabNavRequests.collectLatest {
            sheetMotionController.snapCollapsed(latestSheetCollapsedTargetY)
            playerViewModel.collapsePlayerSheet()

            navController.navigateToTopLevelSafely(Screen.LxMusic.route)
        }
    }
}
