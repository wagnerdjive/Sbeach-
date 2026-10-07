// Applies staff edits (text, images, links) from the content API on top of the page's own HTML.
// Elements opt in with data-cms (text), data-cms-image (<img>), data-cms-bg (background image) and
// data-cms-href (link). Without an edit, or if the API is unreachable, the page's own content is used.
(() => {
  const base = String(window.SOUTH_BEACH_API_BASE || '').trim().replace(/\/+$/, '');
  const isLocalPreview = ['localhost', '127.0.0.1'].includes(location.hostname) && location.port === '8000';
  const apiOrigin = base || (isLocalPreview || location.protocol === 'file:' ? 'http://localhost:8080' : location.origin);
  const translations = window.SouthBeachTranslations || {};
  // Text is stored as plain text: a new line is a line break and *word* is emphasis (italic). No HTML is ever interpreted.
  const toSource = (element) => {
    let out = '';
    element.childNodes.forEach((child) => {
      if (child.nodeType === Node.TEXT_NODE) out += child.nodeValue.replace(/\s+/g, ' ');
      else if (child.nodeName === 'BR') out = out.replace(/ +$/, '') + '\n';
      else if (child.nodeName === 'EM') out += `*${child.textContent.replace(/\s+/g, ' ')}*`;
      else out += child.textContent;
    });
    return out.split('\n').map((line) => line.trim()).join('\n').trim();
  };
  const language = () => { try { return localStorage.getItem('southBeachLanguage') === 'en' ? 'en' : 'pt'; } catch (_) { return 'pt'; } };
  // Each piece is looked up on its own, exactly as the page's own translations are keyed.
  const translatePiece = (piece) => {
    const translated = translations[piece.trim()];
    // Keep the spaces around the piece, as the page's own translator does.
    return translated ? `${piece.match(/^\s*/)[0]}${translated}${piece.match(/\s*$/)[0]}` : piece;
  };

  window.SouthBeachCms = { toSource, translatePiece, apiOrigin };

  const texts = [...document.querySelectorAll('[data-cms]')];
  const images = [...document.querySelectorAll('[data-cms-image]')];
  const backgrounds = [...document.querySelectorAll('[data-cms-bg]')];
  const links = [...document.querySelectorAll('[data-cms-href]')];
  if (!texts.length && !images.length && !backgrounds.length && !links.length) return;

  const defaults = new Map(texts.map((element) => [element, toSource(element)]));
  const defaultSrc = new Map(images.map((element) => [element, element.getAttribute('src')]));
  const defaultHref = new Map(links.map((element) => [element, element.getAttribute('href')]));
  let overrides = {};

  function render(element, source, translate) {
    const fragment = document.createDocumentFragment();
    source.split('\n').forEach((line, index) => {
      if (index > 0) fragment.append(document.createElement('br'));
      line.split(/(\*[^*]+\*)/).forEach((part) => {
        if (!part) return;
        if (part.length > 2 && part.startsWith('*') && part.endsWith('*')) {
          const em = document.createElement('em');
          em.textContent = translate(part.slice(1, -1));
          fragment.append(em);
        } else fragment.append(document.createTextNode(translate(part)));
      });
    });
    element.replaceChildren(fragment);
  }

  const mediaUrl = (value) => (value && value.startsWith('/api/') ? apiOrigin + value : value);

  function apply() {
    const english = language() === 'en';
    texts.forEach((element) => {
      const edit = overrides[element.dataset.cms];
      const fallback = defaults.get(element);
      if (!english) render(element, edit?.pt || fallback, (piece) => piece);
      else if (edit?.en) render(element, edit.en, (piece) => piece);
      else render(element, edit?.pt || fallback, translatePiece);
    });
    images.forEach((element) => {
      const value = overrides[element.dataset.cmsImage]?.pt;
      element.setAttribute('src', value ? mediaUrl(value) : defaultSrc.get(element));
    });
    backgrounds.forEach((element) => {
      const value = overrides[element.dataset.cmsBg]?.pt;
      if (value) element.style.setProperty('--cms-bg', `url("${mediaUrl(value)}")`);
      else element.style.removeProperty('--cms-bg');
    });
    links.forEach((element) => {
      const value = overrides[element.dataset.cmsHref]?.pt;
      element.setAttribute('href', value || defaultHref.get(element));
    });
  }

  apply();
  document.querySelectorAll('[data-language-toggle]').forEach((button) => button.addEventListener('click', () => setTimeout(apply, 0)));
  fetch(`${apiOrigin}/api/content`, { headers: { Accept: 'application/json' } })
    .then((response) => (response.ok ? response.json() : {}))
    .then((data) => { overrides = data || {}; apply(); })
    .catch(() => { /* keep the page's own content */ });
})();
