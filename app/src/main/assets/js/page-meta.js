/*
 * What "Meine Liste" reads from the page (PageMeta.parse): og:title values, document title, descriptions,
 * the og:image as an absolute URL and the first heading (or its logo's alt text). Returns the object.
 */
(function () {
  function all(sel) {
    return Array.prototype.map.call(document.querySelectorAll(sel), function (m) { return m.getAttribute('content') || ''; });
  }
  function first(sel) { var v = all(sel); return v.length ? v[0] : null; }
  var image = first('meta[property="og:image"],meta[name="og:image"]'), resolved = null;
  try { if (image) resolved = new URL(image, document.baseURI).href; } catch (e) {}
  var h1 = document.querySelector('h1'), heading = null;
  if (h1) {
    heading = (h1.innerText || h1.textContent || '').trim();
    var logo = h1.querySelector('img[alt]');
    if (!heading && logo) heading = logo.getAttribute('alt');
  }
  return {
    titles: all('meta[property="og:title"],meta[name="og:title"]'),
    title: document.title,
    ogDescription: first('meta[property="og:description"],meta[name="og:description"]'),
    description: first('meta[name="description"]'),
    image: resolved,
    heading: heading
  };
})()
