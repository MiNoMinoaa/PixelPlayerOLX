package com.minoppol.music.presentation.components

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader as AndroidShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

class BlurEffectCache {
    private var lastRadiusPx: Float = Float.NaN
    private var cached: RenderEffect? = null

    @RequiresApi(Build.VERSION_CODES.S)
    fun get(radiusPx: Float): RenderEffect? {
        if (radiusPx <= 0f) {
            lastRadiusPx = 0f
            cached = null
            return null
        }
        if (radiusPx != lastRadiusPx) {
            lastRadiusPx = radiusPx
            cached = AndroidRenderEffect
                .createBlurEffect(radiusPx, radiusPx, AndroidShader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
        return cached
    }
}
