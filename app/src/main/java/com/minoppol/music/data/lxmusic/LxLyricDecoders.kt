package com.minoppol.music.data.lxmusic

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import kotlin.math.abs

/**
 * Decoders and converters for the platforms' native word-timed lyric formats.
 *
 * All formats are normalized to Enhanced LRC with ABSOLUTE word timestamps:
 *   [mm:ss.xx]<mm:ss.xx>word1<mm:ss.xx>word2…
 * which the existing [com.minoppol.music.utils.LyricsUtils.parseLyrics] already
 * understands (word tags + vN:/bracket voice labels + [bg:] harmony lines).
 *
 * Ported 1:1 from lx-music-mobile's JS implementations:
 *  - KRC (Kugou): base64 → skip 4 bytes → XOR 16-byte key → zlib  (kg/util.js)
 *  - QRC (QQ):    hex → non-standard 3DES-ECB → zlib inflate Z_SYNC_FLUSH (tx/qrcDecode.js)
 *  - LRCx (Kuwo): tp=content header → zlib → base64 → XOR "yeelion" (kw/decodeLyric.js)
 *  - YRC (Netease): JSON lines {"t":..,"c":[{"tx":..}]} with (offset,dur,0) word tags (wy/lyric.js)
 *  - MRC (Migu):  TEA-variant decrypt, [start,dur](offset,dur) tags (mg/utils/mrc.js)
 */
object LxLyricDecoders {

    // ------------------------------------------------------------------
    // Byte-level decryptors
    // ------------------------------------------------------------------

    private val KRC_KEY = byteArrayOf(
        0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47,
        0x51, 0x36, 0x31, 0x2d, 0xce.toByte(), 0xd2.toByte(), 0x6e, 0x69,
    )

    private val B64_DECODER = java.util.Base64.getMimeDecoder() // tolerates newlines like android Base64.DEFAULT

    fun base64DecodeBytes(base64Content: String): ByteArray? =
        runCatching { B64_DECODER.decode(base64Content) }.getOrNull()

    /** Kugou KRC: raw payload bytes → skip first 4 bytes → XOR key → zlib inflate. */
    fun decodeKrcBytes(payload: ByteArray): String? = runCatching {
        if (payload.size <= 4) return null
        val out = ByteArray(payload.size - 4)
        for (i in out.indices) {
            out[i] = (payload[i + 4].toInt() xor KRC_KEY[i % 16].toInt()).toByte()
        }
        String(zlibInflateLenient(out), Charsets.UTF_8)
    }.getOrNull()

    /** Kugou KRC: base64 → skip first 4 bytes → XOR key → zlib inflate. */
    fun decodeKrc(base64Content: String): String? =
        base64DecodeBytes(base64Content)?.let { decodeKrcBytes(it) }

    private val KW_KEY = "yeelion".toByteArray(Charsets.UTF_8)

    /**
     * Kuwo LRCx: response starts with "tp=content", a CRLFCRLF separates headers
     * from the zlib stream; after inflate, when lrcx=1, the payload is base64 →
     * XOR "yeelion" → the LRCx text.
     */
    fun decodeKuwoLrcx(rawResponse: ByteArray, isLrcx: Boolean): String? = runCatching {
        val head = String(rawResponse, 0, minOf(10, rawResponse.size), Charsets.UTF_8).lowercase()
        if (head != "tp=content") return null
        val sep = indexOfBytes(rawResponse, byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte()))
        if (sep < 0) return null
        val inflated = zlibInflateLenient(rawResponse.copyOfRange(sep + 4, rawResponse.size))
        if (!isLrcx) return String(inflated, Charsets.UTF_8)
        val b64 = B64_DECODER.decode(inflated)
        val out = ByteArray(b64.size)
        var i = 0
        while (i < b64.size) {
            var j = 0
            while (j < KW_KEY.size && i < b64.size) {
                out[i] = (b64[i].toInt() xor KW_KEY[j].toInt()).toByte()
                i++; j++
            }
        }
        String(out, Charsets.UTF_8)
    }.getOrNull()

    private fun indexOfBytes(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun zlibInflate(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size * 4)
        val buf = ByteArray(4096)
        val inflater = Inflater()
        inflater.setInput(data)
        try {
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n > 0) {
                    out.write(buf, 0, n)
                } else if (inflater.needsInput() || inflater.needsDictionary()) {
                    break
                }
            }
        } catch (_: java.util.zip.DataFormatException) {
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }

    // ------------------------------------------------------------------
    // QRC: non-standard 3DES (S-boxes S2[23]=15, S4[53]=10 differ from standard DES)
    // Fixed key !@#)(*$%123ZXC!@!@#)(NHL. Ported from lx-music tx/qrcDecode.js.
    // ------------------------------------------------------------------

    private const val DES_ENCRYPT = 1
    private const val DES_DECRYPT = 0

    private val QRC_KEY = byteArrayOf(
        0x21, 0x40, 0x23, 0x29, 0x28, 0x2a, 0x24, 0x25, 0x31, 0x32, 0x33, 0x5a,
        0x58, 0x43, 0x21, 0x40, 0x21, 0x40, 0x23, 0x29, 0x28, 0x4e, 0x48, 0x4c,
    )

    private val QRC_SBOX = arrayOf(
        intArrayOf(
            14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7, 0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8,
            4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0, 15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13,
        ),
        intArrayOf(
            15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10, 3, 13, 4, 7, 15, 2, 8, 15, 12, 0, 1, 10, 6, 9, 11, 5,
            0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15, 13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9,
        ),
        intArrayOf(
            10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8, 13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1,
            13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7, 1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12,
        ),
        intArrayOf(
            7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15, 13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9,
            10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4, 3, 15, 0, 6, 10, 10, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14,
        ),
        intArrayOf(
            2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9, 14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6,
            4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14, 11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3,
        ),
        intArrayOf(
            12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11, 10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8,
            9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6, 4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13,
        ),
        intArrayOf(
            4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1, 13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6,
            1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2, 6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12,
        ),
        intArrayOf(
            13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7, 1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2,
            7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8, 2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11,
        ),
    )

    private val KEY_RND_SHIFT = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)
    private val KEY_PERM_C = intArrayOf(
        56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17, 9, 1, 58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35,
    )
    private val KEY_PERM_D = intArrayOf(
        62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37, 29, 21, 13, 5, 60, 52, 44, 36, 28, 20, 12, 4, 27, 19, 11, 3,
    )
    private val KEY_COMPRESSION = intArrayOf(
        13, 16, 10, 23, 0, 4, 2, 27, 14, 5, 20, 9, 22, 18, 11, 3, 25, 7, 15, 6, 26, 19, 12, 1, 40, 51, 30, 36, 46,
        54, 29, 39, 50, 44, 32, 47, 43, 48, 38, 55, 33, 52, 45, 41, 49, 35, 28, 31,
    )

    // All int ops mask to unsigned 32-bit like JS `>>> 0`.
    private fun u32(x: Int): Int = x.toInt()
    private fun shl32(x: Int, n: Int): Int = (x shl n) // upper bits discarded by Int
    private fun bitnum(a: ByteArray, b: Int, c: Int): Int =
        ((((a[(b / 32) * 4 + 3 - ((b % 32) / 8)].toInt() and 0xFF) ushr (7 - (b % 8))) and 1) shl c)
    private fun bitnumIntr(a: Int, b: Int, c: Int): Int = (((a ushr (31 - b)) and 1) shl c)
    private fun bitnumIntl(a: Int, b: Int, c: Int): Int = ((a shl b) and Int.MIN_VALUE) ushr c
    private fun sboxBit(a: Int): Int = (a and 32) or ((a and 31) ushr 1) or ((a and 1) shl 4)

    private fun initialPermutation(input: ByteArray): IntArray {
        val s0 = (
            bitnum(input, 57, 31) or bitnum(input, 49, 30) or bitnum(input, 41, 29) or bitnum(input, 33, 28) or
                bitnum(input, 25, 27) or bitnum(input, 17, 26) or bitnum(input, 9, 25) or bitnum(input, 1, 24) or
                bitnum(input, 59, 23) or bitnum(input, 51, 22) or bitnum(input, 43, 21) or bitnum(input, 35, 20) or
                bitnum(input, 27, 19) or bitnum(input, 19, 18) or bitnum(input, 11, 17) or bitnum(input, 3, 16) or
                bitnum(input, 61, 15) or bitnum(input, 53, 14) or bitnum(input, 45, 13) or bitnum(input, 37, 12) or
                bitnum(input, 29, 11) or bitnum(input, 21, 10) or bitnum(input, 13, 9) or bitnum(input, 5, 8) or
                bitnum(input, 63, 7) or bitnum(input, 55, 6) or bitnum(input, 47, 5) or bitnum(input, 39, 4) or
                bitnum(input, 31, 3) or bitnum(input, 23, 2) or bitnum(input, 15, 1) or bitnum(input, 7, 0)
            )
        val s1 = (
            bitnum(input, 56, 31) or bitnum(input, 48, 30) or bitnum(input, 40, 29) or bitnum(input, 32, 28) or
                bitnum(input, 24, 27) or bitnum(input, 16, 26) or bitnum(input, 8, 25) or bitnum(input, 0, 24) or
                bitnum(input, 58, 23) or bitnum(input, 50, 22) or bitnum(input, 42, 21) or bitnum(input, 34, 20) or
                bitnum(input, 26, 19) or bitnum(input, 18, 18) or bitnum(input, 10, 17) or bitnum(input, 2, 16) or
                bitnum(input, 60, 15) or bitnum(input, 52, 14) or bitnum(input, 44, 13) or bitnum(input, 36, 12) or
                bitnum(input, 28, 11) or bitnum(input, 20, 10) or bitnum(input, 12, 9) or bitnum(input, 4, 8) or
                bitnum(input, 62, 7) or bitnum(input, 54, 6) or bitnum(input, 46, 5) or bitnum(input, 38, 4) or
                bitnum(input, 30, 3) or bitnum(input, 22, 2) or bitnum(input, 14, 1) or bitnum(input, 6, 0)
            )
        return intArrayOf(s0, s1)
    }

    private fun inversePermutation(s0: Int, s1: Int, out: ByteArray) {
        out[3] = (
            bitnumIntr(s1, 7, 7) or bitnumIntr(s0, 7, 6) or bitnumIntr(s1, 15, 5) or bitnumIntr(s0, 15, 4) or
                bitnumIntr(s1, 23, 3) or bitnumIntr(s0, 23, 2) or bitnumIntr(s1, 31, 1) or bitnumIntr(s0, 31, 0)
            ).toByte()
        out[2] = (
            bitnumIntr(s1, 6, 7) or bitnumIntr(s0, 6, 6) or bitnumIntr(s1, 14, 5) or bitnumIntr(s0, 14, 4) or
                bitnumIntr(s1, 22, 3) or bitnumIntr(s0, 22, 2) or bitnumIntr(s1, 30, 1) or bitnumIntr(s0, 30, 0)
            ).toByte()
        out[1] = (
            bitnumIntr(s1, 5, 7) or bitnumIntr(s0, 5, 6) or bitnumIntr(s1, 13, 5) or bitnumIntr(s0, 13, 4) or
                bitnumIntr(s1, 21, 3) or bitnumIntr(s0, 21, 2) or bitnumIntr(s1, 29, 1) or bitnumIntr(s0, 29, 0)
            ).toByte()
        out[0] = (
            bitnumIntr(s1, 4, 7) or bitnumIntr(s0, 4, 6) or bitnumIntr(s1, 12, 5) or bitnumIntr(s0, 12, 4) or
                bitnumIntr(s1, 20, 3) or bitnumIntr(s0, 20, 2) or bitnumIntr(s1, 28, 1) or bitnumIntr(s0, 28, 0)
            ).toByte()
        out[7] = (
            bitnumIntr(s1, 3, 7) or bitnumIntr(s0, 3, 6) or bitnumIntr(s1, 11, 5) or bitnumIntr(s0, 11, 4) or
                bitnumIntr(s1, 19, 3) or bitnumIntr(s0, 19, 2) or bitnumIntr(s1, 27, 1) or bitnumIntr(s0, 27, 0)
            ).toByte()
        out[6] = (
            bitnumIntr(s1, 2, 7) or bitnumIntr(s0, 2, 6) or bitnumIntr(s1, 10, 5) or bitnumIntr(s0, 10, 4) or
                bitnumIntr(s1, 18, 3) or bitnumIntr(s0, 18, 2) or bitnumIntr(s1, 26, 1) or bitnumIntr(s0, 26, 0)
            ).toByte()
        out[5] = (
            bitnumIntr(s1, 1, 7) or bitnumIntr(s0, 1, 6) or bitnumIntr(s1, 9, 5) or bitnumIntr(s0, 9, 4) or
                bitnumIntr(s1, 17, 3) or bitnumIntr(s0, 17, 2) or bitnumIntr(s1, 25, 1) or bitnumIntr(s0, 25, 0)
            ).toByte()
        out[4] = (
            bitnumIntr(s1, 0, 7) or bitnumIntr(s0, 0, 6) or bitnumIntr(s1, 8, 5) or bitnumIntr(s0, 8, 4) or
                bitnumIntr(s1, 16, 3) or bitnumIntr(s0, 16, 2) or bitnumIntr(s1, 24, 1) or bitnumIntr(s0, 24, 0)
            ).toByte()
    }

    private fun desF(state: Int, key: ByteArray): Int {
        val t1 = (
            bitnumIntl(state, 31, 0) or ((state and 0xF0000000.toInt()) ushr 1) or bitnumIntl(state, 4, 5) or
                bitnumIntl(state, 3, 6) or ((state and 0x0F000000) ushr 3) or bitnumIntl(state, 8, 11) or
                bitnumIntl(state, 7, 12) or ((state and 0x00F00000) ushr 5) or bitnumIntl(state, 12, 17) or
                bitnumIntl(state, 11, 18) or ((state and 0x000F0000) ushr 7) or bitnumIntl(state, 16, 23)
            )
        val t2 = (
            bitnumIntl(state, 15, 0) or (shl32(state and 0x0000F000, 15)) or bitnumIntl(state, 20, 5) or
                bitnumIntl(state, 19, 6) or (shl32(state and 0x00000F00, 13)) or bitnumIntl(state, 24, 11) or
                bitnumIntl(state, 23, 12) or (shl32(state and 0x000000F0, 11)) or bitnumIntl(state, 28, 17) or
                bitnumIntl(state, 27, 18) or (shl32(state and 0x0000000F, 9)) or bitnumIntl(state, 0, 23)
            )

        val lrgstate = intArrayOf(
            (t1 ushr 24) and 0xFF, (t1 ushr 16) and 0xFF, (t1 ushr 8) and 0xFF,
            (t2 ushr 24) and 0xFF, (t2 ushr 16) and 0xFF, (t2 ushr 8) and 0xFF,
        )
        for (i in 0 until 6) lrgstate[i] = lrgstate[i] xor (key[i].toInt() and 0xFF)

        val s = (
            (QRC_SBOX[0][sboxBit(lrgstate[0] ushr 2)] shl 28) or
                (QRC_SBOX[1][sboxBit(((lrgstate[0] and 0x03) shl 4) or (lrgstate[1] ushr 4))] shl 24) or
                (QRC_SBOX[2][sboxBit(((lrgstate[1] and 0x0F) shl 2) or (lrgstate[2] ushr 6))] shl 20) or
                (QRC_SBOX[3][sboxBit(lrgstate[2] and 0x3F)] shl 16) or
                (QRC_SBOX[4][sboxBit(lrgstate[3] ushr 2)] shl 12) or
                (QRC_SBOX[5][sboxBit(((lrgstate[3] and 0x03) shl 4) or (lrgstate[4] ushr 4))] shl 8) or
                (QRC_SBOX[6][sboxBit(((lrgstate[4] and 0x0F) shl 2) or (lrgstate[5] ushr 6))] shl 4) or
                QRC_SBOX[7][sboxBit(lrgstate[5] and 0x3F)]
            )

        return (
            bitnumIntl(s, 15, 0) or bitnumIntl(s, 6, 1) or bitnumIntl(s, 19, 2) or bitnumIntl(s, 20, 3) or
                bitnumIntl(s, 28, 4) or bitnumIntl(s, 11, 5) or bitnumIntl(s, 27, 6) or bitnumIntl(s, 16, 7) or
                bitnumIntl(s, 0, 8) or bitnumIntl(s, 14, 9) or bitnumIntl(s, 22, 10) or bitnumIntl(s, 25, 11) or
                bitnumIntl(s, 4, 12) or bitnumIntl(s, 17, 13) or bitnumIntl(s, 30, 14) or bitnumIntl(s, 9, 15) or
                bitnumIntl(s, 1, 16) or bitnumIntl(s, 7, 17) or bitnumIntl(s, 23, 18) or bitnumIntl(s, 13, 19) or
                bitnumIntl(s, 31, 20) or bitnumIntl(s, 26, 21) or bitnumIntl(s, 2, 22) or bitnumIntl(s, 8, 23) or
                bitnumIntl(s, 18, 24) or bitnumIntl(s, 12, 25) or bitnumIntl(s, 29, 26) or bitnumIntl(s, 5, 27) or
                bitnumIntl(s, 21, 28) or bitnumIntl(s, 10, 29) or bitnumIntl(s, 3, 30) or bitnumIntl(s, 24, 31)
            )
    }

    private fun desCrypt(input: ByteArray, schedule: Array<ByteArray>, output: ByteArray) {
        val state = initialPermutation(input)
        var s0 = state[0]
        var s1 = state[1]
        for (i in 0 until 15) {
            val prev = s1
            s1 = desF(s1, schedule[i]) xor s0
            s0 = prev
        }
        s0 = desF(s1, schedule[15]) xor s0
        inversePermutation(s0, s1, output)
    }

    private fun keySchedule(key: ByteArray, mode: Int): Array<ByteArray> {
        val schedule = Array(16) { ByteArray(6) }
        var c = 0
        var d = 0
        for (i in 0 until 28) {
            c = c or bitnum(key, KEY_PERM_C[i], 31 - i)
            d = d or bitnum(key, KEY_PERM_D[i], 31 - i)
        }
        for (i in 0 until 16) {
            c = (((shl32(c, KEY_RND_SHIFT[i])) or (c ushr (28 - KEY_RND_SHIFT[i]))) and 0xFFFFFFF0.toInt())
            d = (((shl32(d, KEY_RND_SHIFT[i])) or (d ushr (28 - KEY_RND_SHIFT[i]))) and 0xFFFFFFF0.toInt())
            val togen = if (mode == DES_DECRYPT) 15 - i else i
            for (j in 0 until 24) {
                schedule[togen][j / 8] =
                    (schedule[togen][j / 8].toInt() or bitnumIntr(c, KEY_COMPRESSION[j], 7 - (j % 8))).toByte()
            }
            for (j in 24 until 48) {
                schedule[togen][j / 8] =
                    (schedule[togen][j / 8].toInt() or bitnumIntr(d, KEY_COMPRESSION[j] - 27, 7 - (j % 8))).toByte()
            }
        }
        return schedule
    }

    private fun tripledesKeySetup(key: ByteArray, mode: Int): Array<Array<ByteArray>> {
        val k0 = key.copyOfRange(0, 8)
        val k1 = key.copyOfRange(8, 16)
        val k2 = key.copyOfRange(16, 24)
        return if (mode == DES_ENCRYPT) {
            arrayOf(
                keySchedule(k0, DES_ENCRYPT),
                keySchedule(k1, DES_DECRYPT),
                keySchedule(k2, DES_ENCRYPT),
            )
        } else {
            arrayOf(
                keySchedule(k2, DES_DECRYPT),
                keySchedule(k1, DES_ENCRYPT),
                keySchedule(k0, DES_DECRYPT),
            )
        }
    }

    private fun tripledesCrypt(input: ByteArray, schedule: Array<Array<ByteArray>>, output: ByteArray) {
        val buf = ByteArray(8)
        desCrypt(input, schedule[0], buf)
        desCrypt(buf, schedule[1], output)
        desCrypt(output, schedule[2], buf)
        System.arraycopy(buf, 0, output, 0, 8)
    }

    /** QQ QRC: hex → 3DES-ECB decrypt → zlib inflate (Z_SYNC_FLUSH tolerant). */
    fun decodeQrc(hexData: String): String? {
        if (hexData.isEmpty() || hexData.length % 2 != 0) return null
        val encrypted = hexToBytes(hexData) ?: return null
        if (encrypted.isEmpty()) return null

        val schedule = tripledesKeySetup(QRC_KEY, DES_DECRYPT)
        val block = ByteArray(8)
        var i = 0
        while (i + 8 <= encrypted.size) {
            tripledesCrypt(encrypted.copyOfRange(i, i + 8), schedule, block)
            System.arraycopy(block, 0, encrypted, i, 8)
            i += 8
        }
        return runCatching { String(zlibInflateLenient(encrypted), Charsets.UTF_8) }.getOrNull()
    }

    private fun zlibInflateLenient(data: ByteArray): ByteArray {
        // Mirrors pako inflate({ finishFlush: Z_SYNC_FLUSH }): tolerate truncated tail.
        val out = ByteArrayOutputStream(data.size * 4)
        val buf = ByteArray(4096)
        try {
            val inflater = Inflater()
            inflater.setInput(data)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n > 0) out.write(buf, 0, n)
                else if (inflater.needsInput() || inflater.needsDictionary()) break
            }
            inflater.end()
        } catch (_: java.util.zip.DataFormatException) {
            // fall through: return what we got (Z_SYNC_FLUSH semantics)
        }
        return out.toByteArray()
    }

    private fun hexToBytes(hex: String): ByteArray? {
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    // ------------------------------------------------------------------
    // MRC (Migu): TEA-variant decrypt over 64-bit little-endian words.
    // ------------------------------------------------------------------

    private val MRC_DELTA = 2654435769L
    private val MRC_KEYS = longArrayOf(
        27303562373562475L, 18014862372307051L, 22799692160172081L, 34058940340699235L,
        30962724186095721L, 27303523720101991L, 27303523720101998L, 31244139033526382L,
        28992395054481524L,
    )

    /** Migu MRC: hex-ish text split into 16-char groups → int64 → TEA decrypt → UTF-16LE bytes. */
    fun decodeMrc(data: String): String? {
        if (data.length < 32) return data
        val n = data.length / 16
        if (n == 0) return null
        val words = LongArray(n) { i ->
            java.math.BigInteger(data.substring(i * 16, i * 16 + 16), 16).toLong()
        }
        teaDecrypt(words, MRC_KEYS)
        // longArrToString: each long → 8 bytes little-endian → decode as UTF-16LE
        val bytes = ByteArray(words.size * 8)
        for (i in words.indices) {
            var v = words[i]
            for (b in 0 until 8) {
                bytes[i * 8 + b] = (v and 0xFF).toByte()
                v = v ushr 8
            }
        }
        return String(bytes, Charsets.UTF_16LE)
    }

    private fun teaDecrypt(data: LongArray, key: LongArray) {
        val length = data.size.toLong()
        if (length >= 1) {
            var j2 = data[0]
            var j3 = signedToUnsigned(6L + (52L / length) * MRC_DELTA)
            while (true) {
                val j4 = j3
                if (j4 == 0L) break
                val j5 = 3L and unsignedShr(j4, 2)
                var j6 = length
                while (true) {
                    j6--
                    if (j6 > 0L) {
                        val j7 = data[(j6 - 1).toInt()]
                        val i = j6.toInt()
                        j2 = signedToUnsigned(
                            data[i] - signedToUnsigned(
                                signedToUnsigned(xor(j2, j4) + signedToUnsigned(xor(j7, key[((3L and j6).toInt()) xor j5.toInt()]))) xor
                                    signedToUnsigned(
                                        signedToUnsigned(xor(unsignedShr(j7, 5), shl(j2, 2))) +
                                            signedToUnsigned(xor(unsignedShr(j2, 3), shl(j7, 4)))
                                    )
                            )
                        )
                        data[i] = j2
                    } else break
                }
                val j8 = data[(length - 1).toInt()]
                j2 = signedToUnsigned(
                    data[0] - signedToUnsigned(
                        signedToUnsigned(xor(key[((j6 and 3L).toInt()) xor j5.toInt()], j8) + signedToUnsigned(xor(j2, j4))) xor
                            signedToUnsigned(
                                signedToUnsigned(xor(unsignedShr(j8, 5), shl(j2, 2))) +
                                    signedToUnsigned(xor(unsignedShr(j2, 3), shl(j8, 4)))
                            )
                    )
                )
                data[0] = j2
                j3 = signedToUnsigned(j4 - MRC_DELTA)
            }
        }
    }

    // 64-bit unsigned helpers on Long
    private fun signedToUnsigned(x: Long): Long = x // two's complement arithmetic already wraps
    private fun xor(a: Long, b: Long): Long = a xor b
    private fun shl(a: Long, n: Int): Long = a shl n
    private fun unsignedShr(a: Long, n: Int): Long = a ushr n

    // ------------------------------------------------------------------
    // Format converters → Enhanced LRC with ABSOLUTE word timestamps
    // ------------------------------------------------------------------

    private fun msTag(ms: Long): String {
        val frac = (ms % 1000).toString().padStart(3, '0')
        val totalSec = ms / 1000
        val m = (totalSec / 60).toString().padStart(2, '0')
        val s = (totalSec % 60).toString().padStart(2, '0')
        return "[$m:$s.$frac]"
    }

    /** Word tag for enhanced LRC: <mm:ss.mmm> — no square brackets inside. */
    private fun wordTag(ms: Long): String {
        val frac = (ms % 1000).toString().padStart(3, '0')
        val totalSec = ms / 1000
        val m = (totalSec / 60).toString().padStart(2, '0')
        val s = (totalSec % 60).toString().padStart(2, '0')
        return "<$m:$s.$frac>"
    }

    private data class WordToken(val startMs: Long, val text: String)

    /** Matches `[start,dur]`-headed lines; word tags are `<off,dur[,f]>` (relative) or `(abs,dur[,f])` (absolute). */
    private val KRC_LINE = Regex("^\\[(\\d+),(\\d+)](.*)$")
    private val KRC_WORD = Regex("<(\\d+),(\\d+)(?:,\\d+)?>([^<]*)")
    private val KRC_RESIDUE = Regex("<[^<>]*>")
    private val PAREN_TAG = Regex("\\((\\d+),(\\d+)(?:,\\d+)?\\)")
    private val BRACKET_ONLY = Regex("^[()\\[\\]{}]+$")

    private fun trailingSpaceOf(raw: String): String =
        if (raw.lastOrNull()?.isWhitespace() == true) " " else ""

    private fun appendSpaceToLast(words: MutableList<WordToken>) {
        val last = words.lastOrNull() ?: return
        if (last.text.isNotEmpty() && !last.text.last().isWhitespace()) {
            words[words.lastIndex] = last.copy(text = last.text + " ")
        }
    }

    /**
     * KRC text: `[start,dur]<off,dur,0>word…`, word offsets relative to line start.
     * Also handles the `[id:$xxx]` / `[offset:N]` / `[language:base64]` headers
     * (language JSON carries per-line translation/romanization arrays — returned separately).
     */
    fun convertKrc(krcText: String): KrcConversion? {
        val text = krcText.replace("\r", "")
        var body = text
        val idHeader = Regex("^.*?\\[id:\\$\\w+]\n").find(body)
        if (idHeader != null) body = body.removeRange(idHeader.range)

        var translationPerLine: List<String>? = null
        var romanizationPerLine: List<String>? = null
        val langMatch = Regex("\\[language:([\\w=\\\\/+]+)]").find(body)
        if (langMatch != null) {
            body = body.replace(langMatch.value + "\n", "").replace(langMatch.value, "")
            runCatching {
                val json = String(B64_DECODER.decode(langMatch.groupValues[1]), Charsets.UTF_8)
                val root = com.google.gson.JsonParser.parseString(json).asJsonObject
                val content = root.getAsJsonArray("content") ?: return@runCatching
                for (el in content) {
                    val obj = el.asJsonObject
                    when (obj.get("type")?.asInt) {
                        0 -> romanizationPerLine = obj.getAsJsonArray("lyricContent")
                            ?.map { line -> line.asJsonArray.joinToString("") { it.asString } }
                        1 -> translationPerLine = obj.getAsJsonArray("lyricContent")
                            ?.map { line -> line.asJsonArray.joinToString("") { it.asString } }
                    }
                }
            }
        }

        val lrcLines = mutableListOf<String>()
        val wordLines = mutableListOf<String>()
        val lineStartsMs = mutableListOf<Long>() // content lines only, aligned with language arrays
        var hasWords = false
        for (raw in body.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("[offset") || line.startsWith("[ti:") || line.startsWith("[ar:") ||
                line.startsWith("[al:") || line.startsWith("[by:") || line.startsWith("[hash:")
            ) {
                lrcLines.add(line)
                wordLines.add(line)
                continue
            }
            val m = KRC_LINE.find(line) ?: continue
            val startMs = m.groupValues[1].toLongOrNull() ?: continue
            val rest = m.groupValues[3]
            // plain line: strip word tags
            val plain = KRC_WORD.replace(rest) { it.groupValues[3] }
                .let { KRC_RESIDUE.replace(it, "") }
                .replace("$", "")
                .trim()
            lrcLines.add("${msTag(startMs)}$plain")
            lineStartsMs.add(startMs)
            // word line: absolute timestamps in <mm:ss.mmm> tags
            val words = mutableListOf<WordToken>()
            val firstMatch = KRC_WORD.find(rest)
            if (firstMatch != null) {
                val lead = rest.substring(0, firstMatch.range.first).trim()
                if (lead.isNotEmpty()) words.add(WordToken(startMs, lead))
            }
            KRC_WORD.findAll(rest).forEach { wm ->
                val off = wm.groupValues[1].toLongOrNull() ?: 0L
                val raw = wm.groupValues[3].let { KRC_RESIDUE.replace(it, "") }.replace("$", "")
                val w = raw.trim()
                if (w.isNotEmpty()) {
                    words.add(WordToken(startMs + off, w + trailingSpaceOf(raw)))
                } else if (raw.isNotEmpty()) {
                    appendSpaceToLast(words)
                }
            }
            if (words.isNotEmpty()) {
                hasWords = true
                wordLines.add(msTag(startMs) + words.joinToString("") { "${wordTag(it.startMs)}${it.text}" })
            }
        }
        if (lrcLines.isEmpty()) return null
        return KrcConversion(
            lrc = lrcLines.joinToString("\n"),
            wordLrc = if (hasWords) wordLines.joinToString("\n") else null,
            translationLrc = translationPerLine?.let { tagPerLine(it, lineStartsMs) },
            romanizationLrc = romanizationPerLine?.let { tagPerLine(it, lineStartsMs) },
        )
    }

    /** Pairs per-line texts (from the KRC language header) with the main lines' start times. */
    private fun tagPerLine(texts: List<String>, lineStartsMs: List<Long>): String? {
        val out = texts.indices.mapNotNull { i ->
            val t = texts[i].trim()
            if (t.isEmpty()) return@mapNotNull null
            val ms = lineStartsMs.getOrNull(i) ?: return@mapNotNull null
            "${msTag(ms)}$t"
        }
        return out.ifEmpty { null }?.joinToString("\n")
    }

    data class KrcConversion(
        val lrc: String,
        val wordLrc: String?,
        val translationLrc: String?,
        val romanizationLrc: String? = null,
    )

    /**
     * YRC text (Netease) has two server shapes:
     *  - newer songs: JSON lines `{"t":ms,"c":[{"tx":"word","t":absMs},…]}`
     *  - legacy songs: tuple lines `[absStartMs,lineDurMs](absWordMs,wordDurMs,flag)word…`
     * Both normalize to Enhanced LRC with absolute <mm:ss.mmm> word tags.
     */
    fun convertYrc(ylrcText: String): String? {
        val hasJsonLines = ylrcText.lineSequence().any { it.trim().startsWith("{") }
        if (!hasJsonLines) {
            // Legacy tuple YRC — same shape as QRC (absolute paren tags).
            return convertTupleText(ylrcText)
        }
        val out = mutableListOf<String>()
        for (raw in ylrcText.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("[")) {
                out.add(line)
                continue
            }
            if (!line.startsWith("{")) continue
            val parsed = runCatching {
                com.google.gson.JsonParser.parseString(line).asJsonObject
            }.getOrNull() ?: continue
            val t = parsed.get("t")?.takeIf { it.isJsonPrimitive }?.asLong ?: continue
            val words = parsed.getAsJsonArray("c") ?: continue
            val sb = StringBuilder(msTag(t))
            val plain = StringBuilder()
            for (w in words) {
                val obj = w.asJsonObject
                val tx = obj.get("tx")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                val wt = obj.get("t")?.takeIf { it.isJsonPrimitive }?.asLong ?: t
                if (tx.isNotEmpty()) {
                    sb.append(wordTag(wt)).append(tx)
                    plain.append(tx)
                }
            }
            if (plain.isBlank()) continue
            out.add(sb.toString())
        }
        return if (out.isEmpty()) null else out.joinToString("\n")
    }

    /**
     * QRC text (QQ, after [decodeQrc]): `[start,dur]` headed lines. Word tags are
     * `(absStartMs,dur[,flag])` in the real server format, or `<offMs,dur[,flag]>`
     * (KRC-style relative) — both normalize to absolute [wordTag]s.
     * The XML wrapper (`LyricContent="…"`) is stripped first.
     */
    fun convertQrc(qrcText: String): String? = convertTupleText(extractQrcLyricContent(qrcText))

    /**
     * Strips the `LyricContent="…"` XML envelope QQ wraps around decoded QRC text.
     * Returns the text unchanged when no envelope is present (plain LRC trans/roma).
     */
    fun extractQrcLyricContent(qrcText: String): String {
        var text = qrcText.trim()
        if (text.contains("LyricContent=\"")) {
            text = Regex("^[\\s\\S]*?LyricContent=\"").replace(text, "")
            text = Regex("\"/>[\\s\\S]*$").replace(text, "")
        }
        return text
    }

    /**
     * NetEase lyric fields (`lrc`/`tlyric`/`romalrc`) can arrive as JSON lines
     * (`{"t":ms,"c":[{"tx":"word","t":ms}…]}`) instead of `[mm:ss.xxx]` LRC —
     * often mixed with regular LRC lines in one payload. Converts JSON lines to
     * LRC text (word tags when the payload carries them, line start otherwise);
     * returns the original text when no JSON lines are present.
     */
    fun normalizeNeteaseLrcField(raw: String?): String? {
        if (raw.isNullOrBlank()) return raw
        if (raw.lineSequence().none { it.trim().startsWith("{") }) return raw
        return convertYrc(raw) ?: raw
    }

    /**
     * Shared tuple-line parser for QRC and legacy-YRC: `[startMs,durMs]` headed lines
     * whose words carry ABSOLUTE `(absMs,dur[,flag])` tags (or relative `<off,dur[,0]>`
     * KRC tags). Voice markers like `v1:` at the line head survive as bracket
     * text so the LRC parser can still assign a duet voice.
     */
    private fun convertTupleText(sourceText: String): String? {
        val text = sourceText.replace("\r", "")
        val out = mutableListOf<String>()
        var sawTupleLine = false
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("[offset") || line.startsWith("[ti:") || line.startsWith("[ar:") ||
                line.startsWith("[al:") || line.startsWith("[by:") || line.startsWith("[hash:") ||
                line.startsWith("[language:") || line.startsWith("[kuwo:") || line.startsWith("[ver:")
            ) {
                out.add(line)
                continue
            }
            val m = KRC_LINE.find(line) ?: continue
            val startMs = m.groupValues[1].toLongOrNull() ?: continue
            val rest = m.groupValues[3]
            sawTupleLine = true
            val leadMatch = Regex("^\\s*(?:\\(|\\[|\\{)(?![\\d,]+(?:\\)|\\]|\\}))([^()\\[\\]{}]{1,8})(?:\\)|\\]|\\})").find(rest)
                ?: Regex("^\\s*v(\\d{1,4})\\s*:").find(rest)
            val voiceLead = leadMatch?.value?.trim()
            val body = if (leadMatch != null) rest.substring(leadMatch.range.last + 1) else rest

            val angleMatches = KRC_WORD.findAll(body).toList()
            val words = mutableListOf<WordToken>()

            if (angleMatches.isNotEmpty()) {
                if (angleMatches.first().range.first > 0) {
                    val lead = body.substring(0, angleMatches.first().range.first).trim()
                    if (lead.isNotEmpty()) words.add(WordToken(startMs, lead))
                }
                angleMatches.forEach { wm ->
                    val raw = wm.groupValues[3]
                    val w = raw.trim()
                    if (w.isNotEmpty()) {
                        words.add(WordToken(startMs + (wm.groupValues[1].toLongOrNull() ?: 0L), w + trailingSpaceOf(raw)))
                    } else if (raw.isNotEmpty()) {
                        appendSpaceToLast(words)
                    }
                }
            } else if (PAREN_TAG.containsMatchIn(body)) {
                val tagMatches = PAREN_TAG.findAll(body).toList()
                val trailing = body.substring(tagMatches.last().range.last + 1).trim()
                if (trailing.isNotEmpty() && !BRACKET_ONLY.matches(trailing)) {
                    if (tagMatches.first().range.first > 0) {
                        val lead = body.substring(0, tagMatches.first().range.first).trim()
                        if (lead.isNotEmpty() && !BRACKET_ONLY.matches(lead)) words.add(WordToken(startMs, lead))
                    }
                    for (i in tagMatches.indices) {
                        val tm = tagMatches[i]
                        val tagAbsMs = tm.groupValues[1].toLongOrNull() ?: startMs
                        val nextStart = if (i + 1 < tagMatches.size) tagMatches[i + 1].range.first else body.length
                        if (tm.range.last + 1 < nextStart) {
                            val raw = body.substring(tm.range.last + 1, nextStart)
                            val w = raw.trim()
                            if (w.isNotEmpty() && !BRACKET_ONLY.matches(w)) {
                                words.add(WordToken(tagAbsMs, w + trailingSpaceOf(raw)))
                            } else if (w.isEmpty() && raw.isNotEmpty()) {
                                appendSpaceToLast(words)
                            }
                        }
                    }
                } else {
                    var prevEnd = 0
                    for (tm in tagMatches) {
                        val tagAbsMs = tm.groupValues[1].toLongOrNull() ?: startMs
                        if (tm.range.first > prevEnd) {
                            val raw = body.substring(prevEnd, tm.range.first)
                            val between = raw.trim()
                            if (between.isNotEmpty() && !BRACKET_ONLY.matches(between)) {
                                words.add(WordToken(tagAbsMs, between + trailingSpaceOf(raw)))
                            } else if (between.isEmpty() && raw.isNotEmpty()) {
                                appendSpaceToLast(words)
                            }
                        }
                        prevEnd = tm.range.last + 1
                    }
                    if (prevEnd < body.length) {
                        val tail = body.substring(prevEnd).trim()
                        if (tail.isNotEmpty() && !BRACKET_ONLY.matches(tail)) {
                            words.add(WordToken(startMs, tail))
                        }
                    }
                }
            }

            val plain = KRC_WORD.replace(body) { it.groupValues[3] }
                .let { PAREN_TAG.replace(it, "") }
                .let { KRC_RESIDUE.replace(it, "") }
                .replace("$", "")
                .trim()
            if (plain.isEmpty() && words.isEmpty()) continue
            val sb = StringBuilder(msTag(startMs))
            if (voiceLead != null && voiceLead.isNotEmpty()) sb.append(voiceLead)
            if (words.isNotEmpty()) {
                words.forEach { sb.append(wordTag(it.startMs)).append(it.text) }
            } else {
                sb.append(plain)
            }
            out.add(sb.toString())
        }
        if (out.isEmpty() || !sawTupleLine) return null
        return out.joinToString("\n")
    }

    /**
     * Kuwo LRCx: `[mm:ss.xx]` lines with `<a,b>` word tags where the pair encodes
     * the word's start/duration RELATIVE TO THE LINE START, scaled by the
     * `[kuwo:octal]` header: start = (a+b)/(2*off1), end = (a-b)/(2*off2)+start.
     * Produces Enhanced LRC with absolute word timestamps (line start added) and,
     * when a scaled value overruns the next word's start, clamps like
     * lrcTools.getWordInfo.
     */
    fun convertKuwoLrcx(lrcxText: String): String? {
        val text = lrcxText.replace("\r", "")
        var offset1 = 1L
        var offset2 = 1L
        val tags = mutableListOf<String>()
        val rawLines = mutableListOf<String>()

        val kuwoTag = Regex("^\\[kuwo:\\s*(\\S+)]")
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val m = kuwoTag.find(line)
            if (m != null) {
                var content = m.groupValues[1]
                val bracket = content.indexOf("][")
                if (bracket >= 0) content = content.substring(0, bracket)
                val value = content.toIntOrNull(8)
                if (value != null && value != 0) {
                    offset1 = (value / 10).toLong()
                    offset2 = (value % 10).toLong()
                    if (offset1 == 0L || offset2 == 0L) return null
                }
                continue
            }
            if (Regex("\\[(ver|ti|ar|al|offset|by):.*]").containsMatchIn(line)) {
                tags.add(line)
                continue
            }
            rawLines.add(line)
        }

        val wordTimeAll = Regex("<(-?\\d+),(-?\\d+)(?:,-?\\d+)?>")
        val lineHeader = Regex("^(\\[\\d{1,2}:\\d{1,2}(?:[.:]\\d{1,4})?])")

        data class KwWord(var startMs: Long, var endMs: Long, val text: String)

        val out = mutableListOf<String>()
        for (line in rawLines) {
            val hm = lineHeader.find(line) ?: continue
            val timeTag = hm.groupValues[1]
            val lineStartMs = timeToMs(timeTag.removeSurrounding("[", "]"))
            val body = line.removeRange(hm.range).trim()
            if (body.isEmpty()) continue
            if (!wordTimeAll.containsMatchIn(body)) {
                out.add(timeTag + body)
                continue
            }
            val pieces = body.split(Regex("(?=<-?\\d+,-?\\d+(?:,-?\\d+)?>)")).filter { it.isNotEmpty() }
            val kwWords = mutableListOf<KwWord>()
            var leadingText: String? = null
            for (piece in pieces) {
                val tm = Regex("^<(-?\\d+),(-?\\d+)(?:,-?\\d+)?>").find(piece)
                if (tm == null) {
                    // text before the first word tag belongs to the line start
                    if (kwWords.isEmpty() && piece.isNotBlank()) leadingText = piece
                    continue
                }
                val a = tm.groupValues[1].toLongOrNull() ?: continue
                val b = tm.groupValues[2].toLongOrNull() ?: continue
                val w = piece.removeRange(tm.range)
                // lrcTools.getWordInfo: the pair encodes the word's start/duration
                // relative to the line start; lineStartMs makes it absolute.
                var start = lineStartMs + abs((a + b) / (offset1 * 2))
                val end = lineStartMs + abs((a - b) / (offset2 * 2)) + (start - lineStartMs)
                kwWords.lastOrNull()?.let { prev ->
                    if (start < prev.endMs) {
                        prev.endMs = start
                        if (prev.startMs > prev.endMs) prev.startMs = prev.endMs
                    }
                }
                kwWords.add(KwWord(start, end, w))
            }
            if (kwWords.isEmpty()) {
                out.add(timeTag + body.replace(wordTimeAll, ""))
                continue
            }
            val sb = StringBuilder(timeTag)
            leadingText?.let { sb.append(it.trimStart()) }
            kwWords.forEach { sb.append(wordTag(it.startMs)).append(it.text) }
            out.add(sb.toString())
        }
        if (out.isEmpty()) return null
        return (if (tags.isEmpty()) out else tags + out).joinToString("\n")
    }

    data class KuwoLyricSplit(val wordLrc: String, val plainLrc: String, val translationLrc: String)

    fun splitKuwoTranslationLines(lrc: String): KuwoLyricSplit {
        val lineHeader = Regex("^(\\[\\d{1,2}:\\d{1,2}(?:[.:]\\d{1,4})?])")
        val wordTag = Regex("<\\d{1,3}:\\d{1,2}[.:]\\d{2,3}>")

        data class KwLine(
            val startMs: Long,
            val raw: String,
            val text: String,
            val allTagsAtStart: Boolean,
            val tagCount: Int,
        )

        val headers = mutableListOf<String>()
        val parsed = mutableListOf<KwLine>()
        for (raw in lrc.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val hm = lineHeader.find(line) ?: run {
                headers.add(line)
                continue
            }
            val startMs = timeToMs(hm.groupValues[1].removeSurrounding("[", "]"))
            val body = line.removeRange(hm.range)
            val tags = wordTag.findAll(body)
                .map { timeToMs(it.value.removeSurrounding("<", ">")) }
                .toList()
            parsed.add(
                KwLine(
                    startMs = startMs,
                    raw = line,
                    text = body.replace(wordTag, "").trim(),
                    allTagsAtStart = tags.isNotEmpty() && tags.all { it == startMs },
                    tagCount = tags.size,
                )
            )
        }

        val nonEmptyCount = parsed.count { it.text.isNotEmpty() }
        val mains = mutableListOf<KwLine>()
        val transByMainIndex = LinkedHashMap<Int, MutableList<KwLine>>()
        for (p in parsed) {
            if (p.text.isEmpty()) continue
            val isTranslation = p.allTagsAtStart && p.tagCount >= 2
            if (isTranslation) {
                if (mains.isEmpty()) continue
                transByMainIndex.getOrPut(mains.size - 1) { mutableListOf() }.add(p)
            } else {
                mains.add(p)
            }
        }

        if (mains.isEmpty() || mains.size * 4 < nonEmptyCount) {
            return KuwoLyricSplit(lrc, stripWordTags(lrc), "")
        }

        val wordLrc = (headers + mains.map { it.raw }).joinToString("\n")
        val translation = StringBuilder()
        for ((index, lines) in transByMainIndex) {
            val owner = mains[index]
            val text = lines.map { it.text }.filter { it.isNotBlank() }.joinToString(" ")
            if (text.isNotEmpty()) translation.appendLine(msTag(owner.startMs) + text)
        }
        return KuwoLyricSplit(wordLrc, stripWordTags(wordLrc), translation.trim().toString())
    }

    // ------------------------------------------------------------------
    // Translation/romanization re-tagging helpers
    // ------------------------------------------------------------------

    private val LRC_TIME = Regex("\\[([\\d:.]+)]")

    private fun timeToMs(time: String): Long {
        var t = time
        if (!t.contains('.')) t += ".0"
        val arr = t.split(Regex(":|\\."))
        val ms = (arr.last().toLongOrNull() ?: 0L) * when (arr.last().length) {
            1 -> 100L
            2 -> 10L
            else -> 1L
        }
        val hms = arr.dropLast(1)
        val s = hms.lastOrNull()?.toLongOrNull() ?: 0L
        val m = if (hms.size >= 2) hms[hms.size - 2].toLongOrNull() ?: 0L else 0L
        val h = if (hms.size >= 3) hms[hms.size - 3].toLongOrNull() ?: 0L else 0L
        return h * 3600000 + m * 60000 + s * 1000 + ms
    }

    /**
     * lx-music `fixTimeTag`: rewrites the target lrc's line timestamps so each line
     * uses the matching (±100ms) timestamp from the source lrc. Used to align
     * ytlrc/rlrc against the yrc-derived word lines.
     */
    fun fixTimeTag(sourceLrc: String, targetLrc: String): String {
        val sourceLines = sourceLrc.lines().toMutableList()
        val result = mutableListOf<String>()
        val temp = mutableListOf<String>()
        for (line in targetLrc.lines()) {
            val tm = LRC_TIME.find(line) ?: continue
            val words = line.removeRange(tm.range).trim()
            if (words.isBlank()) continue
            val t1 = timeToMs(tm.groupValues[1])
            while (sourceLines.isNotEmpty()) {
                val src = sourceLines.removeAt(0)
                val sm = LRC_TIME.find(src) ?: continue
                val t2 = timeToMs(sm.groupValues[1])
                if (abs(t1 - t2) < 100) {
                    val newLine = line.replaceRange(tm.range, sm.value).trim()
                    result.add(newLine)
                    break
                }
                temp.add(src)
            }
            sourceLines.addAll(0, temp)
            temp.clear()
        }
        return result.joinToString("\n")
    }

    /** Removes <mm:ss.mmm> word tags, leaving plain line-synced text. */
    fun stripWordTags(lrc: String): String =
        lrc.replace(Regex("<\\d{1,3}:\\d{1,2}[.:]\\d{2,3}>"), "")

    /** Rewrites `[mm:ss]`-style tags with 2-digit fractions to `[mm:ss.xx]` (wy fixTimeLabel). */
    fun fixColonFraction(lrc: String): String =
        lrc.replace(Regex("\\[(\\d{2}:\\d{2}):(\\d{2})]"), "[$1.$2]")
}
