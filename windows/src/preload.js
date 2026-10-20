'use strict';

/**
 * Preload du renderer (chrome de l application) : expose une API bornee
 * au monde JS de la page via contextBridge.
 */

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('plimb', {
  appVersion: () => ipcRenderer.invoke('plimb:app-version'),
  listExtensions: () => ipcRenderer.invoke('plimb:list-extensions'),
  readScript: (id, file) => ipcRenderer.invoke('plimb:read-script', id, file),
  blocklist: () => ipcRenderer.invoke('plimb:blocklist'),
  openExternal: (url) => ipcRenderer.invoke('plimb:open-external', url),
  extensionsFolder: () => ipcRenderer.invoke('plimb:open-external', 'about:blank'),
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
