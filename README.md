# Plimb Browser

<p align="center">
  <b>Plimb Browser — navigateur web open source, base Firefox, avec de VRAIES extensions, pour Android et Windows.</b><br/>
  Par <a href="https://github.com/kurodaki-dev">@kurodaki-dev</a> · sous licence MIT
</p>

---

## Un seul moteur : Firefox

Les deux plateformes partagent désormais le **même moteur (Firefox/Gecko)** et le
**même système d'extensions (WebExtensions)** :

| Plateforme | Base | Système d'extensions | Store d'extensions |
|------------|------|----------------------|---------------------|
| **Android** (8.0+) | **GeckoView** (bibliothèque Firefox embarquée) | WebExtensions MV2/MV3 | **addons.mozilla.org** (catalogue intégré à l'app) + `.xpi` + URL |
| **Windows** (x64) | **Firefox portable** (distribution + profil Plimb) | WebExtensions MV2/MV3 | **addons.mozilla.org** (préinstallé) + `.xpi` + `about:addons` |

### Pourquoi ce choix

- **Chromium ne permet pas les extensions sur Android.** Le `WebView` système
  n'expose aucune API d'extension : impossible d'avoir `chrome://extensions` ni le
  Chrome Web Store (même Chrome pour Android ne supporte pas les extensions). Les
  rares navigateurs Android qui y arrivent (Kiwi, Lemur) sont des **forks complets
  de Chromium** — 15+ Go de sources et plusieurs heures de compilation C++. On ne
  les bat pas sur ce terrain.
- **GeckoView est la contrepartie officielle** : le moteur Firefox embarqué, avec
  un vrai système WebExtensions. Même format, même store, des deux côtés.
- **Firefox n'a pas d'équivalent desktop embarquable** (XULRunner est mort depuis
  2015). La version Windows est donc une **distribution Firefox portable** : le
  Firefox de Mozilla, plus un profil Plimb, nos extensions et des add-ons
  préinstallés. Ce n'est pas un Firefox compilé depuis les sources par nous —
  voir « Limites honnêtes » plus bas.

> ℹ️ **Ce qu'on perd au passage** : le **Chrome Web Store** n'est pas accessible
> (une extension Chrome ne s'installe pas dans Firefox). C'est le prix de
> l'unification des deux plateformes.

## Ce que fait Plimb Browser

**Commun**
- Onglets multiples, réouverture des liens dans un onglet, navigation arrière/avant
- Barre d'adresse intelligente (URL ou recherche) — DuckDuckGo, Google, Bing, Ecosia
- Bloqueur de publicité et de traqueurs (uBlock Origin / blocage par hôtes)
- Mode sombre, gestion des extensions activable, **aucune télémétrie**

**Android (application maison autour de GeckoView)**
- Catalogue d'add-ons Mozilla **intégré** : recherche + installation en un tap
- Installation depuis un fichier `.xpi` ou une URL
- Extensions intégrées : **Plimb Bloqueur** (blocage réseau via `webRequest`),
  **Mode sombre universel**, **Anti bannières cookies**
- Téléchargements (MediaStore), envoi de fichiers dans les formulaires, plein écran,
  permissions caméra/micro/géolocalisation, restauration des onglets
- Intents `ACTION_VIEW` / `ACTION_SEND` : plimb peut ouvrir les liens d'autres apps

**Windows (distribution Firefox portable)**
- Aucune installation : on dézippe, on double-clique `Plimb Browser.cmd`
- **uBlock Origin** et **Dark Reader** préinstallés (politiques d'entreprise)
- Nos 3 extensions Plimb intégrées au profil (mode sombre, bannières cookies,
  Picture-in-Picture)
- Télémétrie et mises à jour automatique coupées : le profil est dans le dossier

## Compiler soi-même

### Android
```bash
cd android
./gradlew assembleDebug        # android/app/build/outputs/apk/debug/
```
Ouvres simplement `android/` dans Android Studio (Iguana ou plus récent).

### Windows
```powershell
pwsh -File windows\build.ps1
```
Aucune dépendance npm : PowerShell 5.1+ suffit. Le script télécharge Firefox,
assemble la distribution et produit `windows/dist-electron/Plimb-Browser-Windows-Portable.zip`.

## CI / CD — release auto à chaque build

**Chaque push sur `main` compile et publie une GitHub Release** (tag `build-<n°>`),
avant les binaires en pièces jointes. Plus de tag manuel.

| Workflow | Fichier | Publié |
|----------|---------|--------|
| **Android · APK** | `.github/workflows/android.yml` | APK debug + release |
| **Windows · Firefox portable** | `.github/workflows/windows.yml` | `Plimb-Browser-Windows-Portable.zip` |
| *(manuel)* | `workflow_dispatch` | relance un build complet |

## Système d'extensions

Une extension = un dossier avec un `manifest.json` standard :

```
mon-extension/
├── manifest.json      # manifest_version, content_scripts, browser_specific_settings.gecko.id
├── background.js      # script de fond (MV2)
└── contenu.js         # scripts injectes
```

- **Android** : les extensions intégrées sont dans `android/app/src/main/assets/extensions/`
  (`blocker`, `dark-theme`, `cookie-banner`), chargées au démarrage via
  `ensureBuiltIn`. Le menu **Extensions** permet d'activer/désactiver/supprimer
  et ouvre le **catalogue Mozilla** (`AmoClient` interroge l'API AMO v5).
- **Windows** : les sources sont dans `windows/extensions/` ; `build.ps1` les
  compile en `.xpi` et les enregistre dans le profil (`extensions.json`).
  La gestion se fait ensuite dans `about:addons`.

Les deux côtés partagent la même spécification : une extension écrite pour l'un
fonctionne sur l'autre (sauf spécificités GeckoView, comme les permissions
système).

## Bloqueur de publicité

- **Android** : extension intégrée `blocker/background.js` — blocage réseau via
  `browser.webRequest.onBeforeRequest`, ~95 domaines (régies, réseaux mobile,
  analytics, pop-under). La liste est dans ce fichier.
- **Windows** : **uBlock Origin** (préinstallé) + la protection contre le pistage
  native de Firefox, activée dans `profile/user.js`.

## Structure du dépôt

```
plimb-browser/
├── android/                          # app Android — moteur GeckoView
│   ├── app/src/main/
│   │   ├── assets/                   # home.html, extensions/ (WebExtensions)
│   │   └── java/dev/kurodaki/plimb/  # MainActivity, AddonManager, AmoClient…
├── windows/                          # distribution Firefox portable
│   ├── build.ps1                    # télécharge Firefox, assemble, zippe
│   ├── Plimb Browser.cmd            # lanceur
│   ├── distribution/policies.json   # add-ons AMO, télémétrie coupée
│   ├── profile/user.js              # profil autonome
│   └── extensions/                  # nos 3 WebExtensions (sources)
├── .github/workflows/               # build + release automatique
├── LICENSE                          # MIT © 2026 Kurodaki
└── README.md
```

## Limites honnêtes

- **Android** : seuls les add-ons **signés par Mozilla** s'installent via
  `install()` ; Firefox pour Android est encore très MV2 ; certains add-ons de
  bureau sont signalés incompatibles (normal, c'est mobile).
- **Windows** : c'est le Firefox de Mozilla avec une config Plimb — pas un
  Firefox compilé par nous. Le nom et l'icône restent ceux de Firefox. Nos
  extensions ne sont pas signées par Mozilla : si Firefox refuse un jour le
  chargement local, uBlock Origin + Dark Reader (installés par politique,
  donc signés) assurent l'essentiel.
- **Chromium / Chrome Web Store** : abandonné ici, pour la raison expliquée plus
  haut. Un éventuel build Chromium serait un projet séparé.

## Feuille de route

- [ ] Signature des extensions Plimb par Mozilla (AMO) + auto-install par politique
- [ ] Page « nouvel onglet » Plimb personnalisée (`userChrome.css` + `browser.newtab.url`)
- [ ] Icône et nom « Plimb » dans la barre de titre (repack du thème Firefox)
- [ ] Synchronisation des onglets Android ↔ Windows
- [ ] Version Linux (AppImage Firefox) via le même script
- [ ] Import d'une liste EasyList complète

## Contribuer

Forke, branche `feat/…`, commit `feat: …`, ouvre une Pull Request.

## Licence

MIT © 2026 Kurodaki — voir [LICENSE](./LICENSE).
