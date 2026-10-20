package dev.kurodaki.plimb

import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * Un onglet = une GeckoSession (moteur Gecko) + la GeckoView qui l affiche.
 *
 * `canGoBack` / `canGoForward` sont tenus a jour par les callbacks du moteur
 * (NavigationDelegate), car GeckoSession ne les expose pas en lecture directe.
 */
class PlimbTab(
    val session: GeckoSession,
    val geckoView: GeckoView
) {

    /** Callbacks du moteur pour cet onglet (positionnes par MainActivity). */
    var callbacks: TabCallbacks? = null

    var url: String = ""
    var title: String = ""
    var loading: Boolean = false
    var progress: Int = 0

    val canGoBack: Boolean
        get() = callbacks?.canGoBack == true

    val canGoForward: Boolean
        get() = callbacks?.canGoForward == true

    val isLoading: Boolean
        get() = loading || progress in 1..99
}
