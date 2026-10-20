'use strict';

/**
 * Preload du renderer : expose une API bornee au monde JS de la page
 * (gestion des extensions, telechargements, preferences).
 */

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('plimb', {
  appVersion: () => ipcRenderer.invoke('plimb:app-version'),
  blocklist: () => ipcRenderer.invoke('plimb:blocklist'),
  openExternal: (url) => ipcRenderer.invoke('plimb:open-external', url),

  listExtensions: () => ipcRenderer.invoke('plimb:extensions-list'),
  setExtensionEnabled: (folder, enabled) =>
    ipcRenderer.invoke('plimb:extensions-set-enabled', folder, enabled),
  removeExtension: (folder) => ipcRenderer.invoke('plimb:extensions-remove', folder),
  installUnpackedExtension: () => ipcRenderer.invoke('plimb:extensions-install-unpacked'),
  installCrxExtension: () => ipcRenderer.invoke('plimb:extensions-install-crx'),
  installWebStoreExtension: (input) =>
    ipcRenderer.invoke('plimb:extensions-install-webstore', input),
  openExtensionsFolder: () => ipcRenderer.invoke('plimb:extensions-folder'),

  onOpenUrl: (callback) => {
    ipcRenderer.on('plimb:open-url', (event, url) => callback(url));
  },
  onDownloadProgress: (callback) => {
    ipcRenderer.on('plimb:download-progress', (event, data) => callback(data));
  },
  onDownloadDone: (callback) => {
    ipcRenderer.on('plimb:download-done', (event, data) => callback(data));
  }
});
