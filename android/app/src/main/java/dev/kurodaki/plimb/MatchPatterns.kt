package dev.kurodaki.plimb

/**
 * Filtres de correspondance d URL au format des extensions Chrome.
 * Formats acceptes : "<all_urls>", "<url_pattern>", "*://*/*",
 * "https://*.exemple.com/*", "http://exemple.com/page"
 */
object MatchPatterns {

    private const val PORT_GROUP = "(?:\\:[0-9]+)?"

    /** Retourne null si le motif est invalide. */
    fun compile(pattern: String): Regex? {
        val trimmed = pattern.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed == "<all_urls>") return Regex("^[a-z]+://.*$", RegexOption.IGNORE_CASE)

        val separator = trimmed.indexOf("://")
        if (separator <= 0) return null
        val schemePart = trimmed.substring(0, separator)
        val rest = trimmed.substring(separator + 3)

        val scheme = when {
            schemePart == "*" -> "(?:https?|ftp|file)"
            schemePart.contains("|") ->
                "(?:" + schemePart.split("|").joinToString("|") { Regex.escape(it) } + ")"
            else -> Regex.escape(schemePart)
        }

        val slash = rest.indexOf('/')
        val hostPart = if (slash < 0) rest else rest.substring(0, slash)
        val pathPart = if (slash < 0) "/" else rest.substring(slash)

        val hostRegex = when {
            hostPart == "*" -> "[^/]+"
            hostPart.startsWith("*.") -> "[^/]*\\." + Regex.escape(hostPart.substring(2))
            else -> Regex.escape(hostPart)
        }

        val pathRegex = when {
            pathPart == "*" || pathPart == "/*" -> "/.*"
            pathPart.endsWith("*") -> Regex.escape(pathPart.dropLast(1)) + ".*"
            else -> Regex.escape(pathPart)
        }

        return Regex(
            "^" + scheme + "://" + hostRegex + PORT_GROUP + pathRegex + "$",
            RegexOption.IGNORE_CASE
        )
    }

    fun matches(pattern: String, url: String): Boolean =
        compile(pattern)?.matches(url) == true
}
