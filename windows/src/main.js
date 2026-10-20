'use strict';

/**
 * Plimb Browser (Windows) — processus principal Electron.
 * Gere la fenetre, les extensions, le blocage reseau et les telechargements.
 */

const { app, BrowserWindow, ipcMain, session, shell } = require('electron');
const path = require('path');
const fs = require('fs');

const BUILTIN_BLOCKLIST = [
  'doubleclick.net',
  '2mdn.net',
  'googleadservices.com',
  'googlesyndication.com',
  'googletagservices.com',
  'googletagmanager.com',
  'google-analytics.com',
  'adservice.google.com',
  'ads.google.com',
  'adnxs.com',
  'adform.net',
  'adsrvr.org',
  'pubmatic.com',
  'rubiconproject.com',
  'openx.net',
  'smartadserver.com',
  'indexww.com',
  'casalemedia.com',
  'sharethrough.com',
  'triplelift.com',
  'districtm.io',
  'sonobi.com',
  'gumgum.com',
  'media.net',
  'bidswitch.net',
  'spotxchange.com',
  'spotx.tv',
  'aniview.com',
  'criteo.com',
  'criteo.net',
  'taboola.com',
  'outbrain.com',
  'mgid.com',
  'revcontent.com',
  'adcolony.com',
  'applovin.com',
  'chartboost.com',
  'vungle.com',
  'inmobi.com',
  'startapp.com',
  'mopub.com',
  'unityads.unity3d.com',
  'appsflyer.com',
  'adjust.com',
  'kochava.com',
  'branch.io',
  'amazon-adsystem.com',
  'bat.bing.com',
  'scorecardresearch.com',
  'quantserve.com',
  'chartbeat.com',
  'amplitude.com',
  'mixpanel.com',
  'segment.com',
  'segment.io',
  'optimizely.com',
  'vwo.com',
  'abtasty.com',
  'hotjar.com',
  'mouseflow.com',
  'crazyegg.com',
  'fullstory.com',
  'clarity.ms',
  'popads.net',
  'popcash.net',
  'propellerads.com',
  'adsterra.com',
  'exoclick.com',
  'juicyads.com',
  'trafficstars.com',
  'clickadu.com',
  'adcash.com',
  'ads.tiktok.com',
  'analytics.tiktok.com',
  'an.yandex.ru',
  'ads.yandex.ru',
  'addthis.com',
  'sharethis.com'
];

const BUILTIN_EXTENSIONS_DIR = path.join(__dirname, '..', 'extensions');
const USER_BLOCKLIST_FILE = () => path.join(app.getPath('userData'), 'blocklist.txt');
const USER_EXTENSIONS_DIR = () => path.join(app.getPath('userData'), 'extensions');

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

function start() {
  loadBlocklist();

  try {
    fs.mkdirSync(USER_EXTENSIONS_DIR(), { recursive: true });
  } catch (error) {
    console.warn('Plimb: dossier extensions utilisateur inaccessible', error.message);
  }

  const currentSession = session.defaultSession;

  // Meme liste de blocage que sur Android (voir ../README.md).
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
      payload.state = 'progressing';
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

  app.on('web-contents-created', (event, contents) => {
    if (contents.getType() !== 'webview') return;
    contents.setWindowOpenHandler(({ url }) => {
      sendToRenderer('plimb:open-url', url);
      return { action: 'deny' };
    });
  });

  registerIpc();
}

function registerIpc() {
  ipcMain.handle('plimb:app-version', () => app.getVersion());
  ipcMain.handle('plimb:list-extensions', () => listExtensions());
  ipcMain.handle('plimb:read-script', async (event, id, file) => {
    const extension = listExtensions().find((item) => item.id === id);
    if (!extension || typeof file !== 'string') return null;
    const filePath = path.join(extension.dir, file);
    if (!filePath.startsWith(extension.dir + path.sep)) return null;
    if (!fs.existsSync(filePath)) return null;
    return fs.readFileSync(filePath, 'utf8');
  });
  ipcMain.handle('plimb:blocklist', () => Array.from(blocklist));
  ipcMain.handle('plimb:open-external', async (event, url) => {
    if (typeof url === 'string' && /^https?:\/\//i.test(url)) {
      await shell.openExternal(url);
    }
  });
}

function listExtensions() {
  const result = [];
  const seen = new Set();
  const sources = [
    { dir: BUILTIN_EXTENSIONS_DIR, bundled: true },
    { dir: USER_EXTENSIONS_DIR(), bundled: false }
  ];

  for (const source of sources) {
    if (!source.dir || !fs.existsSync(source.dir)) continue;
    let entries = [];
    try {
      entries = fs.readdirSync(source.dir, { withFileTypes: true });
    } catch (error) {
      continue;
    }
    for (const entry of entries) {
      if (!entry.isDirectory()) continue;
      const extensionDir = path.join(source.dir, entry.name);
      const manifestPath = path.join(extensionDir, 'manifest.json');
      if (!fs.existsSync(manifestPath)) continue;
      try {
        const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
        if (!manifest.id || seen.has(manifest.id)) continue;
        seen.add(manifest.id);
        result.push({
          id: manifest.id,
          name: manifest.name || manifest.id,
          version: manifest.version || '1.0.0',
          description: manifest.description || '',
          author: manifest.author || '',
          matches: Array.isArray(manifest.matches) ? manifest.matches : [],
          js: Array.isArray(manifest.js) ? manifest.js : [],
          dir: extensionDir,
          bundled: source.bundled
        });
      } catch (error) {
        console.warn('Plimb: manifest illisible', manifestPath, error.message);
      }
    }
  }

  result.sort((a, b) => a.name.localeCompare(b.name));
  return result;
}

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
