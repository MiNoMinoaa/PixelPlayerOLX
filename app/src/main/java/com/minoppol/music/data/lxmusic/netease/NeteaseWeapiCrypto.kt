package com.minoppol.music.data.lxmusic.netease

import android.util.Base64
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object NeteaseWeapiCrypto {

    // eapi：AES/ECB，输出大写 hex
    fun eapiEncrypt(url: String, payloadJson: String): String {
        val message = "nobody${url}use${payloadJson}md5forencrypt"
        val digest = md5(message)
        val data = "${url}-36cd479b6b5-${payloadJson}-36cd479b6b5-${digest}"
        val cipher = Cipher.getInstance("AES")
        val keySpec = SecretKeySpec(EAPI_KEY.toByteArray(Charsets.UTF_8), "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        return encrypted.joinToString("") { "%02X".format(it) }
    }

    private fun md5(input: String): String {
        val bytes = java.security.MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private const val EAPI_KEY = "e82ckenh8dichen8"
    private const val PRESET_KEY = "0CoJUm6Qyw8W8jud"
    private const val IV = "0102030405060708"

    private const val PUBLIC_KEY_B64 =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB"

    private val secureRandom = SecureRandom()

    // weapi：随机 key 走两次 AES/CBC，再用 RSA 把 key 包一层
    fun encrypt(payloadJson: String): Pair<String, String> {
        val secretKey = generateSecretKey()
        val first = aesCbcEncryptBase64(payloadJson.toByteArray(Charsets.UTF_8), PRESET_KEY, IV)
        val params = aesCbcEncryptBase64(first.toByteArray(Charsets.UTF_8), secretKey, IV)
        val encSecKey = rsaEncryptNoPaddingHex(secretKey.reversed().toByteArray(Charsets.UTF_8))
        return params to encSecKey
    }

    private fun generateSecretKey(): String {
        val digits = "0123456789"
        return (1..16).map { digits[secureRandom.nextInt(digits.length)] }.joinToString("")
    }

    private fun aesCbcEncryptBase64(data: ByteArray, key: String, iv: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
        val ivSpec = IvParameterSpec(iv.toByteArray(Charsets.UTF_8))
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(data)
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun rsaEncryptNoPaddingHex(data: ByteArray): String {
        val keyFactory = KeyFactory.getInstance("RSA")
        val keySpec = X509EncodedKeySpec(Base64.decode(PUBLIC_KEY_B64, Base64.NO_WRAP))
        val publicKey = keyFactory.generatePublic(keySpec)

        val padded = ByteArray(128 - data.size) { 0 } + data

        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encrypted = cipher.doFinal(padded)

        return encrypted.joinToString("") { "%02x".format(it) }
    }
}
