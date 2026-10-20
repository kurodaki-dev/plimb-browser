# Plimb Browser — Windows (Firefox portable)

Cette version Windows de Plimb Browser est une **distribution Firefox portable** :
le moteur **Firefox** de Mozilla, livré avec un profil Plimb, nos extensions
WebExtensions et des add-ons Mozilla préinstallés. Aucune installation, aucune
écriture en dehors du dossier : le profil est autonome.

## Pourquoi Firefox et pas Chromium ?

Pour avoir **le même système d'extensions que l'app Android** (WebExtensions,
`addons.mozilla.org`). Firefox ne fournit pas de moteur embarquable pour Windows
(XULRunner est abandonné depuis 2015) : on distribue donc Firefox avec sa
configuration « entreprise » (`distribution/policies.json`).

## Ce qui est inclus

| Élément | Rôle |
|---------|------|
| `distribution/policies.json` | Préinstalle **uBlock Origin** et **Dark Reader** depuis AMO, coupe la télémétrie, page d'accueil DuckDuckGo |
| `profile/user.js` | Profil portable : pas de télémétrie, pas de mise à jour auto, autorise les extensions non signées |
| `profile/extensions/*.xpi` | Nos 3 extensions Plimb (mode sombre, bannières cookies, Picture-in-Picture) compilées à la volée |
| `profile/extensions.json` | Registre qui active nos extensions au démarrage |
| `Plimb Browser.cmd` | Lanceur : `firefox.exe -profile .../profile -no-remote` |

## Construire soi-même

```powershell
pwsh -File windows\build.ps1
```

Le script ne demande que PowerShell 5.1+ (aucun `npm install`). Il produit :

```
windows/dist-electron/Plimb-Browser-Windows-Portable.zip
```

Dézippe où tu veux, double-clique `Plimb Browser.cmd`. Le navigateur est
`about:addons` pour gérer les extensions (le gestionnaire natif de Firefox).

## Structure

```
windows/
├── build.ps1                 # script de build (télécharge Firefox, assemble, zippe)
├── Plimb Browser.cmd         # lanceur portable
├── distribution/policies.json
├── profile/user.js
├── extensions/               # sources de nos WebExtensions (format Firefox)
│   ├── dark-theme/
│   ├── cookie-banner/
│   └── popout-video/
└── build-output/             # généré : l'arborescence Firefox + Plimb
```

## Limites honnêtes

- Le moteur est **Firefox de Mozilla**, pas un Firefox compilé par nous :
  le nom et l'icône restent ceux de Firefox dans la barre de titre.
- Les extensions Chrome du Chrome Web Store ne s'installent pas ici (c'est
  `addons.mozilla.org`, comme sur Android).
- Nos propres extensions ne sont **pas signées** par Mozilla : si un jour
  Firefox refuse le chargement local, les add-ons AMO préinstallés
  (uBlock Origin, Dark Reader) assurent l'essentiel.
