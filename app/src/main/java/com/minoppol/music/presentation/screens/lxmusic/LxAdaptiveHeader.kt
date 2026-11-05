package com.minoppol.music.presentation.screens.lxmusic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 发现页头部尺寸基准：按屏幕高度给整条标题栏留 1/4 预算反推缩放，全四页和悬浮按钮都从这里拿数
data class LxHeaderMetrics(
    val scale: Float,
    val platformBand: Dp,
    val tabBarTop: Dp,
    val tabBarBottom: Dp,
    val moduleRowTop: Dp,
    val moduleRowBottom: Dp,
    val moduleRowHPad: Dp,
    val moduleButtonVPad: Dp,
    val moduleButtonHPad: Dp,
    val moduleIcon: Dp,
    val moduleGap: Dp,
    val searchBarHeight: Dp,
    val searchIcon: Dp,
    val searchActionSize: Dp,
    val searchActionIcon: Dp,
    val searchClearIcon: Dp,
    val searchLeadSpacer: Dp,
    val chipRowHeight: Dp,
    val chipVPad: Dp,
    val searchTopOffset: Dp,
    val searchSwitchOffset: Dp,
    val searchSwitchHeight: Dp,
    val searchHeaderHeight: Dp,
    val fabSize: Dp,
    val fabIconLarge: Dp,
    val fabIconSmall: Dp,
    val capsuleSize: Dp,
    val capsuleIcon: Dp,
    val mineAvatar: Dp,
    val mineAvatarIcon: Dp,
    val mineProfileVPad: Dp,
    val mineTabHeight: Dp,
)

// 各档数值：横杠前是原始设计值，未缩放时（大屏）就是最终值
fun buildLxHeaderMetrics(scale: Float): LxHeaderMetrics {
    val hd = { v: Int -> (v * scale).dp }
    val fabScale = (scale * 0.875f).coerceAtLeast(0.80f)
    val hfab = { v: Int -> (v * fabScale).dp }
    val fabSize = hfab(48)
    val capsuleSize = hfab(44)
    val searchBarHeight = hd(44)
    val searchTopOffset = hd(6)
    val searchSwitchHeight = hd(28)
    val searchSwitchOffset = searchTopOffset + searchBarHeight + hd(5)
    val tabBarTop = hd(3)
    val tabBarBottom = hd(2)
    val platformBand = maxOf(hd(54), tabBarTop + 48.dp + tabBarBottom + 2.dp)
    val chipVPad = hd(7)
    val chipRowHeight = capsuleSize + 14.dp + hd(2)
    return LxHeaderMetrics(
        scale = scale,
        platformBand = platformBand,
        tabBarTop = tabBarTop,
        tabBarBottom = tabBarBottom,
        moduleRowTop = hd(9),
        moduleRowBottom = hd(2),
        moduleRowHPad = hd(12),
        moduleButtonVPad = hd(7),
        moduleButtonHPad = hd(8),
        moduleIcon = hd(16),
        moduleGap = hd(4),
        searchBarHeight = searchBarHeight,
        searchIcon = hd(22),
        searchActionSize = hd(38),
        searchActionIcon = hd(20),
        searchClearIcon = hd(20),
        searchLeadSpacer = hd(14),
        chipRowHeight = chipRowHeight,
        chipVPad = chipVPad,
        searchTopOffset = searchTopOffset,
        searchSwitchOffset = searchSwitchOffset,
        searchSwitchHeight = searchSwitchHeight,
        searchHeaderHeight = searchSwitchOffset + searchSwitchHeight,
        fabSize = fabSize,
        fabIconLarge = (fabSize.value * 26f / 48f).dp,
        fabIconSmall = (fabSize.value * 22f / 48f).dp,
        capsuleSize = capsuleSize,
        capsuleIcon = (capsuleSize.value * 18f / 44f).dp,
        mineAvatar = hd(38),
        mineAvatarIcon = hd(20),
        mineProfileVPad = hd(6),
        mineTabHeight = hd(30),
    )
}

// 最重形态（歌单页带搜索框+分类）设计总高约 206dp，屏高的 1/4 除以它得到缩放
fun lxHeaderScaleFor(screenHeightDp: Int): Float =
    (screenHeightDp * 0.25f / 206f).coerceIn(0.75f, 1.05f)

@Composable
fun rememberLxHeaderMetrics(): LxHeaderMetrics {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    return remember(screenHeight) { buildLxHeaderMetrics(lxHeaderScaleFor(screenHeight)) }
}
