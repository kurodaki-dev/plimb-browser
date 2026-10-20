package dev.kurodaki.plimb

import android.net.Uri
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebResponse

/**
 * Tous les callbacks du moteur Gecko pour un onglet.
 * L activite n est notifiee que lorsque l onglet concerne est l onglet actif.
 */
class TabCallbacks(
    private val activity: MainActivity,
    private val tab: PlimbTab
) : GeckoSession.ProgressDelegate,
    GeckoSession.NavigationDelegate,
    GeckoSession.ContentDelegate,
    GeckoSession.PromptDelegate,
    GeckoSession.PermissionDelegate {

    var canGoBack = false
        private set
    var canGoForward = false
        private set

    private fun notifyUi() {
        if (activity.currentTab() === tab) activity.onTabStateChanged()
    }

    // ------------------------------------------------------------ Progression

    override fun onPageStart(session: GeckoSession, url: String) {
        tab.loading = true
        tab.url = url
        notifyUi()
    }

    override fun onPageStop(session: GeckoSession, success: Boolean) {
        tab.loading = false
        notifyUi()
    }

    override fun onProgressChange(session: GeckoSession, progress: Int) {
        tab.progress = progress
        notifyUi()
    }

    // ------------------------------------------------------------ Navigation

    override fun onLocationChange(
        session: GeckoSession,
        url: String?,
        perms: List<GeckoSession.PermissionDelegate.ContentPermission>?,
        hasUserGesture: Boolean
    ) {
        tab.url = url ?: ""
        notifyUi()
    }

    override fun onTitleChange(session: GeckoSession, title: String?) {
        tab.title = title?.takeIf { it.isNotBlank() } ?: tab.url
        notifyUi()
    }

    override fun onCanGoBack(session: GeckoSession, value: Boolean) {
        canGoBack = value
        notifyUi()
    }

    override fun onCanGoForward(session: GeckoSession, value: Boolean) {
        canGoForward = value
        notifyUi()
    }

    override fun onLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest
    ): GeckoResult<AllowOrDeny>? {
        // target=_blank et window.open (avec geste utilisateur) deviennent des onglets.
        if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW && request.hasUserGesture) {
            activity.openTab(request.uri)
            return GeckoResult.deny()
        }

        val scheme = Uri.parse(request.uri).scheme?.lowercase()
        val handled = scheme != null && scheme !in INTERNAL_SCHEMES
        if (handled) {
            activity.openExternally(Uri.parse(request.uri))
            return GeckoResult.deny()
        }
        return null
    }

    // ------------------------------------------------------------- Contenu

    override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
        activity.onDownload(session, response)
    }

    override fun onCloseRequest(session: GeckoSession) {
        activity.closeTab(activity.indexOf(tab))
    }

    override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
        activity.onFullScreenRequested(fullScreen)
    }

    // ------------------------------------------------------------- Prompts

    override fun onFilePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.FilePrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val multiple = prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE
        activity.pickFile(prompt.mimeTypes, multiple) { uris ->
            val response =
                if (uris.isNullOrEmpty()) prompt.dismiss()
                else prompt.confirm(activity, uris)
            result.complete(response)
        }
        return result
    }

    override fun onBeforeUnloadPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))
    }

    // --------------------------------------------------------- Permissions

    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<out String>?,
        callback: GeckoSession.PermissionDelegate.Callback
    ) {
        val missing = permissions?.filter { permission ->
            androidx.core.content.ContextCompat.checkSelfPermission(activity, permission) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } ?: emptyList()

        if (missing.isEmpty()) {
            callback.grant()
        } else {
            activity.requestAppPermissions(missing.toTypedArray()) { granted ->
                if (granted) callback.grant() else callback.reject()
            }
        }
    }

    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: GeckoSession.PermissionDelegate.ContentPermission
    ): GeckoResult<Int>? {
        val autoGrant = when (perm.permission) {
            GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION,
            GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE,
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE,
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE,
            GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS -> true
            else -> false
        }
        if (autoGrant) {
            return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
        }
        return null
    }

    companion object {
        private val INTERNAL_SCHEMES = setOf("http", "https", "file", "data", "about", "moz-extension")
    }
}
