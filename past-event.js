// Shows one past event (details and photo album) chosen by ?e=<slug>, as staff entered it in the panel.
(() => {
  const photos = document.querySelector('[data-past-photos]');
  if (!photos) return;
  const base = String(window.SOUTH_BEACH_API_BASE || '').trim().replace(/\/+$/, '');
  const isLocalPreview = ['localhost', '127.0.0.1'].includes(location.hostname) && location.port === '8000';
  const apiOrigin = base || (isLocalPreview || location.protocol === 'file:' ? 'http://localhost:8080' : location.origin);
  const language = () => { try { return localStorage.getItem('southBeachLanguage') === 'en' ? 'en' : 'pt'; } catch (_) { return 'pt'; } };
  const text = {
    pt: { eyebrow: 'EVENTO PASSADO', back: 'Todos os eventos passados', loading: 'A carregar…', missing: 'Evento não encontrado', missingNote: 'Este evento já não está disponível. Veja os outros eventos passados.', empty: 'Ainda não há fotografias deste evento.' },
    en: { eyebrow: 'PAST EVENT', back: 'All past events', loading: 'Loading…', missing: 'Event not found', missingNote: 'This event is no longer available. See the other past events.', empty: 'There are no photos of this event yet.' }
  };
  const t = (key) => text[language()][key];
  const src = (url) => (url.startsWith('/api/') ? apiOrigin + url : url);
  const slug = new URLSearchParams(location.search).get('e');
  let event = null, failed = false;

  const set = (selector, value) => { const node = document.querySelector(selector); if (node) node.textContent = value; };

  function render() {
    set('[data-past-eyebrow]', t('eyebrow'));
    set('[data-past-back]', t('back'));
    const note = document.querySelector('[data-past-note]');
    if (failed || !event) {
      set('[data-past-title]', failed ? t('missing') : t('loading'));
      set('[data-past-meta]', ''); document.querySelector('[data-past-text]').replaceChildren();
      photos.replaceChildren(); note.hidden = !failed; note.textContent = failed ? t('missingNote') : '';
      return;
    }
    const en = language() === 'en';
    const title = (en && event.titleEn) || event.titlePt;
    set('[data-past-title]', title);
    document.title = `${title} — South Beach Maputo`;
    set('[data-past-meta]', [(en && event.dateTextEn) || event.dateTextPt, event.timeText, event.location].filter(Boolean).join('  ·  '));
    const description = (en && event.descriptionEn) || event.descriptionPt || '';
    document.querySelector('[data-past-text]').replaceChildren(...description.split(/\n{1,}/).filter((line) => line.trim()).map((line) => {
      const p = document.createElement('p'); p.textContent = line; return p;
    }));
    const cards = event.photos.map((photo, index) => {
      const article = document.createElement('article');
      article.className = 'gallery-card';
      article.dataset.galleryItem = 'event';
      const button = document.createElement('button');
      button.type = 'button';
      button.setAttribute('data-lightbox-open', '');
      button.dataset.src = src(photo.imageUrl);
      button.dataset.title = `${title} · ${index + 1}/${event.photos.length}`;
      const image = document.createElement('img');
      image.src = src(photo.imageUrl); image.alt = `${title} — ${index + 1}`; image.loading = 'lazy';
      button.append(image); article.append(button);
      return article;
    });
    photos.replaceChildren(...cards);
    note.hidden = cards.length > 0; note.textContent = cards.length ? '' : t('empty');
  }

  render();
  if (!slug) { failed = true; render(); return; }
  fetch(`${apiOrigin}/api/past-events/${encodeURIComponent(slug)}`, { headers: { Accept: 'application/json' } })
    .then((response) => (response.ok ? response.json() : Promise.reject(response.status)))
    .then((data) => { event = data; render(); })
    .catch((status) => { if (status === 404) { failed = true; render(); } });

  document.querySelectorAll('[data-language-toggle]').forEach((button) => button.addEventListener('click', () => setTimeout(render, 0)));
})();
