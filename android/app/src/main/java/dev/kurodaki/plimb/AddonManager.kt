package dev.kurodaki.plimb

import android.util.Log
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/** Representation simplifiee d un add-on pour l affichage. */
data class AddonEntry(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val enabled: Boolean,
    val builtIn: Boolean
)

/**
 * Gestionnaire d extensions (WebExtensions) via l API officielle de GeckoView.
 *
 * Les extensions integrees sont livrees dans /assets/extensions et installees au demarrage.
 * Les autres peuvent etre installees depuis un fichier .xpi ou une URL (addons.mozilla.org).
 */
object AddonManager {

    const val INSTALL_FROM_FILE = WebExtensionController.INSTALLATION_METHOD_FROM_FILE
    const val INSTALL_FROM_MANAGER = WebExtensionController.INSTALLATION_METHOD_MANAGER

    private val BUILT_IN = mapOf(
        "plimb-blocker@plimb.browser" to "resource://android/assets/extensions/blocker/",
        "plimb-dark-theme@plimb.browser" to "resource://android/assets/extensions/dark-theme/",
        "plimb-cookie-banner@plimb.browser" to "resource://android/assets/extensions/cookie-banner/"
    )

    /** Installe (une seule fois) les extensions livrees avec l application. */
    fun installBuiltIns(runtime: GeckoRuntime) {
        val controller = runtime.webExtensionController
        BUILT_IN.forEach { (id, location) ->
            controller.ensureBuiltIn(location, id).accept(
                {},
                { error -> Log.w("Plimb", "Extension integree non installee : $id", error) }
            )
        }
    }

    fun list(runtime: GeckoRuntime): GeckoResult<List<AddonEntry>> =
        runtime.webExtensionController.list()
            .map { extensions -> extensions.map { it.toEntry() } }

    fun install(runtime: GeckoRuntime, uri: String): GeckoResult<WebExtension> =
        runtime.webExtensionController.install(uri, INSTALL_FROM_MANAGER)

    fun setEnabled(runtime: GeckoRuntime, entry: AddonEntry, enabled: Boolean) {
        find(runtime, entry.id) { extension ->
            if (enabled) {
                runtime.webExtensionController.enable(extension, WebExtensionController.EnableSource.USER)
            } else {
                runtime.webExtensionController.disable(extension, WebExtensionController.EnableSource.USER)
            }
        }
    }

    fun uninstall(runtime: GeckoRuntime, entry: AddonEntry) {
        find(runtime, entry.id) { extension ->
            runtime.webExtensionController.uninstall(extension)
        }
    }

    private fun find(runtime: GeckoRuntime, id: String, onFound: (WebExtension) -> Unit) {
        runtime.webExtensionController.list().accept(
            { extensions -> extensions.firstOrNull { it.id == id }?.let(onFound) },
            { error -> Log.w("Plimb", "Extension introuvable : $id", error) }
        )
    }

    private fun WebExtension.toEntry(): AddonEntry = AddonEntry(
        id = id,
        name = metaData?.name ?: id,
        version = metaData?.version ?: "",
        description = metaData?.description ?: "",
        enabled = metaData?.enabled ?: true,
        builtIn = isBuiltIn || id.startsWith("plimb-")
    )
}
