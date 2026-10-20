package dev.kurodaki.plimb

import android.app.Application
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Plimb Browser (Android) — moteur GeckoView (Firefox).
 *
 * GeckoView est le moteur de rendu embarque de Firefox : il expose un veritable systeme
 * d extensions WebExtensions (installables depuis addons.mozilla.org ou depuis un .xpi),
 * ce que le WebView systeme ne permet pas.
 */
class PlimbApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (runtime == null) {
            val settings = GeckoRuntimeSettings.Builder()
                .aboutConfigEnabled(true)
                .contentBlocking(GeckoRuntimeSettings.CONTENT_BLOCKING_STRICT)
                .build()
            runtime = GeckoRuntime.create(this, settings)
        }
    }

    companion object {
        var runtime: GeckoRuntime? = null
            private set
    }
}
