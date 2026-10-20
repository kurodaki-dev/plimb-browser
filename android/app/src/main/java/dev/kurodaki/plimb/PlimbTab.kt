package dev.kurodaki.plimb

import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/** Un onglet = une GeckoSession (moteur Gecko) + la GeckoView qui l affiche. */
class PlimbTab(val session: GeckoSession, val geckoView: GeckoView) {

    var url: String = ""
    var title: String = ""
    var loading: Boolean = false
    var progress: Int = 0

    val isLoading: Boolean
        get() = loading || progress in 1..99
}
