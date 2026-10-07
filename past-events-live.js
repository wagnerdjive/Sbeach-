// Replaces the archive list on the events page with the past events staff manage in the panel (cover, title, date).
// The list written in events.html stays as the fallback if the API is unreachable.
(() => {
  const list = document.querySelector('.past-event-list');
  if (!list) return;
  const base = String(window.SOUTH_BEACH_API_BASE || '').trim().replace(/\/+$/, '');
  const isLocalPreview = ['localhost', '127.0.0.1'].includes(location.hostname) && location.port === '8000';
  const apiOrigin = base || (isLocalPreview || location.protocol === 'file:' ? 'http://localhost:8080' : location.origin);
  const language = () => { try { return localStorage.getItem('southBeachLanguage') === 'en' ? 'en' : 'pt'; } catch (_) { return 'pt'; } };
  const photoWord = (n) => (language() === 'en' ? (n === 1 ? 'photo' : 'photos') : (n === 1 ? 'fotografia' : 'fotografias'));
  let events = null;

  function render() {
    if (!events) return;
    const en = language() === 'en';
    list.className = 'past-event-grid';
    list.replaceChildren(...events.map((event) => {
      const link = document.createElement('a');
      link.href = `past-event.html?e=${encodeURIComponent(event.slug)}`;
      const media = document.createElement('div');
      media.className = 'past-event-cover';
      if (event.coverUrl) {
        const image = document.createElement('img');
        image.src = event.coverUrl.startsWith('/api/') ? apiOrigin + event.coverUrl : event.coverUrl;
        image.alt = ''; image.loading = 'lazy';
        media.append(image);
      }
      const title = document.createElement('strong');
      title.textContent = (en && event.titleEn) || event.titlePt;
      const meta = document.createElement('span');
      meta.textContent = [(en && event.dateTextEn) || event.dateTextPt, event.photoCount ? `${event.photoCount} ${photoWord(event.photoCount)}` : null].filter(Boolean).join(' · ');
      link.append(media, title, meta);
      return link;
    }));
  }

  fetch(`${apiOrigin}/api/past-events`, { headers: { Accept: 'application/json' } })
    .then((response) => (response.ok ? response.json() : null))
    .then((data) => { if (Array.isArray(data) && data.length) { events = data; render(); } })
    .catch(() => { /* keep the list written in the page */ });

  document.querySelectorAll('[data-language-toggle]').forEach((button) => button.addEventListener('click', () => setTimeout(render, 0)));
})();
