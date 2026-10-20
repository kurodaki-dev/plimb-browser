(function () {
  try {
    if (document.documentElement.getAttribute('data-plimb-dark') === '1') return;
    document.documentElement.setAttribute('data-plimb-dark', '1');

    var style = document.createElement('style');
    style.id = 'plimb-dark-style';
    style.textContent =
      'html { background-color: #101418 !important; }' +
      'html { filter: invert(92%) hue-rotate(180deg) contrast(95%) saturate(110%) !important; }' +
      'img, video, iframe, picture, canvas, svg, embed, object, [style*="background-image"] {' +
      '  filter: invert(100%) hue-rotate(180deg) contrast(100%) saturate(100%) !important;' +
      '}';
    (document.head || document.documentElement).appendChild(style);
  } catch (error) {
    console.warn('Plimb dark-theme :', error);
  }
})();
