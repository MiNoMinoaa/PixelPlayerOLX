package com.lostf1sh.pixelplayeross.data.model

import androidx.compose.runtime.Immutable
import java.io.File

@Immutable
data class DirectoryItem(
    val path: String,
    val isAllowed: Boolean
) {
    val displayName: String
        get() = File(path).name.ifEmpty { path } // Shows the folder name, or the path if it's the root
}
