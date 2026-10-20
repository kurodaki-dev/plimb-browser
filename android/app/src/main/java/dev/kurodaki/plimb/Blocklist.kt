package dev.kurodaki.plimb

import android.content.Context
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Liste de blocage reseau (publicites et traqueurs), chargee depuis les assets
 * et eventuellement completee par un fichier utilisateur.
 */
object Blocklist {

    private const val ASSET_FILE = "adblock/hosts.txt"
    private const val USER_FILE = "blocklist.txt"

    private val hosts = mutableSetOf<String>()
    private var loaded = false

    fun ensure(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val app = context.applicationContext
            hosts.clear()
            Extensions.readAsset(app, ASSET_FILE)?.lines()?.forEach { parseLine(it) }
            val userFile = java.io.File(app.filesDir, USER_FILE)
            if (userFile.exists()) userFile.readText().lines().forEach { parseLine(it) }
            loaded = true
        }
    }

    private fun parseLine(line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) return
        val domain = trimmed.split(Regex("\\s+")).lastOrNull()
            ?.lowercase()
            ?.trimEnd('.')
            ?: return
        if (domain.isEmpty() || domain.contains('*') || domain.contains('/')) return
        if (Regex("^[a-z0-9.-]+$").matches(domain)) hosts.add(domain)
    }

    /** Un hote (ou l un de ses sous-domaines) est-il bloque ? */
    fun isBlocked(context: Context, host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        ensure(context)
        val candidate = host.lowercase().trimEnd('.')
        if (candidate.isEmpty()) return false
        if (hosts.contains(candidate)) return true
        return hosts.any { candidate.endsWith(".$it") }
    }

    /** Reponse vide utilisee pour annuler une requete bloquee. */
    fun emptyResponse(): WebResourceResponse =
        WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )
}
