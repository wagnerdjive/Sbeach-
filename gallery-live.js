// Shows the gallery photos managed by staff. The photos in gallery.html are the fallback if the API is unreachable.
(() => {
  const grid = document.querySelector('.gallery-page-grid');
  if (!grid) return;
  const base = String(window.SOUTH_BEACH_API_BASE || '').trim().replace(/\/+$/, '');
  const isLocalPreview = ['localhost', '127.0.0.1'].includes(location.hostname) && location.port === '8000';
  const apiOrigin = base || (isLocalPreview || location.protocol === 'file:' ? 'http://localhost:8080' : location.origin);
  const translations = window.SouthBeachTranslations || {};
  const language = () => { try { return localStorage.getItem('southBeachLanguage') === 'en' ? 'en' : 'pt'; } catch (_) { return 'pt'; } };
  const caption = (photo) => (language() === 'en' ? photo.captionEn || translations[photo.captionPt] || photo.captionPt : photo.captionPt);
  const src = (url) => (url.startsWith('/api/') ? apiOrigin + url : url);
  const sizeClass = { WIDE: ' gallery-wide', TALL: ' gallery-tall', NORMAL: '' };
  let photos = null;

  function render() {
    if (!photos) return;
    const filterButton = document.querySelector('[data-gallery-filter][aria-pressed="true"]');
    const filter = filterButton?.dataset.galleryFilter || 'all';
    const cards = photos.map((photo) => {
      const text = caption(photo);
      const article = document.createElement('article');
      article.className = `gallery-card${sizeClass[photo.size] || ''}`;
      article.dataset.galleryItem = photo.category.toLowerCase();
      article.hidden = filter !== 'all' && article.dataset.galleryItem !== filter;
      const button = document.createElement('button');
      button.type = 'button';
      button.setAttribute('data-lightbox-open', '');
      button.dataset.src = src(photo.imageUrl);
      button.dataset.title = text;
      const image = document.createElement('img');
      image.src = src(photo.imageUrl); image.alt = text; image.loading = 'lazy';
      const label = document.createElement('span');
      label.textContent = text;
      button.append(image, label);
      article.append(button);
      return article;
    });
    grid.replaceChildren(...cards);
  }

  fetch(`${apiOrigin}/api/gallery`, { headers: { Accept: 'application/json' } })
    .then((response) => (response.ok ? response.json() : null))
    .then((list) => { if (Array.isArray(list)) { photos = list; render(); } })
    .catch(() => { /* keep the photos written in the page */ });

  document.querySelectorAll('[data-language-toggle]').forEach((button) => button.addEventListener('click', () => setTimeout(render, 0)));
})();
