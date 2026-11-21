package com.minoppol.music.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.minoppol.music.R

internal fun lxSourceAliasResId(source: String): Int? = when (source) {
    "wy" -> R.string.lx_source_alias_netease
    "kw" -> R.string.lx_source_alias_kuwo
    "tx" -> R.string.lx_source_alias_qq
    "kg" -> R.string.lx_source_alias_kugou
    else -> null
}

internal fun lxSourceOriginalResId(source: String): Int? = when (source) {
    "wy" -> R.string.lx_source_netease
    "kw" -> R.string.lx_source_kuwo
    "tx" -> R.string.lx_source_qq
    "kg" -> R.string.lx_source_kugou
    else -> null
}

@Composable
internal fun lxSourceDisplayName(source: String, nameStyle: String): String {
    val aliasResId = lxSourceAliasResId(source)
    val originalResId = lxSourceOriginalResId(source)
    return if (nameStyle == "alias" && aliasResId != null) {
        stringResource(aliasResId)
    } else if (originalResId != null) {
        stringResource(originalResId)
    } else {
        source
    }
}
