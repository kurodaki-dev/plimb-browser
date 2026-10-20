package dev.kurodaki.plimb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Un add-on propose par addons.mozilla.org. */
data class AddonListing(
    val id: String,
    val name: String,
    val summary: String,
    val iconUrl: String?,
    val downloadUrl: String,
    val users: Long,
    val rating: Double
)

/**
 * Client minimal de l API publique d addons.mozilla.org (AMO v5).
 * Utilise par le catalogue d extensions integre au navigateur.
 */
object AmoClient {

    private const val SEARCH_URL = "https://addons.mozilla.org/api/v5/addons/search/"
    private const val TIMEOUT = 15_000

    suspend fun search(query: String, limit: Int = 25): List<AddonListing> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url =
                "$SEARCH_URL?app=firefox-android&type=extension&sort=users&limit=$limit&lang=fr&q=$encoded"

            val body = runCatching {\n                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT
                    readTimeout = TIMEOUT
                    setRequestProperty("User-Agent", "PlimbBrowser/1.0 (Android)")
                    setRequestProperty("Accept", "application/json")
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
                connection.disconnect()
                if (code in 200..299) text else null
            }.getOrNull() ?: return@withContext emptyList()

            parse(body)
        }

    private fun parse(payload: String): List<AddonListing> {
        val root = runCatching { JSONObject(payload) }.getOrNull() ?: return emptyList()
        val results = root.optJSONArray("results") ?: return emptyList()
        val listings = mutableListOf<AddonListing>()

        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val downloadUrl = item.optJSONObject("current_version")
                ?.optJSONObject("file")
                ?.optString("url")
                ?.takeIf { it.isNotBlank() && it.startsWith("https://") }
                ?: continue

            listings.add(
                AddonListing(
                    id = item.optString("slug").ifBlank { item.optString("id") },
                    name = localText(item.opt("name")) ?: item.optString("slug"),
                    summary = localText(item.opt("summary"))
                        ?: item.optString("description").ifBlank { "" },
                    iconUrl = pickIcon(item.optJSONObject("icons")),
                    downloadUrl = downloadUrl,
                    users = item.optJSONObject("average_daily_users")?.optLong("value") ?: 0L,
                    rating = item.optJSONObject("ratings")?.optDouble("average") ?: 0.0
                )
            )
        }
        return listings
    }

    private fun pickIcon(icons: JSONObject?): String? {
        if (icons == null) return null
        listOf("64", "48", "32", "128").forEach { key ->
            val value = icons.optString(key)
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    /** Les champs localises d AMO sont des objets { "fr-FR": "..." } ou une chaine simple. */
    private fun localText(value: Any?): String? = when (value) {
        is String -> value.takeIf { it.isNotBlank() }
        is JSONObject -> {
            value.optString("fr-FR").takeIf { it.isNotBlank() }
                ?: value.optString("en-US").takeIf { it.isNotBlank() }
                ?: value.keys().asSequence().mapNotNull { key ->
                    value.optString(key).takeIf { it.isNotBlank() }
                }.firstOrNull()
        }
        else -> null
    }
}
