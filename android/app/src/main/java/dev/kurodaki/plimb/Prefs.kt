package dev.kurodaki.plimb

import android.content.Context
import android.content.SharedPreferences
import java.net.URLEncoder

/** Reglages persistants partages par toute l application. */
object Prefs {

    const val NAME = "plimb_settings"
    const val FILE_EXTENSIONS = "plimb_extensions"

    const val KEY_SEARCH_ENGINE = "search_engine"
    const val KEY_BLOCK_ADS = "block_ads"
    const val KEY_JS = "javascript"
    const val KEY_DESKTOP = "desktop_mode"
    const val KEY_DARK = "dark_mode"
    const val KEY_RESTORE = "restore_tabs"
    const val KEY_SESSION = "session_urls"
    const val KEY_LAST_URL = "last_url"

    val ENGINES = listOf(
        "duckduckgo" to "https://duckduckgo.com/?q=%s",
        "google" to "https://www.google.com/search?q=%s",
        "bing" to "https://www.bing.com/search?q=%s",
        "ecosia" to "https://www.ecosia.org/search?q=%s"
    )

    val ENGINE_LABELS = mapOf(
        "duckduckgo" to "DuckDuckGo",
        "google" to "Google",
        "bing" to "Bing",
        "ecosia" to "Ecosia"
    )

    fun settings(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun extensionPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE_EXTENSIONS, Context.MODE_PRIVATE)

    /** Construit l URL de recherche pour le moteur choisi. */
    fun searchUrl(context: Context, query: String): String {
        val engine = settings(context).getString(KEY_SEARCH_ENGINE, ENGINE_LABELS.keys.first())
            ?: ENGINE_LABELS.keys.first()
        val template = ENGINES.firstOrNull { it.first == engine }?.second ?: ENGINES.first().second
        return template.replace("%s", URLEncoder.encode(query, "UTF-8"))
    }
}
