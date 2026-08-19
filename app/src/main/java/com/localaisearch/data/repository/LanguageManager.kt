package com.localaisearch.data.repository

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.languageDataStore: DataStore<Preferences> by preferencesDataStore(name = "language_prefs")

/**
 * Language manager supporting 10 languages + system default:
 * - en (English)
 * - zh-CN (Simplified Chinese)
 * - zh-TW (Traditional Chinese)
 * - ru (Russian)
 * - ko (Korean)
 * - ja (Japanese)
 * - ar (Arabic - RTL)
 * - pt (Portuguese)
 * - fr (French)
 * - de (German)
 *
 * Uses DataStore for persistence. Falls back to system default when not set.
 */
class LanguageManager(private val context: Context) {

    companion object {
        private val LANGUAGE_KEY = stringPreferencesKey("app_language")

        const val SYSTEM_DEFAULT = "system"
        const val ENGLISH = "en"
        const val SIMPLIFIED_CHINESE = "zh-CN"
        const val TRADITIONAL_CHINESE = "zh-TW"
        const val RUSSIAN = "ru"
        const val KOREAN = "ko"
        const val JAPANESE = "ja"
        const val ARABIC = "ar"
        const val PORTUGUESE = "pt"
        const val FRENCH = "fr"
        const val GERMAN = "de"

        val SUPPORTED_LANGUAGES = listOf(
            SYSTEM_DEFAULT,
            ENGLISH,
            SIMPLIFIED_CHINESE,
            TRADITIONAL_CHINESE,
            RUSSIAN,
            KOREAN,
            JAPANESE,
            ARABIC,
            PORTUGUESE,
            FRENCH,
            GERMAN
        )

        fun isRtlLanguage(language: String): Boolean {
            return language == ARABIC
        }
    }

    val currentLanguage: Flow<String> = context.languageDataStore.data.map { prefs ->
        prefs[LANGUAGE_KEY] ?: SYSTEM_DEFAULT
    }

    /**
     * Get the currently set language code synchronously.
     */
    suspend fun getCurrentLanguageCode(): String {
        return currentLanguage.first()
    }

    suspend fun setLanguage(languageCode: String) {
        context.languageDataStore.edit { prefs ->
            prefs[LANGUAGE_KEY] = languageCode
        }
    }

    /**
     * Apply the language to the app's configuration and recreate the activity.
     * @return true if the activity was recreated, false if no change was needed.
     */
    fun applyLanguage(activity: Activity, languageCode: String): Boolean {
        val locale = when (languageCode) {
            SIMPLIFIED_CHINESE -> Locale.SIMPLIFIED_CHINESE
            TRADITIONAL_CHINESE -> Locale.TRADITIONAL_CHINESE
            ENGLISH -> Locale.ENGLISH
            RUSSIAN -> Locale("ru")
            KOREAN -> Locale.KOREAN
            JAPANESE -> Locale.JAPANESE
            ARABIC -> Locale("ar")
            PORTUGUESE -> Locale("pt")
            FRENCH -> Locale.FRENCH
            GERMAN -> Locale.GERMAN
            else -> getSystemLocale()
        }

        val currentLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            activity.resources.configuration.locales.get(0)
        } else {
            @Suppress("DEPRECATION")
            activity.resources.configuration.locale
        }

        if (currentLocale == locale) {
            return false
        }

        Locale.setDefault(locale)
        val config = Configuration(activity.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        @Suppress("DEPRECATION")
        activity.resources.updateConfiguration(config, activity.resources.displayMetrics)

        // Recreate activity for changes to take full effect
        activity.recreate()
        return true
    }

    /**
     * Check if the given language requires RTL layout.
     */
    fun requiresRtl(languageCode: String): Boolean {
        return isRtlLanguage(languageCode)
    }

    private fun getSystemLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LocaleList.getDefault().get(0) ?: Locale.ENGLISH
        } else {
            @Suppress("DEPRECATION")
            Locale.getDefault()
        }
    }

    /**
     * Get the display name for a language code.
     */
    fun getLanguageDisplayName(code: String): String {
        return when (code) {
            SYSTEM_DEFAULT -> "System Default"
            ENGLISH -> "English"
            SIMPLIFIED_CHINESE -> "\u7B80\u4F53\u4E2D\u6587"
            TRADITIONAL_CHINESE -> "\u7E41\u9AD4\u4E2D\u6587"
            RUSSIAN -> "\u0420\u0443\u0441\u0441\u043A\u0438\u0439"
            KOREAN -> "\uD55C\uAD6D\uC5B4"
            JAPANESE -> "\u65E5\u672C\u8A9E"
            ARABIC -> "\u0627\u0644\u0639\u0631\u0628\u064A\u0629"
            PORTUGUESE -> "Portugu\u00EAs"
            FRENCH -> "Fran\u00E7ais"
            GERMAN -> "Deutsch"
            else -> code
        }
    }

    /**
     * Get the native display name for a language code.
     */
    fun getLanguageNativeName(code: String): String {
        return when (code) {
            SYSTEM_DEFAULT -> ""
            ENGLISH -> "English"
            SIMPLIFIED_CHINESE -> "\u7B80\u4F53\u4E2D\u6587"
            TRADITIONAL_CHINESE -> "\u7E41\u9AD4\u4E2D\u6587"
            RUSSIAN -> "\u0420\u0443\u0441\u0441\u043A\u0438\u0439"
            KOREAN -> "\uD55C\uAD6D\uC5B4"
            JAPANESE -> "\u65E5\u672C\u8A9E"
            ARABIC -> "\u0627\u0644\u0639\u0631\u0628\u064A\u0629"
            PORTUGUESE -> "Portugu\u00EAs"
            FRENCH -> "Fran\u00E7ais"
            GERMAN -> "Deutsch"
            else -> code
        }
    }
}
