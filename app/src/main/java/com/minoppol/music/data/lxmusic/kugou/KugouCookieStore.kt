package com.minoppol.music.data.lxmusic.kugou

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KugouCookieStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "KugouCookieStore"
        private const val PREFS_NAME = "kugou_prefs"
        private const val KEY_COOKIE = "kg_cookie"
        private const val KEY_VIP_TYPE = "kg_vip_type"
        private const val KEY_ACCOUNT_ID = "kg_account_id"
        private const val KEY_VIP_UPDATED_AT = "kg_vip_updated_at"
        private const val KEY_COOKIE_VALID = "kg_cookie_valid"
        private const val KEY_NICKNAME = "kg_nickname"
        private const val KEY_AVATAR_URL = "kg_avatar_url"
        private const val KEY_APP_TOKEN = "kg_app_token"
        private const val KEY_APP_USERID = "kg_app_userid"
        private const val KEY_APP_VIP_TYPE = "kg_app_vip_type"
        const val VIP_UNKNOWN = -1
    }

    private val prefs: SharedPreferences = createCredentialPrefs()

    private val _cookieFlow = MutableStateFlow(prefs.getString(KEY_COOKIE, null))
    val cookieFlow: StateFlow<String?> = _cookieFlow.asStateFlow()

    private val _vipTypeFlow = MutableStateFlow(prefs.getInt(KEY_VIP_TYPE, VIP_UNKNOWN))
    val vipTypeFlow: StateFlow<Int> = _vipTypeFlow.asStateFlow()

    private val _cookieValidFlow = MutableStateFlow(
        prefs.getBoolean(KEY_COOKIE_VALID, true)
    )
    val cookieValidFlow: StateFlow<Boolean> = _cookieValidFlow.asStateFlow()

    private val _nicknameFlow = MutableStateFlow(prefs.getString(KEY_NICKNAME, null))
    val nicknameFlow: StateFlow<String?> = _nicknameFlow.asStateFlow()

    private val _avatarUrlFlow = MutableStateFlow(prefs.getString(KEY_AVATAR_URL, null))
    val avatarUrlFlow: StateFlow<String?> = _avatarUrlFlow.asStateFlow()

    private val _appTokenFlow = MutableStateFlow(prefs.getString(KEY_APP_TOKEN, null))
    val appTokenFlow: StateFlow<String?> = _appTokenFlow.asStateFlow()
    private val _appUseridFlow = MutableStateFlow(prefs.getString(KEY_APP_USERID, null))
    val appUseridFlow: StateFlow<String?> = _appUseridFlow.asStateFlow()

    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val loggedInFlow: StateFlow<Boolean> = combine(_cookieFlow, _appTokenFlow) { cookie, appToken ->
        !cookie.isNullOrBlank() || !appToken.isNullOrBlank()
    }.stateIn(storeScope, SharingStarted.Eagerly, hasCookie() || hasAppLogin())

    fun hasCookie(): Boolean = !_cookieFlow.value.isNullOrBlank()

    fun getCookie(): String? = _cookieFlow.value

    fun hasAppLogin(): Boolean = !_appTokenFlow.value.isNullOrBlank()

    fun getAppToken(): String? = _appTokenFlow.value

    fun getAppUserid(): String? = _appUseridFlow.value

    fun getVipType(): Int = _vipTypeFlow.value

    fun getAccountId(): Long = prefs.getLong(KEY_ACCOUNT_ID, -1L)

    fun getNickname(): String? = _nicknameFlow.value

    fun getAvatarUrl(): String? = _avatarUrlFlow.value

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

    fun setAccountInfo(vipType: Int, accountId: Long) {
        prefs.edit()
            .putInt(KEY_VIP_TYPE, vipType)
            .putLong(KEY_ACCOUNT_ID, accountId)
            .putLong(KEY_VIP_UPDATED_AT, System.currentTimeMillis())
            .putBoolean(KEY_COOKIE_VALID, true)
            .apply()
        _vipTypeFlow.value = vipType
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

    fun setAppLogin(token: String, userid: String, vipType: Int) {
        prefs.edit()
            .putString(KEY_APP_TOKEN, token)
            .putString(KEY_APP_USERID, userid)
            .putInt(KEY_APP_VIP_TYPE, vipType)
            .putLong(KEY_ACCOUNT_ID, userid.toLongOrNull() ?: -1L)
            .apply()
        _appTokenFlow.value = token
        _appUseridFlow.value = userid
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
            .remove(KEY_COOKIE_VALID)
            .remove(KEY_NICKNAME)
            .remove(KEY_AVATAR_URL)
            .remove(KEY_APP_TOKEN)
            .remove(KEY_APP_USERID)
            .remove(KEY_APP_VIP_TYPE)
            .apply()
        _cookieFlow.value = null
        _vipTypeFlow.value = VIP_UNKNOWN
        _cookieValidFlow.value = true
        _nicknameFlow.value = null
        _avatarUrlFlow.value = null
        _appTokenFlow.value = null
        _appUseridFlow.value = null
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
