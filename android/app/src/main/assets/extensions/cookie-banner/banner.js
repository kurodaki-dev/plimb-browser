(function () {
  var REJECT = /(reject all|reject|decline|deny|refuser|tout refuser|ne pas accepter|refus|no,? thanks|disagree)/i;
  var ACCEPT = /(accept all|accept|accepter|tout accepter|j'accepte|agree|i agree|ok|got it|continuer)/i;

  function click(regex) {
    var nodes = document.querySelectorAll('button, a[role="button"], [class*="cookie"] button, [id*="cookie"] button, [class*="consent"] button');
    for (var i = 0; i < nodes.length; i++) {
      var label = (nodes[i].textContent || '').trim();
      if (label.length > 0 && label.length < 40 && regex.test(label)) {
        nodes[i].click();
        return true;
      }
    }
    return false;
  }

  var attempts = 0;
  var timer = setInterval(function () {
    attempts += 1;
    var handled = click(REJECT) || click(ACCEPT);
    if (handled || attempts > 20) clearInterval(timer);
  }, 700);
})();
