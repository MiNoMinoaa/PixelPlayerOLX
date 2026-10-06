package com.minoppol.music.data.lxmusic.netease

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NeteaseCookieStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "NeteaseCookieStore"
        private const val PREFS_NAME = "netease_prefs"
        private const val KEY_COOKIE = "wy_cookie"
        private const val KEY_VIP_TYPE = "wy_vip_type"
        private const val KEY_ACCOUNT_ID = "wy_account_id"
        private const val KEY_VIP_UPDATED_AT = "wy_vip_updated_at"
        private const val KEY_PURCHASED_ALBUMS = "wy_purchased_album_ids"
        private const val KEY_COOKIE_VALID = "wy_cookie_valid"
        private const val KEY_NICKNAME = "wy_nickname"
        private const val KEY_AVATAR_URL = "wy_avatar_url"
        const val VIP_UNKNOWN = -1
    }

    private val prefs: SharedPreferences = createCredentialPrefs()

    private val _cookieFlow = MutableStateFlow(prefs.getString(KEY_COOKIE, null))
    val cookieFlow: StateFlow<String?> = _cookieFlow.asStateFlow()

    private val _vipTypeFlow = MutableStateFlow(prefs.getInt(KEY_VIP_TYPE, VIP_UNKNOWN))
    val vipTypeFlow: StateFlow<Int> = _vipTypeFlow.asStateFlow()

    private val _purchasedAlbumIdsFlow = MutableStateFlow(
        prefs.getStringSet(KEY_PURCHASED_ALBUMS, emptySet()).orEmpty()
    )
    val purchasedAlbumIdsFlow: StateFlow<Set<String>> = _purchasedAlbumIdsFlow.asStateFlow()

    private val _cookieValidFlow = MutableStateFlow(
        prefs.getBoolean(KEY_COOKIE_VALID, true)
    )
    val cookieValidFlow: StateFlow<Boolean> = _cookieValidFlow.asStateFlow()

    private val _nicknameFlow = MutableStateFlow(prefs.getString(KEY_NICKNAME, null))
    val nicknameFlow: StateFlow<String?> = _nicknameFlow.asStateFlow()

    private val _avatarUrlFlow = MutableStateFlow(prefs.getString(KEY_AVATAR_URL, null))
    val avatarUrlFlow: StateFlow<String?> = _avatarUrlFlow.asStateFlow()

    fun hasCookie(): Boolean = !_cookieFlow.value.isNullOrBlank()

    fun getCookie(): String? = _cookieFlow.value

    fun getVipType(): Int = _vipTypeFlow.value

    fun getAccountId(): Long = prefs.getLong(KEY_ACCOUNT_ID, -1L)

    fun getNickname(): String? = _nicknameFlow.value

    fun getAvatarUrl(): String? = _avatarUrlFlow.value

    fun isAlbumPurchased(albumId: String?): Boolean =
        albumId != null && albumId in _purchasedAlbumIdsFlow.value

    fun getAccountUpdatedAt(): Long = prefs.getLong(KEY_VIP_UPDATED_AT, 0L)

    fun setCookie(value: String) {
        val trimmed = value.trim()
        prefs.edit()
            .putString(KEY_COOKIE, trimmed)
            .putBoolean(KEY_COOKIE_VALID, true)
            .apply()
        _cookieFlow.value = trimmed
        _cookieValidFlow.value = true
    }

    fun setAccountInfo(vipType: Int, accountId: Long, purchasedAlbumIds: Set<String>) {
        prefs.edit()
            .putInt(KEY_VIP_TYPE, vipType)
            .putLong(KEY_ACCOUNT_ID, accountId)
            .putLong(KEY_VIP_UPDATED_AT, System.currentTimeMillis())
            .putStringSet(KEY_PURCHASED_ALBUMS, purchasedAlbumIds)
            .putBoolean(KEY_COOKIE_VALID, true)
            .apply()
        _vipTypeFlow.value = vipType
        _purchasedAlbumIdsFlow.value = purchasedAlbumIds
        _cookieValidFlow.value = true
    }

    fun setProfile(nickname: String?, avatarUrl: String?) {
        prefs.edit()
            .putString(KEY_NICKNAME, nickname)
            .putString(KEY_AVATAR_URL, avatarUrl)
            .apply()
        _nicknameFlow.value = nickname
        _avatarUrlFlow.value = avatarUrl
    }

    fun markCookieInvalid() {
        prefs.edit()
            .putBoolean(KEY_COOKIE_VALID, false)
            .putInt(KEY_VIP_TYPE, VIP_UNKNOWN)
            .apply()
        _cookieValidFlow.value = false
        _vipTypeFlow.value = VIP_UNKNOWN
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_COOKIE)
            .remove(KEY_VIP_TYPE)
            .remove(KEY_ACCOUNT_ID)
            .remove(KEY_VIP_UPDATED_AT)
            .remove(KEY_PURCHASED_ALBUMS)
            .remove(KEY_COOKIE_VALID)
            .remove(KEY_NICKNAME)
            .remove(KEY_AVATAR_URL)
            .apply()
        _cookieFlow.value = null
        _vipTypeFlow.value = VIP_UNKNOWN
        _purchasedAlbumIdsFlow.value = emptySet()
        _cookieValidFlow.value = true
        _nicknameFlow.value = null
        _avatarUrlFlow.value = null
    }

    private fun createCredentialPrefs(): SharedPreferences = try {
        createEncryptedPrefs()
    } catch (e: Exception) {
        Timber.e(e, "$TAG: EncryptedSharedPreferences unreadable, deleting and recreating")
        context.deleteSharedPreferences(PREFS_NAME)
        try {
            createEncryptedPrefs()
        } catch (e2: Exception) {
            Timber.e(e2, "$TAG: EncryptedSharedPreferences still failing, falling back to plain prefs")
            context.getSharedPreferences("${PREFS_NAME}_plain", Context.MODE_PRIVATE)
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
