package dev.kurodaki.plimb

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Client WebView principal : blocage des publicites, ouverture des liens externes
 * et notification de l activite a MainActivity.
 */
class PlimbWebViewClient(private val owner: MainActivity) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        if (!owner.isBlockingAds()) return null
        val host = request.url.host ?: return null
        return if (Blocklist.isBlocked(owner, host)) Blocklist.emptyResponse() else null
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val scheme = request.url.scheme?.lowercase() ?: return false
        if (scheme == "http" || scheme == "https" || scheme == "file") return false
        owner.openExternally(request.url)
        return true
    }

    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        owner.onPageStarted(view, url)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        owner.onPageCommitted(view, url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        owner.onPageFinished(view, url)
    }

    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(
        view: WebView,
        handler: android.webkit.SslErrorHandler,
        error: android.net.http.SslError
    ) {
        // On laisse le choix a l utilisateur via la boite de dialogue par defaut.
        handler.cancel()
    }
}
