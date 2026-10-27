package com.minoppol.music.data.lxmusic

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DeflaterOutputStream
import kotlin.math.abs

/**
 * Golden vectors for the QRC path were produced by running the ENCRYPT direction
 * of lx-music's non-standard 3DES (qrcDecode.js tables, fixed key, zlib
 * Z_SYNC_FLUSH) in node, then verified to round-trip through the original
 * decrypt implementation (qrc_gen/verify.mjs, 4/4 OK).
 */
class LxLyricDecodersTest {

    // ------------------------------------------------------------------
    // QRC golden vectors (see qrc_gen/vectors.json)
    // ------------------------------------------------------------------

    private val qrcVector0Plain =
        "[offset:0]\n[1000,2000]<0,300,0>你<300,300,0>好<600,800,0>世界\n[4000,1000]<0,500,0>再<500,500,0>见\n"
    private val qrcVector0Hex =
        "318967b79244609087efcf9e46cbcc2071badc40933dccbdea5adf6ce7de393829db1e5be31b926cc7e0531cb5ae8bd550aa509b414d23f6b702a758fca5a9389c5835ea53cf1e50eab9ff628a0850e55cf5cef7e099f20b"
    private val qrcVector1Plain =
        "[offset:0]\n[500,800]<0,200,0>ラ<200,300,0>イ<500,300,0>ト\n"
    private val qrcVector1Hex =
        "318967b79244609060db526b3c32cbbc0f1e5c3dad42164da24cdcafe51a9810efa4e731a23ba5055d1f6a7360ea98363f9bf460e5994bca"

    @Test
    fun `decodeQrc golden vector 0 round-trips`() {
        val decoded = LxLyricDecoders.decodeQrc(qrcVector0Hex)
        assertThat(decoded).isEqualTo(qrcVector0Plain)
    }

    @Test
    fun `decodeQrc golden vector 1 round-trips`() {
        val decoded = LxLyricDecoders.decodeQrc(qrcVector1Hex)
        assertThat(decoded).isEqualTo(qrcVector1Plain)
    }

    @Test
    fun `decodeQrc rejects odd-length and empty input`() {
        assertThat(LxLyricDecoders.decodeQrc("")).isNull()
        assertThat(LxLyricDecoders.decodeQrc("abc")).isNull()
    }

    // ------------------------------------------------------------------
    // QRC → Enhanced LRC conversion
    // ------------------------------------------------------------------

    @Test
    fun `convertQrc produces absolute-timestamp enhanced lrc`() {
        val converted = LxLyricDecoders.convertQrc(qrcVector0Plain)!!
        // line starts at 1000ms, words at 1000/1300/1600; second line 4000/4500
        assertThat(converted).contains("[00:01.000]<00:01.000>你<00:01.300>好<00:01.600>世界")
        assertThat(converted).contains("[00:04.000]<00:04.000>再<00:04.500>见")
    }

    @Test
    fun `convertQrc strips xml wrapper`() {
        val wrapped = "<?xml version=\"1.0\" encoding=\"utf-8\"?><QrcInfos><QrcHeadInfo Title=\"x\"/><LyricInfo LyricCount=\"1\" LyricType=\"1\">" +
            "<Lyric_1 LyricType=\"1\" LyricContent=\"[1000,2000]<0,500,0>测<500,500,0>试\"/></LyricInfo></QrcInfos>"
        val converted = LxLyricDecoders.convertQrc(wrapped)!!
        assertThat(converted).contains("[00:01.000]<00:01.000>测<00:01.500>试")
        assertThat(converted).doesNotContain("xml")
    }

    // ------------------------------------------------------------------
    // YRC → Enhanced LRC conversion
    // ------------------------------------------------------------------

    @Test
    fun `convertYrc converts json lines to absolute word timestamps`() {
        val yrc = listOf(
            """{"t":1200,"c":[{"tx":"你","t":1200},{"tx":"好","t":1500},{"tx":"世界","t":1800}]}""",
            """{"t":5000,"c":[{"tx":"再","t":5000},{"tx":"见","t":5500}]}""",
        ).joinToString("\n")
        val converted = LxLyricDecoders.convertYrc(yrc)!!
        assertThat(converted).contains("[00:01.200]<00:01.200>你<00:01.500>好<00:01.800>世界")
        assertThat(converted).contains("[00:05.000]<00:05.000>再<00:05.500>见")
    }

    @Test
    fun `convertYrc keeps bracket header lines`() {
        val yrc = "[offset:0]\n" + """{"t":100,"c":[{"tx":"a","t":100}]}"""
        val converted = LxLyricDecoders.convertYrc(yrc)!!
        assertThat(converted).startsWith("[offset:0]")
        assertThat(converted).contains("[00:00.100]<00:00.100>a")
    }

    @Test
    fun `convertYrc returns null for garbage`() {
        assertThat(LxLyricDecoders.convertYrc("not json at all")).isNull()
    }

    @Test
    fun `convertYrc handles legacy tuple lines without leaking ms tags into text`() {
        // Regression: 网易云旧式 YRC 是 [absStartMs,lineDurMs](absWordMs,wordDurMs,flag)词
        val legacy = "[71060,170](71060,170,0)啊\n[71230,180](71230,180,0)哦\n"
        val converted = LxLyricDecoders.convertYrc(legacy)!!
        assertThat(converted).contains("[01:11.060]<01:11.060>啊")
        assertThat(converted).contains("[01:11.230]<01:11.230>哦")
        assertThat(converted).doesNotContain("(71060")
        assertThat(converted).doesNotContain("71060,170,0")
    }

    @Test
    fun `convertYrc keeps voice marker from legacy tuple lines`() {
        val legacy = "[71060,170](男)(71060,170,0)啊(71230,100,0)耶\n"
        val converted = LxLyricDecoders.convertYrc(legacy)!!
        assertThat(converted).startsWith("[01:11.060](男)")
        assertThat(converted).contains("<01:11.060>啊<01:11.230>耶")
        assertThat(converted).doesNotContain(",170,0)")
    }

    // ------------------------------------------------------------------
    // KRC decode + conversion
    // ------------------------------------------------------------------

    /** Builds a KRC payload: zlib-compress → XOR key → prepend 4 junk bytes → base64. */
    private fun buildKrcPayload(krcText: String): String {
        val compressed = ByteArrayOutputStream().also { out ->
            DeflaterOutputStream(out).use { it.write(krcText.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        val key = byteArrayOf(
            0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47,
            0x51, 0x36, 0x31, 0x2d, 0xce.toByte(), 0xd2.toByte(), 0x6e, 0x69,
        )
        val body = ByteArray(compressed.size)
        for (i in compressed.indices) body[i] = (compressed[i].toInt() xor key[i % 16].toInt()).toByte()
        val full = ByteArray(4 + body.size)
        full[0] = 0x40; full[1] = 0x41; full[2] = 0x42; full[3] = 0x43
        System.arraycopy(body, 0, full, 4, body.size)
        return Base64.getEncoder().encodeToString(full)
    }

    @Test
    fun `decodeKrc and convertKrc round-trip`() {
        val krc = "[id:\$krc]\n[1000,2000]<0,300,0>你<300,300,0>好<600,800,0>世界\n[4000,1000]<0,500,0>再<500,500,0>见\n"
        val payload = buildKrcPayload(krc)
        val decoded = LxLyricDecoders.decodeKrc(payload)!!
        assertThat(decoded).contains("[1000,2000]")
        val conversion = LxLyricDecoders.convertKrc(decoded)!!
        assertThat(conversion.lrc).contains("[00:01.000]你好世界")
        assertThat(conversion.lrc).contains("[00:04.000]再见")
        assertThat(conversion.wordLrc).contains("[00:01.000]<00:01.000>你<00:01.300>好<00:01.600>世界")
        assertThat(conversion.wordLrc).contains("[00:04.000]<00:04.000>再<00:04.500>见")
    }

    @Test
    fun `convertKrc parses language header for translation`() {
        val langJson = """{"content":[{"type":1,"lyricContent":[["你好世界"],["再见"]]}],"type":1}"""
        val langB64 = Base64.getEncoder().encodeToString(langJson.toByteArray(Charsets.UTF_8))
        val krc = "[language:$langB64]\n[1000,2000]<0,300,0>你好世界\n[4000,1000]<0,500,0>再见\n"
        val payload = buildKrcPayload(krc)
        val conversion = LxLyricDecoders.convertKrc(LxLyricDecoders.decodeKrc(payload)!!)!!
        assertThat(conversion.translationLrc).isNotNull()
        assertThat(conversion.translationLrc!!).contains("[00:01.000]你好世界")
        assertThat(conversion.translationLrc!!).contains("[00:04.000]再见")
    }

    // ------------------------------------------------------------------
    // Kuwo LRCx decode + conversion
    // ------------------------------------------------------------------

    /** Builds a kuwo mobi.s response: headers + CRLFCRLF + zlib(base64(xor(lrcx)))  */
    private fun buildKuwoResponse(lrcxText: String): ByteArray {
        val xored = ByteArray(lrcxText.toByteArray(Charsets.UTF_8).size)
        val bytes = lrcxText.toByteArray(Charsets.UTF_8)
        val key = "yeelion".toByteArray(Charsets.UTF_8)
        var i = 0
        while (i < bytes.size) {
            var j = 0
            while (j < key.size && i < bytes.size) {
                xored[i] = (bytes[i].toInt() xor key[j].toInt()).toByte()
                i++; j++
            }
        }
        val b64 = Base64.getEncoder().encodeToString(xored).toByteArray(Charsets.UTF_8)
        val compressed = ByteArrayOutputStream().also { out ->
            DeflaterOutputStream(out).use { it.write(b64) }
        }.toByteArray()
        val header = "tp=content\r\nCS=qwertyuiop\r\n\r\n".toByteArray(Charsets.UTF_8)
        return header + compressed
    }

    @Test
    fun `decodeKuwoLrcx and convertKuwoLrcx round-trip`() {
        // [kuwo:17] → octal 17 = decimal 15 → offset1=1, offset2=5 (non-zero pair)
        val lrcx = "[kuwo:17]\n[ti:test]\n[00:01.00]你好<10,4>世界\n"
        val response = buildKuwoResponse(lrcx)
        val decoded = LxLyricDecoders.decodeKuwoLrcx(response, isLrcx = true)!!
        assertThat(decoded).contains("[kuwo:17]")
        val converted = LxLyricDecoders.convertKuwoLrcx(decoded)!!
        // start=(10+4)/2=7ms, end=|10-4|/10+7=7ms → word at <00:00.007>
        assertThat(converted).contains("[ti:test]")
        assertThat(converted).contains("[00:01.00]你好<00:00.007>世界")
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    @Test
    fun `fixTimeTag aligns translation timestamps to source`() {
        val source = "[00:01.000]你好\n[00:04.000]再见"
        val target = "[00:01.050]hello\n[00:04.020]bye"
        val fixed = LxLyricDecoders.fixTimeTag(source, target)
        assertThat(fixed).isEqualTo("[00:01.000]hello\n[00:04.000]bye")
    }

    @Test
    fun `fixColonFraction rewrites colon fractions`() {
        assertThat(LxLyricDecoders.fixColonFraction("[00:12:34]词"))
            .isEqualTo("[00:12.34]词")
    }
}
