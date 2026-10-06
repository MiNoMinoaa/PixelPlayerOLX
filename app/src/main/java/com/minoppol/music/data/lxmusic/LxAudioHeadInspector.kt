package com.minoppol.music.data.lxmusic

object LxAudioHeadInspector {

    enum class Container { MP3, FLAC, MP4, UNKNOWN }

    data class HeadInfo(
        val container: Container,
        val bitrateBps: Int? = null,
        val durationSec: Long? = null,
        val vbr: Boolean = false,
        val bitsPerSample: Int? = null,
    )

    fun inspect(head: ByteArray): HeadInfo {
        if (head.size < 4) return HeadInfo(Container.UNKNOWN)
        val u8: (Int) -> Int = { head[it].toInt() and 0xFF }
        // FLAC: "fLaC"
        if (u8(0) == 0x66 && u8(1) == 0x4C && u8(2) == 0x61 && u8(3) == 0x43) {
            return parseFlac(head)
        }
        // MP4: bytes 4..7 == "ftyp"
        if (head.size >= 8 && u8(4) == 0x66 && u8(5) == 0x74 && u8(6) == 0x79 && u8(7) == 0x70) {
            return HeadInfo(Container.MP4)
        }
        if (u8(0) == 0x49 && u8(1) == 0x44 && u8(2) == 0x33) { // "ID3"
            return parseMp3(head, skipId3v2(head))
        }
        return parseMp3(head, 0)
    }

    private fun parseFlac(head: ByteArray): HeadInfo {
        if (head.size < 26) return HeadInfo(Container.FLAC)
        val u8: (Int) -> Int = { head[it].toInt() and 0xFF }
        if ((u8(4) and 0x7F) != 0) return HeadInfo(Container.FLAC)
        val sampleRate = (u8(18) shl 12) or (u8(19) shl 4) or (u8(20) shr 4)
        val bitsPerSample = (((u8(20) and 0x01) shl 4) or (u8(21) shr 4)) + 1
        val totalSamples = ((u8(21) and 0x0F).toLong() shl 32) or
            (u8(22).toLong() shl 24) or
            (u8(23).toLong() shl 16) or
            (u8(24).toLong() shl 8) or
            u8(25).toLong()
        val duration = if (sampleRate > 0 && totalSamples > 0L) {
            totalSamples / sampleRate
        } else null
        return HeadInfo(
            container = Container.FLAC,
            durationSec = duration,
            bitsPerSample = bitsPerSample.takeIf { it in 1..64 },
        )
    }

    private fun skipId3v2(head: ByteArray): Int {
        if (head.size < 10) return 0
        val u8: (Int) -> Int = { head[it].toInt() and 0xFF }
        val size = ((u8(6) and 0x7F) shl 21) or
            ((u8(7) and 0x7F) shl 14) or
            ((u8(8) and 0x7F) shl 7) or
            (u8(9) and 0x7F)
        return 10 + size
    }

    private fun parseMp3(head: ByteArray, startOffset: Int): HeadInfo {
        var i = startOffset.coerceAtLeast(0)
        while (i + 3 < head.size) {
            val b0 = head[i].toInt() and 0xFF
            val b1 = head[i].toInt() and 0xFF
            if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                val version = (b1 shr 3) and 0x03 // 3=MPEG1, 2=MPEG2, 0=MPEG2.5
                val layer = (b1 shr 1) and 0x03   // 1=Layer III
                val b2 = head[i + 2].toInt() and 0xFF
                val bitrateIndex = (b2 shr 4) and 0x0F
                val bitrate = bitrateFor(version, layer, bitrateIndex)
                if (bitrate > 0) {
                    val vbr = hasXingHeader(head, i, version)
                    return HeadInfo(
                        container = Container.MP3,
                        bitrateBps = if (vbr) null else bitrate,
                        vbr = vbr,
                    )
                }
            }
            i++
        }
        return if (startOffset > 0) HeadInfo(Container.MP3) else HeadInfo(Container.UNKNOWN)
    }

    private fun hasXingHeader(head: ByteArray, frameOffset: Int, version: Int): Boolean {
        val channelsMonoGuessOffsets = if (version == 3) intArrayOf(32, 17) else intArrayOf(17, 9)
        for (rel in channelsMonoGuessOffsets) {
            val p = frameOffset + 4 + rel
            if (p + 4 <= head.size) {
                val tag = String(head, p, 4, Charsets.US_ASCII)
                if (tag == "Xing" || tag == "Info") return true
            }
        }
        return false
    }

    private fun bitrateFor(version: Int, layer: Int, index: Int): Int {
        if (index <= 0 || index >= 15) return 0
        val table = when {
            version == 3 && layer == 1 -> intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
            version == 3 && layer == 2 -> intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384)
            (version == 2 || version == 0) && layer == 1 -> intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
            else -> return 0
        }
        return table[index] * 1000
    }

    fun minPlausibleBitrateBps(quality: String): Int = when (quality) {
        LxQualities.Q128 -> 96_000
        LxQualities.Q320 -> 224_000
        LxQualities.FLAC -> 350_000
        LxQualities.FLAC24, LxQualities.ATMOS, LxQualities.MASTER -> 700_000
        else -> 96_000
    }

    fun expectsLossless(quality: String): Boolean =
        quality == LxQualities.FLAC ||
            quality == LxQualities.FLAC24 ||
            quality == LxQualities.MASTER
}
