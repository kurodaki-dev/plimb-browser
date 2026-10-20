/* Plimb Bloqueur — meme liste de domaines que windows/blocklist.txt.
   Generee depuis la meme source : garder les deux fichiers synchronises. */
(function () {
  var BLOCKED = [
    "doubleclick.net", "2mdn.net", "googleadservices.com", "googlesyndication.com",
    "googletagservices.com", "googletagmanager.com", "google-analytics.com",
    "adservice.google.com", "ads.google.com", "adnxs.com", "adform.net", "adsrvr.org",
    "pubmatic.com", "rubiconproject.com", "openx.net", "smartadserver.com",
    "indexww.com", "casalemedia.com", "sharethrough.com", "triplelift.com",
    "districtm.io", "sonobi.com", "gumgum.com", "media.net", "bidswitch.net",
    "spotxchange.com", "spotx.tv", "aniview.com", "criteo.com", "criteo.net",
    "taboola.com", "outbrain.com", "mgid.com", "revcontent.com", "adcolony.com",
    "applovin.com", "chartboost.com", "vungle.com", "inmobi.com", "startapp.com",
    "mopub.com", "unityads.unity3d.com", "appsflyer.com", "adjust.com", "kochava.com",
    "branch.io", "amazon-adsystem.com", "bat.bing.com", "scorecardresearch.com",
    "quantserve.com", "chartbeat.com", "amplitude.com", "mixpanel.com", "segment.com",
    "segment.io", "optimizely.com", "vwo.com", "abtasty.com", "hotjar.com",
    "mouseflow.com", "crazyegg.com", "fullstory.com", "clarity.ms", "popads.net",
    "popcash.net", "propellerads.com", "adsterra.com", "exoclick.com", "juicyads.com",
    "trafficstars.com", "clickadu.com", "adcash.com", "ads.tiktok.com",
    "analytics.tiktok.com", "an.yandex.ru", "ads.yandex.ru", "addthis.com", "sharethis.com"
  ];

  function blocked(url) {
    var host;
    try {
      host = new URL(url).hostname.toLowerCase();
    } catch (error) {
      return false;
    }
    for (var i = 0; i < BLOCKED.length; i++) {
      var domain = BLOCKED[i];
      if (host === domain || host.endsWith("." + domain)) {
        return true;
      }
    }
    return false;
  }

  if (typeof browser !== "undefined" && browser.webRequest && browser.webRequest.onBeforeRequest) {
    browser.webRequest.onBeforeRequest.addListener(
      function (details) {
        return { cancel: blocked(details.url) };
      },
      { urls: ["<all_urls>"] },
      "blocking"
    );
    console.log("Plimb Bloqueur actif :", BLOCKED.length, "domaines");
  } else {
    console.warn("Plimb Bloqueur : API webRequest indisponible.");
  }
})();
