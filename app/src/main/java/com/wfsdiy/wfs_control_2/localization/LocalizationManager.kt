package com.wfsdiy.wfs_control_2.localization

import android.content.Context
import com.wfsdiy.wfs_control_2.PREFS_NAME
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.Locale

internal const val KEY_LANGUAGE = "language"

object LocalizationManager {
    private var translations: JSONObject = JSONObject()
    private var fallback: JSONObject = JSONObject()
    private val _currentLanguage = MutableStateFlow("en")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()
    var availableLanguages: List<Pair<String, String>> = listOf("en" to "English")
        private set

    fun init(context: Context) {
        discoverLanguages(context)
        // Load saved preference, fallback to device locale, fallback to "en"
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
        val deviceLocale = Locale.getDefault().language // "fr", "de", etc.
        val targetLang = saved ?: deviceLocale
        // Always load English as fallback
        fallback = loadJson(context, "en")
        // Load target language (may be same as fallback)
        val langAvailable = availableLanguages.any { it.first == targetLang }
        loadLanguage(context, if (langAvailable) targetLang else "en")
    }

    fun loadLanguage(context: Context, code: String) {
        translations = loadJson(context, code)
        _currentLanguage.value = code
        // Save preference
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, code).apply()
    }

    fun get(key: String): String =
        resolve(translations, key)
            ?: resolve(fallback, key)
            ?: key

    fun get(key: String, vararg params: Pair<String, String>): String {
        var result = get(key)
        for ((name, value) in params) result = result.replace("{$name}", value)
        return result
    }

    private fun resolve(json: JSONObject, key: String): String? {
        val parts = key.split(".")
        var current: Any = json
        for (part in parts) {
            current = when (current) {
                is JSONObject -> current.opt(part) ?: return null
                else -> return null
            }
        }
        return if (current is String) current else current.toString()
    }

    private fun loadJson(context: Context, locale: String): JSONObject {
        return try {
            val inputStream = context.assets.open("lang/$locale.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            JSONObject(jsonString)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun discoverLanguages(context: Context) {
        val languages = mutableListOf<Pair<String, String>>()
        try {
            val files = context.assets.list("lang") ?: emptyArray()
            for (file in files.sorted()) {
                if (file.endsWith(".json")) {
                    val code = file.removeSuffix(".json")
                    val json = loadJson(context, code)
                    val meta = json.optJSONObject("meta")
                    val name = meta?.optString("language", code) ?: code
                    languages.add(code to name)
                }
            }
        } catch (e: Exception) {
            // Fallback to English only
        }
        if (languages.isEmpty()) {
            languages.add("en" to "English")
        }
        availableLanguages = languages
    }
}
