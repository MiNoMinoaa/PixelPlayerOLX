package com.minoppol.music.presentation.navigation

internal fun isMainRootRoute(route: String?): Boolean = when (route) {
    Screen.Home.route,
    Screen.LxMusic.route,
    Screen.Library.route,
    Screen.RecentlyPlayedTab.route,
    Screen.Search.route -> true
    else -> false
}

internal fun mainRootRouteIndex(route: String?): Int? = when (route) {
    Screen.Home.route -> 0
    Screen.LxMusic.route -> 1
    Screen.Library.route -> 2
    Screen.RecentlyPlayedTab.route -> 3
    Screen.Search.route -> 4
    else -> null
}
