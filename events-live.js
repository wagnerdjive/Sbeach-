(() => {
  const list = document.querySelector('[data-events-list]');
  if (!list) return;

  const base = String(window.SOUTH_BEACH_API_BASE || '').trim().replace(/\/+$/, '');
  const isLocalPreview = ['localhost', '127.0.0.1'].includes(location.hostname) && location.port === '8000';
  const apiOrigin = base || (isLocalPreview || location.protocol === 'file:' ? 'http://localhost:8080' : location.origin);

  const copy = {
    pt: { label: 'PRÓXIMOS EVENTOS', title: 'Escolha o evento<br /><em>e os seus bilhetes.</em>', buy: 'Comprar bilhetes', soldOut: 'Esgotado', notOnSale: 'Venda em breve', from: 'a partir de', noPoster: 'Sem cartaz' },
    en: { label: 'UPCOMING EVENTS', title: 'Choose your event<br /><em>and tickets.</em>', buy: 'Buy tickets', soldOut: 'Sold out', notOnSale: 'On sale soon', from: 'from', noPoster: 'No poster' }
  };
  const lang = () => { try { return localStorage.getItem('southBeachLanguage') === 'en' ? 'en' : 'pt'; } catch (_) { return 'pt'; } };
  const t = (key) => copy[lang()][key];
  const locale = () => (lang() === 'en' ? 'en-GB' : 'pt-PT');
  const money = (minor) => `${(minor / 100).toLocaleString(locale(), { minimumFractionDigits: minor % 100 ? 2 : 0, maximumFractionDigits: 2 })} MZN`;
  const when = (iso) => new Intl.DateTimeFormat(locale(), { timeZone: 'Africa/Maputo', dateStyle: 'full', timeStyle: 'short' }).format(new Date(iso));
  const el = (tag, props = {}, ...children) => { const node = Object.assign(document.createElement(tag), props); node.append(...children); return node; };

  let events = [];
  const refreshers = [];

  function card(event) {
    const types = event.ticketTypes;
    const open = types.filter((type) => type.onSale && type.available > 0);
    const href = `tickets.html?evento=${encodeURIComponent(event.slug)}`;
    const media = el('div', { className: 'event-card-media', role: event.posterUrl ? undefined : 'img', ariaLabel: event.posterUrl ? undefined : event.title });
    if (event.posterUrl) media.append(el('img', { className: 'event-card-poster', src: apiOrigin + event.posterUrl, alt: `Cartaz — ${event.title}`, loading: 'lazy' }));
    const status = el('span', { className: 'event-card-status' });
    const cta = el('span', { className: 'button button-dark event-card-cta' });
    const link = el('a', { className: 'event-card-link', href }, media,
      el('div', { className: 'event-card-body' },
        el('p', { className: 'eyebrow', textContent: event.location }),
        el('h3', { textContent: event.title }),
        el('p', { className: 'event-card-date', textContent: when(event.startsAt) }),
        status, cta));
    refreshers.push(() => {
      status.textContent = open.length
        ? `${t('from')} ${money(Math.min(...open.map((type) => type.priceMinor)))}`
        : types.some((type) => !type.onSale && type.available > 0) ? t('notOnSale') : t('soldOut');
      cta.textContent = t('buy');
    });
    return el('article', { className: 'event-card' }, link);
  }

  function render() {
    refreshers.length = 0;
    const heading = el('div', { className: 'events-list-heading' }, el('p', { className: 'eyebrow' }), el('h2'));
    refreshers.push(() => { heading.querySelector('.eyebrow').textContent = t('label'); heading.querySelector('h2').innerHTML = t('title'); });
    list.replaceChildren(heading, el('div', { className: 'event-card-grid' }, ...events.map(card)));
    refreshers.forEach((run) => run());
  }

  async function load() {
    try {
      const response = await fetch(`${apiOrigin}/api/events`, { headers: { Accept: 'application/json' } });
      if (!response.ok) return;
      events = (await response.json()).filter((event) => event.ticketTypes.length > 0);
      if (!events.length) return;
      render();
      list.hidden = false;
      // The list replaces the "no events" notice and the "coming soon" teaser.
      document.querySelectorAll('.event-state, .ticketing-teaser').forEach((section) => { section.hidden = true; });
      // Coming back from a sale screen: land on the list, not on the page's intro.
      if (location.hash === '#bilhetes') list.scrollIntoView();
    } catch (_) { /* keep the static page when the API is unreachable */ }
  }

  document.querySelector('[data-language-toggle]')?.addEventListener('click', () => setTimeout(() => events.length && render(), 0));
  load();
})();
