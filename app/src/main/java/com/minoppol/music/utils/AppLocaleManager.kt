package com.minoppol.music.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.minoppol.music.data.preferences.AppLanguage
import java.util.Locale

object AppLocaleManager {
    private const val PREFERENCES_NAME = "app_locale_preferences"
    private const val KEY_LANGUAGE_TAG = "app_language_tag"

    fun currentLanguageTag(context: Context): String =
        AppLanguage.normalize(
            context
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE_TAG, AppLanguage.SYSTEM.tag)
                .orEmpty()
        )

    fun applyLanguage(context: Context, languageTag: String) {
        val normalized = AppLanguage.normalize(languageTag)
        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_TAG, normalized)
            .apply()
    }

    fun localeForTag(languageTag: String): Locale {
        val parts = languageTag.split("-", limit = 2)
        val language = parts.getOrElse(0) { "en" }
        val country = parts.getOrNull(1) ?: ""
        return if (country.isNotEmpty()) Locale(language, country) else Locale(language)
    }

    fun wrapContext(base: Context): Context {
        val languageTag = currentLanguageTag(base)
        if (languageTag.isBlank()) return base

        val locale = localeForTag(languageTag)
        Locale.setDefault(locale)

        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setLocales(LocaleList(locale))
            }
        }

        return base.createConfigurationContext(configuration)
    }

    fun refreshApplicationContextLocale(context: Context) {
        val languageTag = currentLanguageTag(context)
        val locale = if (languageTag.isBlank()) Locale.getDefault() else localeForTag(languageTag)
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setLocales(LocaleList(locale))
            }
        }
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
    }
}
