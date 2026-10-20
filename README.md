# Plimb Browser

<p align="center">
  <b>Plimb Browser — navigateur web open source, moteur Chromium, pour Android et Windows (version portable).</b><br/>
  Par <a href="https://github.com/kurodaki-dev">@kurodaki-dev</a> · sous licence MIT
</p>

---

## C'est quoi ?

Plimb Browser est un navigateur web moderne qui tourne sur deux plateformes :

| Plateforme | Base technique | Moteur | Système d'extensions |
|------------|----------------|--------|----------------------|
| **Android** (7.0+) | Kotlin + `androidx.webkit` / `WebView` | Chromium (moteur système) | scripts utilisateur (`.user.js`) + manifests JSON MV2 |
| **Windows** (x64 / ia32) | Electron + `<webview>` | Chromium (bundlé) | idem, même format de manifest |

Les deux plateformes partagent :
- le même **format d'extension** (manifest JSON + fichiers JS injectés),
- la même **liste de blocage** des publicités et traqueurs,
- les mêmes **moteurs de recherche** (DuckDuckGo, Google, Bing, Ecosia),
- la même identité visuelle (bleu `#2563eb` + orange `#f97316`).

## Fonctionnalités

**Commun**
- Onglets multiples, réouverture des liens, navigation arrière/avant
- Barre d'adresse intelligente (URL ou recherche)
- Bloqueur de publicité et de traqueurs (filtrage réseau par hôte)
- Système d'extensions activable/désactivable par extension
- Mode sombre, aucune télémétrie

**Android**
- Intent `ACTION_VIEW` (ouvrir un lien depuis une autre app) et `ACTION_SEND`
- Téléchargements via le `DownloadManager` avec notification
- Envoi de fichiers vers les formulaires (`<input type=file>`)
- Choix du moteur de recherche, mode « version ordinateur », activation de JavaScript
- Restauration des onglets au démarrage
- Pop-ups (`target=_blank`, `window.open`) ouverts dans un onglet

**Windows**
- Application portable : un seul `.exe`, rien à installer
- Téléchargements avec barre de progression
- Pop-ups ouverts dans un onglet
- Extensions ajoutables par l'utilisateur (dossier `extensions/` dans les données utilisateur)
- Raccourcis clavier (`Ctrl+T`, `Ctrl+W`, `Ctrl+L`, `Ctrl+R`, `Alt+←/→`)

## Compiler soi-même

### Android
```bash
cd android
./gradlew assembleDebug        # APK prête à installer
# ou assembleRelease (nécessite un keystore, voir la CI)
```
Ouvres simplement le dossier `android/` dans Android Studio (Hedgehog ou plus récent).

### Windows
```bash
cd windows
npm install
npm start                      # lancement en développement
npm run dist:portable          # construit l'exe portable dans dist-electron/
```

## CI / CD (GitHub Actions)

À chaque `push` sur `main`, deux workflows compilent le projet :

| Workflow | Fichier | Résultat |
|----------|---------|----------|
| **Android · compilation de l'APK** | `.github/workflows/android.yml` | artefact `plimb-browser-android-debug` (APK) |
| **Windows · application portable** | `.github/workflows/windows.yml` | artefact `plimb-browser-windows-portable` (`.exe`) |

Sur un tag `v*` (ex. `v1.0.0`), `.github/workflows/release.yml` recompile tout et publie une
**GitHub Release** avec l'APK Android et l'exe Windows portable.

```bash
git tag v1.0.0
git push origin v1.0.0
```

La compilation Android publie aussi une APK *release* signée si les secrets suivants existent :
`PLIMB_KEYSTORE` (keystore encodé en base64), `PLIMB_STORE_PASSWORD`, `PLIMB_KEY_ALIAS`.
Sans ces secrets, une APK release signée avec la clé de test est produite pour vérifier la chaîne.

## Système d'extensions

Chaque extension est un dossier :

```
mon-extension/
├── manifest.json
└── script.js
```

`manifest.json` :

```json
{
  "id": "mon-extension",
  "name": "Mon extension",
  "version": "1.0.0",
  "description": "Ce que fait l'extension",
  "author": "Plimb",
  "defaultEnabled": true,
  "matches": ["*://*/*"],
  "runAt": "document_start",
  "js": ["script.js"]
}
```

- `matches` : filtres au format Chrome (`*://*/*`, `https://*.wikipedia.org/*`, `<all_urls>`)
- `runAt` : `document_start` ou `document_end` (injection sur les deux plateformes)
- `js` : fichiers injectés dans la page, dans l'ordre

Android : extensions intégrées dans `android/app/src/main/assets/extensions/`, extensions utilisateur
dans `.../files/extensions/user/` (installables depuis une URL via le menu Extensions).
Windows : intégrées dans `windows/extensions/`, utilisateur dans `%APPDATA%/plimb-browser/extensions/`.

Extensions livrées avec le navigateur : **Mode sombre universel**, **Anti bannières cookies**
et, sur Windows, **Bouton Picture-in-Picture pour les vidéos**.

## Bloqueur de publicité

Le blocage se fait au niveau réseau (requête annulée avant chargement) en comparant l'hôte demandé
avec une liste :

- Android : `android/app/src/main/assets/adblock/hosts.txt`
- Windows : `windows/blocklist.txt`

Les deux fichiers ont le même contenu et peuvent être enrichis. Une liste perso peut aussi être ajoutée
(`blocklist.txt` dans les données de l'application).

## Structure du dépôt

```
plimb-browser/
├── android/                    # Application Android (Kotlin, Gradle)
│   ├── app/src/main/
│   │   ├── assets/             # home.html, extensions/, adblock/
│   │   └── java/dev/kurodaki/plimb/
│   └── build.gradle.kts
├── windows/                    # Application Windows (Electron)
│   ├── src/                    # main, preload, renderer
│   ├── extensions/             # extensions intégrées
│   └── package.json            # config electron-builder (portable)
├── .github/workflows/          # CI : APK + portable + release
├── LICENSE                     # MIT © 2026 Kurodaki
└── README.md
```

## Note technique sur « basé sur Chromium »

Plimb Browser utilise le **moteur Chromium** : sur Android via le `WebView` système
(qui est le composant Chromium officiel du système), sur Windows via Electron (Chromium empaqueté).
Même base de rendu que Chrome : compatibilité des standards web, WebRTC, WebGL, PWA, affichage des pages.

Compiler un **fork complet de Chromium** (dépôt `chromium/src`, plus de 15 Go, plusieurs heures de
`gclient sync` puis compilation) est un projet séparé : ce dépôt est autonome et compilable en quelques
minutes, tout en reposant sur le même moteur de rendu.

### Feuille de route

- [ ] Synchronisation des onglets entre Android et Windows
- [ ] Import d'une liste EasyList complète via URL
- [ ] Support des extensions `manifest v3`
- [ ] Version Linux (AppImage) via le même socle Electron

## Contribuer

1. Forke le dépôt
2. Crée ta branche : `git checkout -b feat/ma-fonctionnalite`
3. Commit : `git commit -m "feat: ..."`
4. Pousse et ouvre une Pull Request

## Licence

MIT © 2026 Kurodaki — voir [LICENSE](./LICENSE).
