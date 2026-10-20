package dev.kurodaki.plimb

import android.webkit.WebView

/** Un onglet = une WebView + son etat. */
class PlimbTab(val webView: WebView) {

    /** Derniere URL dans laquelle les extensions ont ete injectees. */
    var lastInjected: String? = null

    val url: String
        get() = webView.url ?: ""

    val title: String
        get() = webView.title?.takeIf { it.isNotBlank() } ?: url
}
