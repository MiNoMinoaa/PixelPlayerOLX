package com.minoppol.music.utils

import android.Manifest
import android.os.Build

@android.annotation.SuppressLint("InlinedApi")
internal fun folderArtworkPermissions(sdkVersion: Int): List<String> = when {
    sdkVersion >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )
    sdkVersion >= Build.VERSION_CODES.TIRAMISU -> listOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}
