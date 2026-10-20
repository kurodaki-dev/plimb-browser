'use strict';

/**
 * Plimp Browser (Windows) — processus principal Electron.
 *
 * Base Chromium (Electron 44) avec DE VRAIES extensions Chrome : l API officielle
 * session.extensions.* permet de charger des extensions decompressees (.crx,
 * dossiers, telechargement depuis le Chrome Web Store). Voir README pour les limites.
 */

const { app, BrowserWindow, ipcMain, session, shell, dialog } = require('electron');
const path = require('node:path');
const fs = require('node:fs');
const { unpackCrxSync, readManifest } = require('./crx.js');

const BUILTIN_BLOCKLIST = [
  'doubleclick.net', '2mdn.net', 'googleadservices.com', 'googlesyndication.com',
  'googletagservices.com', 'googletagmanager.com', 'google-analytics.com',
  'adservice.google.com', 'ads.google.com', 'adnxs.com', 'adform.net', 'adsrvr.org',
  'pubmatic.com', 'rubiconproject.com', 'openx.net', 'smartadserver.com',
  'indexww.com', 'casalemedia.com', 'sharethrough.com', 'triplelift.com',
  'districtm.io', 'sonobi.com', 'gumgum.com', 'media.net', 'bidswitch.net',
  'spotxchange.com', 'spotx.tv', 'aniview.com', 'criteo.com', 'criteo.net',
  'taboola.com', 'outbrain.com', 'mgid.com', 'revcontent.com', 'adcolony.com',
  'applovin.com', 'chartboost.com', 'vungle.com', 'inmobi.com', 'startapp.com',
  'mopub.com', 'unityads.unity3d.com', 'appsflyer.com', 'adjust.com', 'kochava.com',
  'branch.io', 'amazon-adsystem.com', 'bat.bing.com', 'scorecardresearch.com',
  'quantserve.com', 'chartbeat.com', 'amplitude.com', 'mixpanel.com', 'segment.com',
  'segment.io', 'optimizely.com', 'vwo.com', 'abtasty.com', 'hotjar.com',
  'mouseflow.com', 'crazyegg.com', 'fullstory.com', 'clarity.ms', 'popads.net',
  'popcash.net', 'propellerads.com', 'adsterra.com', 'exoclick.com', 'juicyads.com',
  'trafficstars.com', 'clickadu.com', 'adcash.com', 'ads.tiktok.com',
  'analytics.tiktok.com', 'an.yandex.ru', 'ads.yandex.ru', 'addthis.com', 'sharethis.com'
];

const BUILTIN_EXTENSIONS_DIR = path.join(__dirname, '..', 'extensions');
const USER_EXTENSIONS_DIR = () => path.join(app.getPath('userData'), 'extensions');
const EXTENSIONS_STATE_FILE = () => path.join(app.getPath('userData'), 'extensions.json');
const USER_BLOCKLIST_FILE = () => path.join(app.getPath('userData'), 'blocklist.txt');

// Meme technique que Chrome : telechargement d un paquet signe depuis le Web Store.
const WEBSTORE_CRX_URL =
  'https://clients2.google.com/service/update2/crx' +
  '?response=redirect&prodversion=141.0.0.0&acceptformat=crx2,crx3,crx5&x=id%3D';

let mainWindow = null;
let blocklist = new Set();
let blockedPatterns = [];

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (!mainWindow) return;
    if (mainWindow.isMinimized()) mainWindow.restore();
    mainWindow.show();
    mainWindow.focus();
  });

  app.whenReady().then(start);

  app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit();
  });

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) start();
  });
}

// ------------------------------------------------------------------- demarrage

function start() {
  loadBlocklist();

  try {
    fs.mkdirSync(USER_EXTENSIONS_DIR(), { recursive: true });
  } catch (error) {
    console.warn('Plimb: dossier extensions utilisateur inaccessible', error.message);
  }

  const currentSession = session.defaultSession;

  // Blocage reseau (meme liste que l extension Android).
  currentSession.webRequest.onBeforeRequest({ urls: ['*://*/*'] }, (details, callback) => {
    let host = null;
    try {
      host = new URL(details.url).hostname;
    } catch (error) {
      host = null;
    }
    if (host && isHostBlocked(host)) {
      return callback({ cancel: true });
    }
    callback({});
  });

  currentSession.on('will-download', (event, item) => {
    const payload = {
      name: item.getFilename(),
      received: item.getReceivedBytes(),
      total: item.getTotalBytes(),
      state: 'progressing',
      path: item.getSavePath()
    };
    item.on('updated', () => {
      payload.received = item.getReceivedBytes();
      sendToRenderer('plimb:download-progress', payload);
    });
    item.once('done', () => {
      payload.received = item.getReceivedBytes();
      payload.state = 'completed';
      payload.path = item.getSavePath();
      sendToRenderer('plimb:download-done', payload);
    });
  });

  mainWindow = new BrowserWindow({
    width: 1360,
    height: 860,
    minWidth: 760,
    minHeight: 500,
    backgroundColor: '#0b1220',
    title: 'Plimb Browser',
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  });

  mainWindow.once('ready-to-show', () => mainWindow.show());
  mainWindow.setMenuBarVisibility(false);
  mainWindow.loadFile(path.join(__dirname, 'index.html'));

  // Les extensions sont chargees dans la session par defaut : les <webview>
  // doivent donc utiliser la session par defaut (pas de partition).
  app.on('web-contents-created', (event, contents) => {
    if (contents.getType() !== 'webview') return;
    contents.setWindowOpenHandler(({ url }) => {
      sendToRenderer('plimb:open-url', url);
      return { action: 'deny' };
    });
  });

  registerIpc();
  loadAllExtensions();
}

// --------------------------------------------------------------- extensions

function readExtensionState() {
  try {
    const parsed = JSON.parse(fs.readFileSync(EXTENSIONS_STATE_FILE(), 'utf8'));
    if (Array.isArray(parsed.extensions)) return parsed;
  } catch (error) {
    /* premier lancement */
  }
  return { extensions: [] };
}

function writeExtensionState(state) {
  fs.writeFileSync(EXTENSIONS_STATE_FILE(), JSON.stringify(state, null, 2));
}

function entryFolder(entry) {
  return entry.builtIn
    ? path.join(BUILTIN_EXTENSIONS_DIR, entry.folder)
    : path.join(USER_EXTENSIONS_DIR(), entry.folder);
}

function scanExtensionFolder(folder, builtIn) {
  const manifest = readManifest(folder);
  if (!manifest || !manifest.name) return null;
  return {
    folder: path.basename(folder),
    id: null,
    name: manifest.name || path.basename(folder),
    version: manifest.version || '',
    description: manifest.description || '',
    enabled: true,
    builtIn
  };
}

/** Enregistre toute extension trouvee dans les dossiers integres / utilisateur. */
function discoverExtensions() {
  const state = readExtensionState();
  const known = new Set(state.extensions.map((entry) => entry.folder));
  let changed = false;

  const sources = [
    { dir: BUILTIN_EXTENSIONS_DIR, builtIn: true },
    { dir: USER_EXTENSIONS_DIR(), builtIn: false }
  ];

  for (const source of sources) {
    if (!fs.existsSync(source.dir)) continue;
    let names = [];
    try {
      names = fs.readdirSync(source.dir, { withFileTypes: true })
        .filter((item) => item.isDirectory())
        .map((item) => item.name);
    } catch (error) {
      continue;
    }
    for (const name of names) {
      const entry = scanExtensionFolder(path.join(source.dir, name), source.builtIn);
      if (entry && !known.has(entry.folder)) {
        state.extensions.push(entry);
        changed = true;
      }
    }
  }

  if (changed) writeExtensionState(state);
  return state;
}

async function loadExtensionEntry(entry) {
  const location = entryFolder(entry);
  if (!fs.existsSync(location)) {
    console.warn('Plimb: dossier extension absent', location);
    return null;
  }
  try {
    const loaded = await session.defaultSession.extensions.loadExtension(location, {
      allowFileAccess: true
    });
    return loaded && loaded.id ? loaded.id : null;
  } catch (error) {
    console.warn('Plimb: extension non chargee', entry.folder, error.message);
    return null;
  }
}

async function loadAllExtensions() {
  const state = discoverExtensions();
  for (const entry of state.extensions) {
    if (!entry.enabled) continue;
    const id = await loadExtensionEntry(entry);
    if (id && entry.id !== id) {
      entry.id = id;
    }
  }
  writeExtensionState(state);
}

function listInstalledExtensions() {
  const state = readExtensionState();
  const loaded = new Map();
  try {
    for (const ext of session.defaultSession.extensions.getAllExtensions()) {
      loaded.set(ext.id, ext);
    }
  } catch (error) {
    /* API indisponible sur cette version */
  }
  return state.extensions.map((entry) => {
    const ext = loaded.get(entry.id);
    return {
      folder: entry.folder,
      id: entry.id,
      name: ext ? ext.name : entry.name || entry.folder,
      version: ext ? ext.version : entry.version || '',
      description: ext ? ext.description || '' : entry.description || '',
      // Une extension non listee par getAllExtensions est reputee incomplete/incompatible.
      warnings: ext ? [] : ['Extension non compatible avec cette version de Chromium.'],
      enabled: entry.enabled,
      loaded: loaded.has(entry.id),
      builtIn: !!entry.builtIn
    };
  });
}

async function setExtensionEnabled(folder, enabled) {
  const state = readExtensionState();
  const entry = state.extensions.find((item) => item.folder === folder);
  if (!entry) return listInstalledExtensions();

  entry.enabled = !!enabled;
  writeExtensionState(state);

  if (entry.enabled) {
    const id = await loadExtensionEntry(entry);
    if (id) entry.id = id;
    writeExtensionState(state);
  } else if (entry.id) {
    try {
      session.defaultSession.extensions.removeExtension(entry.id);
    } catch (error) {
      console.warn('Plimb: desactivation impossible', entry.folder, error.message);
    }
  }
  return listInstalledExtensions();
}

async function removeExtension(folder) {
  const state = readExtensionState();
  const entry = state.extensions.find((item) => item.folder === folder);
  state.extensions = state.extensions.filter((item) => item.folder !== folder);
  writeExtensionState(state);

  if (entry) {
    if (entry.id) {
      try {
        session.defaultSession.extensions.removeExtension(entry.id);
      } catch (error) {
        /* deja dechargee */
      }
    }
    if (!entry.builtIn) {
      fs.rmSync(entryFolder(entry), { recursive: true, force: true });
    }
  }
  return listInstalledExtensions();
}

async function registerExtension(folderName, builtIn) {
  const state = readExtensionState();
  if (state.extensions.some((item) => item.folder === folderName)) {
    return listInstalledExtensions();
  }
  const location = path.join(USER_EXTENSIONS_DIR(), folderName);
  const manifest = readManifest(location);
  if (!manifest) {
    throw new Error('manifest.json introuvable dans le dossier choisi.');
  }
  const entry = {
    folder: folderName,
    id: null,
    name: manifest.name || folderName,
    version: manifest.version || '',
    description: manifest.description || '',
    enabled: true,
    builtIn: !!builtIn
  };
  state.extensions.push(entry);
  writeExtensionState(state);
  const id = await loadExtensionEntry(entry);
  if (id) entry.id = id;
  writeExtensionState(state);
  return listInstalledExtensions();
}

async function installUnpackedFolder(folderPath) {
  const manifest = readManifest(folderPath);
  if (!manifest) {
    throw new Error('Ce dossier ne contient pas de manifest.json d extension Chrome.');
  }
  const folderName = `local-${Date.now().toString(36)}`;
  const target = path.join(USER_EXTENSIONS_DIR(), folderName);
  fs.cpSync(folderPath, target, { recursive: true });
  return registerExtension(folderName, false);
}

async function installCrxFile(crxPath) {
  const folderName = path.basename(crxPath, '.crx').replace(/[^a-zA-Z0-9_-]/g, '-') ||
    `crx-${Date.now().toString(36)}`;
  const target = path.join(USER_EXTENSIONS_DIR(), folderName);
  unpackCrxSync(crxPath, target);
  return registerExtension(folderName, false);
}

/** Identifiant d extension Chrome a partir d une URL du Web Store ou d un ID brut. */
function extractExtensionId(input) {
  const text = (input || '').trim();
  const direct = /^[a-p]{32}$/.exec(text);
  if (direct) return direct[0];
  const fromUrl = /\/([a-p]{32})(?:\?|$)/.exec(text);
  if (fromUrl) return fromUrl[1];
  return null;
}

async function installFromWebStore(input) {
  const id = extractExtensionId(input);
  if (!id) {
    throw new Error(
      'URL ou identifiant du Chrome Web Store invalide (32 lettres minuscules).'
    );
  }
  const response = await fetch(WEBSTORE_CRX_URL + encodeURIComponent(id) + '%26uc');
  if (!response.ok) {
    throw new Error(`Chrome Web Store indisponible (HTTP ${response.status}).`);
  }
  const buffer = Buffer.from(await response.arrayBuffer());
  const target = path.join(
    app.getPath('temp'),
    `plimb-cws-${id}-${Date.now().toString(36)}.crx`
  );
  fs.writeFileSync(target, buffer);
  try {
    const folderName = `cws-${id}`;
    unpackCrxSync(target, path.join(USER_EXTENSIONS_DIR(), folderName));
    return registerExtension(folderName, false);
  } finally {
    fs.rmSync(target, { force: true });
  }
}

// ------------------------------------------------------- IPC pour le rendu

function registerIpc() {
  ipcMain.handle('plimb:app-version', () => app.getVersion());
  ipcMain.handle('plimb:blocklist', () => Array.from(blocklist));
  ipcMain.handle('plimb:open-external', async (event, url) => {
    if (typeof url === 'string' && /^https?:\/\//i.test(url)) {
      await shell.openExternal(url);
    }
  });

  ipcMain.handle('plimb:extensions-list', () => listInstalledExtensions());

  ipcMain.handle('plimb:extensions-set-enabled', async (event, folder, enabled) =>
    setExtensionEnabled(folder, enabled)
  );

  ipcMain.handle('plimb:extensions-remove', async (event, folder) =>
    removeExtension(folder)
  );

  ipcMain.handle('plimb:extensions-install-unpacked', async () => {
    const result = await dialog.showOpenDialog(mainWindow, {
      title: 'Choisir le dossier d une extension Chrome (decompressee)',
      properties: ['openDirectory']
    });
    if (result.canceled || !result.filePaths[0]) {
      return { cancelled: true, extensions: listInstalledExtensions() };
    }
    const extensions = await installUnpackedFolder(result.filePaths[0]);
    return { cancelled: false, extensions };
  });

  ipcMain.handle('plimb:extensions-install-crx', async () => {
    const result = await dialog.showOpenDialog(mainWindow, {
      title: 'Choisir un fichier .crx',
      properties: ['openFile'],
      filters: [{ name: 'Extension Chrome', extensions: ['crx'] }]
    });
    if (result.canceled || !result.filePaths[0]) {
      return { cancelled: true, extensions: listInstalledExtensions() };
    }
    const extensions = await installCrxFile(result.filePaths[0]);
    return { cancelled: false, extensions };
  });

  ipcMain.handle('plimb:extensions-install-webstore', async (event, input) => {
    const extensions = await installFromWebStore(input);
    return { cancelled: false, extensions };
  });

  ipcMain.handle('plimb:extensions-folder', async () => {
    fs.mkdirSync(USER_EXTENSIONS_DIR(), { recursive: true });
    await shell.openPath(USER_EXTENSIONS_DIR());
    return USER_EXTENSIONS_DIR();
  });
}

// ------------------------------------------------------------ blocage reseau

function loadBlocklist() {
  const hosts = new Set(BUILTIN_BLOCKLIST);
  const userFile = USER_BLOCKLIST_FILE();
  if (fs.existsSync(userFile)) {
    try {
      fs.readFileSync(userFile, 'utf8').split(/\r?\n/).forEach((line) => {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith('#') || trimmed.startsWith('!')) return;
        const domain = trimmed.split(/\s+/).pop().toLowerCase().replace(/\.$/, '');
        if (domain && /^[a-z0-9.-]+$/.test(domain)) hosts.add(domain);
      });
    } catch (error) {
      console.warn('Plimb: blocklist utilisateur illisible', error.message);
    }
  }
  blocklist = hosts;
  blockedPatterns = Array.from(hosts).map((domain) =>
    new RegExp('(^|\\.)' + domain.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '$', 'i')
  );
  console.log('Plimb:', hosts.size, 'domaines bloqués');
}

function isHostBlocked(host) {
  return blockedPatterns.some((pattern) => pattern.test(host));
}

function sendToRenderer(channel, payload) {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send(channel, payload);
  }
}
