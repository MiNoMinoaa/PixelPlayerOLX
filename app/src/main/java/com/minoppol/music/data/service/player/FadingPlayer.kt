package com.minoppol.music.data.service.player

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG_FADE = "FadingPlayerFade"

/**
 * Wraps the session-facing player so play/pause ramp the volume over a short fade instead
 * of cutting the audio hard.
 *
 * The fade target is captured from the inner player only when no fade is running: ReplayGain
 * writes its normalized volume directly on the engine's master player (not through this
 * wrapper), so tracking only wrapper-side [setVolume] calls would make every fade end at full
 * volume and defeat the normalization. When a fade is interrupted (rapid pause/play toggling),
 * the mid-ramp transient volume is stale, so the next fade targets the logical volume instead.
 * [getVolume] reports the logical volume during a fade rather than the transient mid-ramp value.
 */
class FadingPlayer(
    val innerPlayer: Player,
    private val scope: CoroutineScope,
) : ForwardingPlayer(innerPlayer) {

    private var fadeJob: Job? = null
    // Volume the player should sit at when no fade is running.
    private var logicalVolume = innerPlayer.volume

    override fun setVolume(volume: Float) {
        logicalVolume = volume
        if (fadeJob?.isActive != true) {
            super.setVolume(volume)
        }
    }

    override fun getVolume(): Float {
        return if (fadeJob?.isActive == true) logicalVolume else super.getVolume()
    }

    override fun play() {
        val hadActiveFade = fadeJob?.isActive == true
        fadeJob?.cancel()
        if (isPlaying) {
            if (hadActiveFade) super.setVolume(logicalVolume)
            super.play()
            return
        }

        val tEnter = SystemClock.elapsedRealtimeNanos()
        val targetVolume = if (hadActiveFade) {
            logicalVolume
        } else {
            logicalVolume = innerPlayer.volume
            logicalVolume
        }
        super.setVolume(0f)
        super.play()
        val tPlayCalled = SystemClock.elapsedRealtimeNanos()
        Log.d(TAG_FADE, "play(): super.play() called in ${(tPlayCalled - tEnter) / 1_000_000}ms, fading 0→$targetVolume over ${FADE_DURATION_MS}ms")

        fadeJob = scope.launch(Dispatchers.Main) {
            val stepTime = FADE_DURATION_MS / FADE_STEPS
            for (i in 1..FADE_STEPS) {
                if (!isActive) break
                val volume = (i.toFloat() / FADE_STEPS) * targetVolume
                super.setVolume(volume)
                delay(stepTime)
            }
            super.setVolume(targetVolume)
            Log.d(TAG_FADE, "play(): fade complete")
        }
    }

    override fun pause() {
        val hadActiveFade = fadeJob?.isActive == true
        fadeJob?.cancel()
        if (!isPlaying) {
            if (hadActiveFade) super.setVolume(logicalVolume)
            super.pause()
            return
        }

        val tEnter = SystemClock.elapsedRealtimeNanos()
        val startVolume = if (hadActiveFade) {
            logicalVolume
        } else {
            logicalVolume = innerPlayer.volume
            logicalVolume
        }

        // FIX: pause 必须立即调 super.pause()（和 play() 立即调 super.play() 对称），
        // 不能把 super.pause() 放在 fade 循环末尾（会让声音拖 FADE_DURATION_MS 才停，有点儿反直觉）
        super.pause()
        val tPauseCalled = SystemClock.elapsedRealtimeNanos()
        Log.d(TAG_FADE, "pause(): super.pause() called in ${(tPauseCalled - tEnter) / 1_000_000}ms (was deferred by ${FADE_DURATION_MS}ms before fix)")

        fadeJob = scope.launch(Dispatchers.Main) {
            val stepTime = FADE_DURATION_MS / FADE_STEPS
            for (i in 1..FADE_STEPS) {
                if (!isActive) break
                val volume = ((FADE_STEPS - i).toFloat() / FADE_STEPS) * startVolume
                super.setVolume(volume)
                delay(stepTime)
            }
            super.setVolume(0f)
            super.setVolume(startVolume)
            Log.d(TAG_FADE, "pause(): fade complete, volume restored to $startVolume")
        }
    }

    private companion object {
        const val FADE_DURATION_MS = 500L
        const val FADE_STEPS = 25
    }
}
