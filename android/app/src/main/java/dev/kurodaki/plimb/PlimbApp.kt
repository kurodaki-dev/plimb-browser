package dev.kurodaki.plimb

import android.app.Application
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Plimb Browser (Android) — moteur GeckoView (Firefox).
 *
 * GeckoView est le moteur de rendu embarque de Firefox : il expose un veritable systeme
 * d extensions WebExtensions, contrairement au WebView systeme.
 */
class PlimbApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (runtime == null) {
            val settings = GeckoRuntimeSettings.Builder()
                // about:config reste accessible (bascule de xpinstall.signatures.required, etc.)
                .aboutConfigEnabled(true)
                .build()
            runtime = GeckoRuntime.create(this, settings)
        }
    }

    companion object {
        var runtime: GeckoRuntime? = null
            private set
    }
}
