# Plimb Browser

<p align="center">
  <b>Plimb Browser — navigateur web open source, avec de VRAIES extensions, pour Android et Windows.</b><br/>
  Par <a href="https://github.com/kurodaki-dev">@kurodaki-dev</a> · sous licence MIT
</p>

---

## Deux moteurs, un seul navigateur

Plimb Browser est construit autour de deux moteurs de rendu complets, chacun avec son
**système d'extensions natif** :

| Plateforme | Moteur | Système d'extensions | Store d'extensions |
|------------|--------|----------------------|---------------------|
| **Android** (8.0+) | **GeckoView** (Firefox 141) | WebExtensions (MV2 + MV3) | **addons.mozilla.org** (catalogue intégré) + fichier `.xpi` + URL |
| **Windows** (x64) | **Chromium** (Electron 44) | API `session.extensions` (Chrome MV2/MV3) | **Chrome Web Store** (ID ou URL) + fichier `.crx` + dossier non empaqueté |

**Pourquoi deux moteurs ?** Parce qu'aucun moteur unique ne couvre les deux mondes des extensions :

- **Chrome Web Store = Chromium.** Sur Android, le `WebView` système **n'expose aucune API
  d'extension** : impossible d'avoir `chrome://extensions` ni le Web Store dessus (même Chrome
  pour Android ne supporte pas les extensions). Les navigateurs Android qui y arrivent (Kiwi,
  Lemur) sont des **forks complets de Chromium** — 15+ Go de sources et plusieurs heures de
  compilation C++. On a donc pris l'autre route : **GeckoView**, le moteur Firefox embarqué,
  qui expose un vrai système WebExtensions, sans compiler Chromium.
- **Sur Windows**, Electron embarque Chromium et — depuis sa version 44 — expose une API
  officielle d'extensions. On peut donc y charger de vraies extensions Chrome.

> ⚠️ **Limites assumées** : Electron supporte les extensions *dépaquetées*, pas les `.crx`
> tels quels (on les dépaquette), et ne garantit pas toutes les API Chrome
> (`chrome.action`, `declarativeNetRequest`, `alarms`, `contextMenus` non supportées :
> l'extension s'installe mais est signalée incompatible). Côté GeckoView, seuls les add-ons
> **signés par Mozilla** s'installent et Firefox Android est encore très MV2.

## Ce que fait Plimb Browser

**Commun**
- Onglets multiples, réouverture des liens dans un onglet, navigation arrière/avant
- Barre d'adresse intelligente (URL ou recherche), moteurs : DuckDuckGo, Google, Bing, Ecosia
- Bloqueur de publicité et de traqueurs : **~95 domaines** filtrés avant chargement
- Gestionnaire d'extensions dans l'application : activer/désactiver/supprimer, installation
- Mode sombre, aucune télémétrie, aucune requête vers nos serveurs

**Android**
- Catalogue d'add-ons Mozilla **intégré** : recherche + installation en 1 tap
- Installation depuis un fichier `.xpi` ou une URL
- Extensions intégrées : **Plimb Bloqueur** (blocage réseau), **Mode sombre universel**,
  **Anti bannières cookies**
- Téléchargements (MediaStore), envoi de fichiers dans les formulaires, plein écran,
  permissions caméra/micro/géolocalisation, restauration des onglets
- Intents `ACTION_VIEW` et `ACTION_SEND` (ouvrir un lien depuis une autre app)

**Windows**
- Application **portable** : un `.exe`, rien à installer
- Gestionnaire d'extensions complet (façon `chrome://extensions`) : dossier non empaqueté,
  `.crx`, téléchargement direct depuis le Chrome Web Store
- Extensions intégrées : **Mode sombre universel**, **Anti bannières cookies**,
  **Bouton Picture-in-Picture**
- Téléchargements avec progression, pop-ups dans un onglet, raccourcis clavier

## Compiler soi-même

### Android
```bash
cd android
./gradlew assembleDebug        # android/app/build/outputs/apk/debug/
# ou assembleRelease (keystore : voir la CI)
```
Ouvres simplement `android/` dans Android Studio (Iguana ou plus récent).

### Windows
```bash
cd windows
npm install
npm start                      # developpement
npm run dist:portable          # exe portable dans dist-electron/
```

## CI / CD — releases automatiques

**Chaque push sur `main` compile et publie une GitHub Release** (tag `build-<numéro du run>`),
avec les binaires en pièces jointes. Plus besoin de créer un tag à la main.

| Workflow | Fichier | Ce qu'il publie |
|----------|---------|-----------------|
| **Android · compilation de l'APK** | `.github/workflows/android.yml` | APK debug + release (signée si secrets) |
| **Windows · application portable** | `.github/workflows/windows.yml` | exe Windows portable |
| *(manuel)* | `workflow_dispatch` | relance un build complet |

La chaîne Gradle est générée en CI (`gradle wrapper --gradle-version 8.9`) car le JAR du
wrapper n'est pas versionné. L'APK release n'est signée qu'avec les secrets
`PLIMB_KEYSTORE` / `PLIMB_STORE_PASSWORD` / `PLIMB_KEY_ALIAS` (sinon : clé de test).

## Système d'extensions

### Android (WebExtensions GeckoView)
Chaque extension intégrée est un dossier avec un `manifest.json` standard
(`manifest_version`, `content_scripts`, `browser_specific_settings.gecko.id`) :

```
mon-extension/
├── manifest.json
├── background.js     # scripts de fond (MV2)
└── contenu.js        # scripts injectes
```

Dans `assets/extensions/` : `blocker/` (blocage réseau via `webRequest`), `dark-theme/`,
`cookie-banner/`. Le menu **Extensions** liste les add-ons installés, permet
d'activer/désactiver/supprimer, et donne accès au catalogue Mozilla.

### Windows (extensions Chrome)
Même principe, format Chrome. Le gestionnaire permet en plus :
- **Dossier non empaqueté** (mode développeur Chrome) ;
- **Fichier `.crx`** — dépaqueté par `src/crx.js` (en-tête CRX2/3 puis extraction ZIP) ;
- **Chrome Web Store** — ID (32 caractères) ou URL de la fiche ; le `.crx` signé est
  téléchargé, dépaqueté, puis chargé.

Les extensions installées manuellement vivent dans `%APPDATA%/plimb-browser/extensions/`.

## Bloqueur de publicité

Filtrage **avant** le chargement de la requête, sur l'hôte demandé :

- Android : extension intégrée `extensions/blocker/background.js` (`browser.webRequest`)
- Windows : `windows/blocklist.txt` + `session.webRequest` dans `src/main.js`

Même liste des deux côtés (~95 régies publicitaires, réseaux mobile, analytics, pop-under).

## Structure du dépôt

```
plimb-browser/
├── android/                         # app Android — moteur GeckoView (Firefox)
│   ├── app/src/main/
│   │   ├── assets/                  # home.html, extensions/ (WebExtensions integrees)
│   │   └── java/dev/kurodaki/plimb/ # MainActivity, AddonManager, AmoClient, …
├── windows/                         # app Windows — Electron/Chromium
│   ├── src/                         # main.js, preload.js, renderer.js, crx.js
│   ├── extensions/                  # extensions Chrome integrees (MV2)
│   └── blocklist.txt
├── .github/workflows/               # CI : build + release automatique
├── LICENSE                          # MIT © 2026 Kurodaki
└── README.md
```

## Feuille de route

- [ ] Android : fork Chromium (Kiwi/Kiwi-Next) pour accéder au **Chrome** Web Store —
      projet séparé, multi-Go, heures de compilation
- [ ] Windows : shim de compatibilité `declarativeNetRequest` pour les bloqueurs MV3
- [ ] Import d'une liste EasyList complète via URL
- [ ] Synchronisation des onglets Android ↔ Windows
- [ ] Version Linux (AppImage) via le même socle Electron

## Contribuer

Forke, crée une branche `feat/…`, commit `feat: …`, ouvre une Pull Request.

## Licence

MIT © 2026 Kurodaki — voir [LICENSE](./LICENSE).
