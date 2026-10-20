// Plimb Browser — profil Firefox portable
// user.js est applique a chaque demarrage : il configure le navigateur
// sans rien installer sur le systeme.

// Autorise les extensions non signees (les notres, dans profile/extensions).
user_pref("xpinstall.signatures.required", false);

// Pas de telemetrie ni de rapports.
user_pref("toolkit.telemetry.enabled", false);
user_pref("toolkit.telemetry.unified", false);
user_pref("datareporting.healthreport.uploadEnabled", false);
user_pref("datareporting.policy.dataSubmissionEnabled", false);
user_pref("app.shield.optoutstudies.enabled", false);
user_pref("browser.ping-centre.telemetry", false);

// Portable : pas de mise a jour automatique du moteur (le zip est fige).
user_pref("app.update.auto", false);
user_pref("app.update.checkInstallTime", false);

// Profil autonome, pas de verification du navigateur par defaut.
user_pref("browser.shell.checkDefaultBrowser", false);
user_pref("browser.startup.homepage_override.mstone", "ignore");
user_pref("browser.aboutConfig.showWarning", false);

// Demarrage sur la page d accueil.
user_pref("browser.startup.page", 1);
user_pref("browser.startup.homepage", "https://duckduckgo.com");

// Recherche privee : on garde le blocage de tracking maximum.
user_pref("privacy.trackingprotection.enabled", true);
user_pref("privacy.trackingprotection.socialtracking.enabled", true);
user_pref("privacy.trackingprotection.fingerprinting.enabled", true);
