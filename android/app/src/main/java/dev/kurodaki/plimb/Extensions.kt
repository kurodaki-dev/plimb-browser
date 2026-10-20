package dev.kurodaki.plimb

import android.content.Context
import android.webkit.WebView

/**
 * Modele d une extension Plimb (format compatible avec les manifests d extensions
 * Chrome MV2, simplifie : pas de background, que des scripts injectes).
 */
data class Extension(
    val id: String = "",
    val name: String = "",
    val version: String = "1.0.0",
    val description: String = "",
    val author: String = "",
    val homepage: String = "",
    val defaultEnabled: Boolean = true,
    val matches: List<String> = emptyList(),
    val js: List<String> = emptyList(),
    val runAt: String = "document_end",
    var bundled: Boolean = false,
    var baseDir: String = ""
) {
    fun isEnabled(context: Context): Boolean =
        Prefs.extensionPrefs(context).getBoolean("enabled_$id", defaultEnabled)

    fun setEnabled(context: Context, value: Boolean) {
        Prefs.extensionPrefs(context).edit().putBoolean("enabled_$id", value).apply()
    }
}

/**
 * Gestionnaire d extensions : decouverte, activation, injection dans les pages
 * et installation de scripts utilisateur.
 */
object Extensions {

    private const val ASSETS_DIR = "extensions"
    const val USER_DIR = "extensions/user"

    private val gson = com.google.gson.Gson()
    private val cache = mutableListOf<Extension>()

    /** Vide le cache (apres une installation ou une suppression). */
    fun reset() {
        synchronized(cache) { cache.clear() }
    }

    /** Toutes les extensions disponibles (integrees + utilisateur). */
    fun all(context: Context): List<Extension> {
        synchronized(cache) {
            if (cache.isEmpty()) {
                loadBundled(context.applicationContext)
                loadUser(context.applicationContext)
            }
            return cache.toList()
        }
    }

    private fun loadBundled(context: Context) {
        val ids = runCatching { context.assets.list(ASSETS_DIR) }.getOrNull() ?: return
        ids.filter { it.isNotBlank() }.forEach { id ->
            val dir = "$ASSETS_DIR/$id"
            val manifest = readAsset(context, "$dir/manifest.json") ?: return@forEach
            parse(manifest)?.let { ext ->
                ext.bundled = true
                ext.baseDir = dir
                cache.add(ext)
            }
        }
    }

    private fun loadUser(context: Context) {
        val userDir = java.io.File(context.filesDir, USER_DIR)
        val dirs = userDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: return
        for (dir in dirs) {
            val manifestFile = java.io.File(dir, "manifest.json")
            if (!manifestFile.exists()) continue
            val ext = parse(manifestFile.readText()) ?: continue
            ext.bundled = false
            ext.baseDir = dir.absolutePath
            if (cache.none { it.id == ext.id }) cache.add(ext)
        }
    }

    private fun parse(json: String): Extension? =
        runCatching { gson.fromJson(json, Extension::class.java) }.getOrNull()
            ?.takeIf { it.id.isNotBlank() && it.js.isNotEmpty() }

    /** Lit un fichier des assets (aussi utilise par le bloqueur). */
    fun readAsset(context: Context, path: String): String? =
        runCatching {
            context.assets.open(path).bufferedReader().use { it.readText() }
        }.getOrNull()

    fun readScript(context: Context, ext: Extension, file: String): String? =
        runCatching {
            if (ext.bundled) readAsset(context, "${ext.baseDir}/$file")
            else java.io.File(ext.baseDir, file).readText()
        }.getOrNull()

    /**
     * Injecte dans la page tous les scripts des extensions actives dont les
     * filtres correspondent a l URL. A appeler sur `onPageFinished`.
     */
    fun inject(context: Context, webView: WebView) {
        val url = webView.url ?: return
        for (ext in all(context)) {
            if (!ext.isEnabled(context)) continue
            val patterns = ext.matches.mapNotNull { MatchPatterns.compile(it) }
            if (patterns.isEmpty() || patterns.none { it.matches(url) }) continue
            for (file in ext.js) {
                val code = readScript(context, ext, file) ?: continue
                webView.evaluateJavascript(code, null)
            }
        }
    }

    /**
     * Installe un script utilisateur (.user.js) telecharge depuis une URL.
     * Retourne l extension creee, ou null en cas d echec.
     */
    fun installUserScript(
        context: Context,
        id: String,
        name: String,
        source: String,
        script: String
    ): Extension? {
        val app = context.applicationContext
        val dir = java.io.File(app.filesDir, "$USER_DIR/$id")
        if (!dir.exists() && !dir.mkdirs()) return null
        val fileName = sanitize(id) + ".user.js"
        runCatching { java.io.File(dir, fileName).writeText(script) }.getOrNull() ?: return null
        val ext = Extension(
            id = id,
            name = name.ifBlank { id },
            description = "Script utilisateur importe depuis $source",
            author = "Import utilisateur",
            matches = listOf("*://*/*"),
            js = listOf(fileName),
            runAt = "document_end",
            bundled = false,
            baseDir = dir.absolutePath
        )
        runCatching { java.io.File(dir, "manifest.json").writeText(gson.toJson(ext)) }
            .getOrNull() ?: return null
        reset()
        return ext
    }

    /** Supprime une extension utilisateur. */
    fun removeUser(context: Context, id: String): Boolean {
        val dir = java.io.File(context.applicationContext.filesDir, "$USER_DIR/$id")
        val deleted = !dir.exists() || dir.deleteRecursively()
        reset()
        return deleted
    }

    private fun sanitize(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_-]"), "-").lowercase().ifBlank { "script" }
}
