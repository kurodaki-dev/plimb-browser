package dev.kurodaki.plimb

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Client transitoire pour les fenetres ouvertes par la page (window.open / target=_blank).
 * Des que la page enfant commence a naviguer, la WebView est promue en onglet normal.
 */
class PopupClient(private val owner: MainActivity, private val popup: WebView) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        owner.adoptPopup(popup, request.url.toString())
        return true
    }
}
