'use strict';

/**
 * Rendu du navigateur : barre d onglets, barre d outils, injection des extensions.
 */

var views = document.getElementById('views');
var tabsElement = document.getElementById('tabs');
var urlInput = document.getElementById('url-input');
var menuElement = document.getElementById('menu');
var extListElement = document.getElementById('ext-list');
var downloadsElement = document.getElementById('downloads');
var blockedCountElement = document.getElementById('blocked-count');

var tabs = new Map();
var activeId = null;
var sequence = 0;
var extensions = [];

var SEARCH_ENGINES = {
  duckduckgo: 'https://duckduckgo.com/?q=',
  google: 'https://www.google.com/search?q=',
  bing: 'https://www.bing.com/search?q=',
  ecosia: 'https://www.ecosia.org/search?q='
};

var NEWTAB_PAGE =
  'data:text/html;charset=utf-8,' +
  encodeURIComponent(
    '<!DOCTYPE html><html lang="fr"><head><meta charset="utf-8"><title>Plimb Browser</title><style>' +
    'body{margin:0;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:18px;' +
    'font-family:system-ui,sans-serif;background:linear-gradient(160deg,#2563eb,#1e3a8a 45%,#0b1220);color:#e6edf7}' +
    'h1{margin:0;font-size:34px}h1 span{color:#f97316}' +
    'form{display:flex;width:min(620px,82vw)}input{flex:1;padding:14px;border:1px solid #334155;background:#101a2c;color:#fff;' +
    'border-radius:999px 0 0 999px;outline:none;font-size:15px}' +
    'button{padding:14px 20px;border:0;border-radius:0 999px 999px 0;background:#f97316;color:#fff;font-weight:700}' +
    '.links{display:flex;gap:16px;flex-wrap:wrap;justify-content:center}a{color:#9ec1ff;text-decoration:none;font-size:14px}' +
    '</style></head><body>' +
    '<h1>Plimb <span>Browser</span></h1>' +
    '<form id="q"><input id="term" placeholder="Adresse ou recherche" autofocus /><button>Go</button></form>' +
    '<div class="links">' +
    '<a href="https://duckduckgo.com">DuckDuckGo</a>' +
    '<a href="https://fr.wikipedia.org">Wikipédia</a>' +
    '<a href="https://www.youtube.com">YouTube</a>' +
    '<a href="https://github.com">GitHub</a>' +
    '<a href="https://www.reddit.com">Reddit</a>' +
    '</div>' +
    '<script>' +
    "document.getElementById('q').addEventListener('submit',function(e){e.preventDefault();" +
    "var v=document.getElementById('term').value.trim();if(!v)return;" +
    "var url=/^[^\\s]+\\.[^\\s]+$/.test(v)&&v.indexOf('://')===-1?'https://'+v:'https://duckduckgo.com/?q='+encodeURIComponent(v);" +
    'location.href=url;});' +
    '<\/script></body></html>'
  );

function store(key, value) {
  try {
    if (arguments.length === 1) {
      var raw = localStorage.getItem(key);
      return raw === null ? undefined : JSON.parse(raw);
    }
    localStorage.setItem(key, JSON.stringify(value));
  } catch (error) {
    return undefined;
  }
  return undefined;
}

function getPreference(key, fallback) {
  var value = store(key);
  return value === undefined ? fallback : value;
}

function createTab(url) {
  var id = ++sequence;
  var tab = { id: id, title: 'Nouvel onglet', url: '', loading: false, view: null };
  var view = document.createElement('webview');
  view.id = 'webview-' + id;
  view.setAttribute('src', url || NEWTAB_PAGE);
  view.setAttribute('allowpopups', '');
  view.setAttribute('partition', 'persist:plimb');
  view.setAttribute('webpreferences', 'contextIsolation=true, sandbox=true, nodeIntegration=false');
  view.style.cssText =
    'position:absolute;top:0;left:0;width:100%;height:100%;border:0;display:none';

  view.addEventListener('dom-ready', function () {
    injectExtensions(view);
  });
  view.addEventListener('page-title-updated', function (event) {
    tab.title = event.title || tab.title;
    renderTabs();
  });
  view.addEventListener('did-navigate', function (event) {
    tab.url = event.url;
    afterNavigation();
  });
  view.addEventListener('did-navigate-in-page', function (event) {
    tab.url = event.url;
    afterNavigation();
  });
  view.addEventListener('did-start-loading', function () {
    tab.loading = true;
    renderTabs();
  });
  view.addEventListener('did-stop-loading', function () {
    tab.loading = false;
    renderTabs();
    updateNav();
  });
  view.addEventListener('close', function () {
    closeTab(id);
  });

  views.appendChild(view);
  tab.view = view;
  tabs.set(id, tab);
  activateTab(id);
  renderTabs();
  return tab;
}

function activateTab(id) {
  if (!tabs.has(id)) return;
  activeId = id;
  tabs.forEach(function (tab) {
    tab.view.style.display = 'none';
  });
  var tab = tabs.get(id);
  tab.view.style.display = 'block';
  urlInput.value = tab.url || '';
  renderTabs();
  updateNav();
}

function closeTab(id) {
  var tab = tabs.get(id);
  if (!tab) return;
  tab.view.remove();
  tabs.delete(id);
  if (tabs.size === 0) {
    activeId = null;
    createTab();
  } else if (activeId === id) {
    var first = tabs.keys().next().value;
    activateTab(first);
  } else {
    renderTabs();
  }
}

function activeTab() {
  return activeId === null ? null : tabs.get(activeId);
}

function activeView() {
  var tab = activeTab();
  return tab ? tab.view : null;
}

function renderTabs() {
  tabsElement.innerHTML = '';
  tabs.forEach(function (tab) {
    var element = document.createElement('div');
    element.className = 'tab' + (tab.id === activeId ? ' active' : '') + (tab.loading ? ' loading' : '');

    var label = document.createElement('span');
    label.className = 'tab-title';
    label.textContent = tab.title || 'Nouvel onglet';

    var close = document.createElement('button');
    close.className = 'tab-close';
    close.textContent = '×';
    close.title = 'Fermer l\'onglet';
    close.addEventListener('click', function (event) {
      event.stopPropagation();
      closeTab(tab.id);
    });

    element.appendChild(label);
    element.appendChild(close);
    element.addEventListener('click', function () {
      activateTab(tab.id);
    });
    tabsElement.appendChild(element);
  });
}

function afterNavigation() {
  var tab = activeTab();
  if (tab) urlInput.value = tab.url;
  renderTabs();
  updateNav();
}

function updateNav() {
  var view = activeView();
  if (!view) return;
  try {
    Promise.resolve(view.canGoBack()).then(function (value) {
      document.getElementById('btn-back').disabled = !value;
    });
    Promise.resolve(view.canGoForward()).then(function (value) {
      document.getElementById('btn-forward').disabled = !value;
    });
  } catch (error) {
    /* anciennes versions d Electron */
  }
}

function navigate(view, url) {
  if (!view || !url) return;
  view.loadURL(url);
}

function normalizeInput(input) {
  var text = (input || '').trim();
  if (!text) return '';
  if (/^[a-z]+:\/\//i.test(text) || /^about:blank$/i.test(text)) return text;
  var looksLikeUrl = /^[^\s]+\.[^\s]+$/.test(text) && text.indexOf(' ') === -1;
  if (looksLikeUrl) return 'https://' + text;
  var engine = getPreference('engine', 'duckduckgo');
  var base = SEARCH_ENGINES[engine] || SEARCH_ENGINES.duckduckgo;
  return base + encodeURIComponent(text);
}

function searchUrl(query) {
  var engine = getPreference('engine', 'duckduckgo');
  var base = SEARCH_ENGINES[engine] || SEARCH_ENGINES.duckduckgo;
  return base + encodeURIComponent(query || '');
}

// ------------------------------------------------------------- extensions

function loadExtensions() {
  if (!window.plimb) return Promise.resolve([]);
  return window.plimb
    .listExtensions()
    .then(function (list) {
      extensions = list || [];
      return extensions;
    })
    .catch(function (error) {
      console.warn('Plimb: extensions indisponibles', error);
      return [];
    });
}

function extensionEnabled(extension) {
  return getPreference('ext:' + extension.id, extension.defaultEnabled !== false);
}

function urlMatches(pattern, url) {
  if (!pattern || pattern === '<all_urls>') return true;
  try {
    var escaped = pattern.replace(/[.+?^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '.*');
    return new RegExp('^' + escaped + '$').test(url);
  } catch (error) {
    return false;
  }
}

function injectExtensions(view) {
  loadExtensions().then(function (list) {
    var url = '';
    try {
      url = view.getURL();
    } catch (error) {
      url = '';
    }
    list.forEach(function (extension) {
      if (!extensionEnabled(extension)) return;
      if (extension.matches && extension.matches.length) {
        var matched = extension.matches.some(function (pattern) {
          return urlMatches(pattern, url);
        });
        if (!matched) return;
      }
      (extension.js || []).forEach(function (file) {
        window.plimb.readScript(extension.id, file).then(function (code) {
          if (!code) return;
          try {
            view.executeJavaScript(code, true);
          } catch (error) {
            console.warn('Plimb: injection impossible', extension.id, error);
          }
        });
      });
    });
  });
}

function renderExtensionMenu() {
  extListElement.innerHTML = '';
  if (!extensions.length) {
    var empty = document.createElement('li');
    empty.className = 'muted';
    empty.textContent = 'Aucune extension installée.';
    extListElement.appendChild(empty);
    return;
  }
  extensions.forEach(function (extension) {
    var item = document.createElement('li');
    var checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.checked = extensionEnabled(extension);
    checkbox.addEventListener('change', function () {
      store('ext:' + extension.id, checkbox.checked);
      var view = activeView();
      if (view) view.reload();
    });
    var label = document.createElement('span');
    label.textContent = ' ' + extension.name + ' ' + extension.version;
    var badge = document.createElement('em');
    badge.className = 'tag';
    badge.textContent = extension.bundled ? 'intégrée' : 'utilisateur';
    item.appendChild(checkbox);
    item.appendChild(label);
    item.appendChild(badge);
    item.title = extension.description || '';
    extListElement.appendChild(item);
  });
}

// ------------------------------------------------------------------ menu

document.getElementById('btn-menu').addEventListener('click', function (event) {
  event.stopPropagation();
  menuElement.hidden = !menuElement.hidden;
  if (!menuElement.hidden) {
    loadExtensions().then(renderExtensionMenu);
  }
});

document.addEventListener('click', function (event) {
  if (!menuElement.hidden && !menuElement.contains(event.target)) {
    menuElement.hidden = true;
  }
});

document.querySelectorAll('input[name="engine"]').forEach(function (radio) {
  radio.checked = getPreference('engine', 'duckduckgo') === radio.value;
  radio.addEventListener('change', function () {
    store('engine', radio.value);
  });
});

document.getElementById('btn-extensions').addEventListener('click', function (event) {
  event.stopPropagation();
  menuElement.hidden = false;
  loadExtensions().then(renderExtensionMenu);
});

// --------------------------------------------------------------- toolbar

document.getElementById('new-tab').addEventListener('click', function () {
  createTab();
});
document.getElementById('btn-back').addEventListener('click', function () {
  var view = activeView();
  if (view) view.goBack();
});
document.getElementById('btn-forward').addEventListener('click', function () {
  var view = activeView();
  if (view) view.goForward();
});
document.getElementById('btn-reload').addEventListener('click', function () {
  var view = activeView();
  var tab = activeTab();
  if (!view || !tab) return;
  if (tab.loading) view.stopLoading();
  else view.reload();
});
document.getElementById('btn-home').addEventListener('click', function () {
  var view = activeView();
  if (view) view.loadURL(NEWTAB_PAGE);
});

urlInput.addEventListener('keydown', function (event) {
  if (event.key === 'Enter') {
    var target = normalizeInput(urlInput.value);
    navigate(activeView(), target);
    urlInput.blur();
  } else if (event.key === 'Escape') {
    var tab = activeTab();
    urlInput.value = tab ? tab.url || '' : '';
    urlInput.blur();
  }
});
urlInput.addEventListener('focus', function () {
  urlInput.select();
});

document.addEventListener('keydown', function (event) {
  var ctrl = event.ctrlKey || event.metaKey;
  if (ctrl && event.key.toLowerCase() === 't') {
    event.preventDefault();
    createTab();
  } else if (ctrl && event.key.toLowerCase() === 'w') {
    event.preventDefault();
    if (activeId !== null) closeTab(activeId);
  } else if (ctrl && event.key.toLowerCase() === 'l') {
    event.preventDefault();
    urlInput.focus();
  } else if (ctrl && event.key.toLowerCase() === 'r') {
    event.preventDefault();
    document.getElementById('btn-reload').click();
  } else if (event.altKey && event.key === 'ArrowLeft') {
    var back = activeView();
    if (back) back.goBack();
  } else if (event.altKey && event.key === 'ArrowRight') {
    var forward = activeView();
    if (forward) forward.goForward();
  }
});

// ------------------------------------------------------------ telechargements

if (window.plimb) {
  window.plimb.onOpenUrl(function (url) {
    if (url) createTab(url);
  });

  window.plimb.onDownloadProgress(function (data) {
    downloadsElement.hidden = false;
    var percent = data.total > 0 ? Math.round((data.received / data.total) * 100) : 0;
    downloadsElement.textContent = '⬇ ' + data.name + ' — ' + percent + '%';
  });

  window.plimb.onDownloadDone(function (data) {
    downloadsElement.hidden = false;
    downloadsElement.textContent =
      data.state === 'completed' ? '✔ ' + data.name + ' téléchargé' : '✖ ' + data.name + ' (échec)';
    setTimeout(function () {
      downloadsElement.hidden = true;
    }, 5000);
  });

  window.plimb.blocklist().then(function (list) {
    blockedCountElement.textContent = String((list || []).length);
  });
}

loadExtensions().then(function () {
  if (window.plimb) {
    window.plimb.appVersion().then(function (version) {
      document.title = 'Plimb Browser ' + version;
    });
  }
});

createTab();
