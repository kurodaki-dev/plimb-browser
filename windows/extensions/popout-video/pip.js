(function () {
  function addButton() {
    if (document.getElementById('plimb-pip-btn')) return;
    var video = document.querySelector('video');
    if (!video) return;

    var button = document.createElement('button');
    button.id = 'plimb-pip-btn';
    button.textContent = 'PiP';
    button.style.cssText =
      'position:fixed;right:16px;bottom:16px;z-index:2147483647;padding:8px 12px;' +
      'border:0;border-radius:8px;background:#2563eb;color:#fff;' +
      'font:700 12px/1 system-ui,sans-serif;cursor:pointer;box-shadow:0 2px 10px rgba(0,0,0,.35)';

    button.addEventListener('click', function () {
      try {
        if (document.pictureInPictureElement) {
          document.exitPictureInPicture();
        } else {
          video.requestPictureInPicture();
        }
      } catch (error) {
        console.warn('Plimb popout-video :', error);
      }
    });

    (document.body || document.documentElement).appendChild(button);
  }

  addButton();
  setTimeout(addButton, 1500);
  window.addEventListener('load', addButton);

  try {
    var observer = new MutationObserver(addButton);
    observer.observe(document.documentElement, { childList: true, subtree: true });
  } catch (error) {
    /* navigateur sans MutationObserver */
  }
})();
