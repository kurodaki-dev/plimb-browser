package dev.kurodaki.plimb

import android.app.Application

/**
 * Application Plimb Browser. Charge au démarrage la liste de blocage
 * (publicites / traqueurs) pour que le filtrage reseau soit immediatement disponible.
 */
class PlimbApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Blocklist.ensure(this)
    }
}
