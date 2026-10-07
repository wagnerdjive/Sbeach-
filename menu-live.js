// Shows the dishes and drinks managed by staff in each menu panel. A space with no items keeps the link to its full menu.
(() => {
  const lists = [...document.querySelectorAll('[data-menu-items]')];
  if (!lists.length) return;
  const base = String(window.SOUTH_BEACH_API_BASE || '').trim().replace(/\/+$/, '');
  const isLocalPreview = ['localhost', '127.0.0.1'].includes(location.hostname) && location.port === '8000';
  const apiOrigin = base || (isLocalPreview || location.protocol === 'file:' ? 'http://localhost:8080' : location.origin);
  const translations = window.SouthBeachTranslations || {};
  const language = () => { try { return localStorage.getItem('southBeachLanguage') === 'en' ? 'en' : 'pt'; } catch (_) { return 'pt'; } };
  const pick = (pt, en) => (language() === 'en' ? en || translations[pt] || pt : pt);
  const price = (cents) => `${(cents / 100).toLocaleString(language() === 'en' ? 'en-GB' : 'pt-PT', { minimumFractionDigits: cents % 100 ? 2 : 0, maximumFractionDigits: 2 })} MZN`;
  let items = null;

  function render() {
    if (!items) return;
    const pressed = document.querySelector('[data-menu-category][aria-pressed="true"]');
    const kind = (pressed?.dataset.menuCategory || 'food').toUpperCase();
    lists.forEach((list) => {
      const own = items.filter((item) => item.venue === list.dataset.menuItems);
      const panel = list.closest('[data-menu-panel]');
      panel.classList.toggle('has-items', own.length > 0);
      panel.querySelector('.menu-external')?.toggleAttribute('hidden', own.length > 0);
      list.hidden = own.length === 0;
      const shown = own.filter((item) => item.kind === kind);
      const sections = new Map();
      shown.forEach((item) => {
        const title = pick(item.sectionPt, item.sectionEn);
        if (!sections.has(title)) sections.set(title, []);
        sections.get(title).push(item);
      });
      const blocks = [...sections].map(([title, group]) => {
        const block = document.createElement('section');
        block.className = 'menu-section';
        const heading = document.createElement('h4');
        heading.textContent = title;
        const dishes = document.createElement('ul');
        group.forEach((item) => {
          const li = document.createElement('li');
          const name = document.createElement('span');
          name.className = 'menu-dish-name';
          name.textContent = pick(item.namePt, item.nameEn);
          li.append(name);
          if (item.priceCents != null) {
            const cost = document.createElement('span');
            cost.className = 'menu-dish-price';
            cost.textContent = price(item.priceCents);
            li.append(cost);
          }
          const about = pick(item.descriptionPt, item.descriptionEn);
          if (item.descriptionPt && about) {
            const description = document.createElement('p');
            description.textContent = about;
            li.append(description);
          }
          dishes.append(li);
        });
        block.append(heading, dishes);
        return block;
      });
      if (!blocks.length && own.length) {
        const none = document.createElement('p');
        none.className = 'menu-empty';
        none.textContent = language() === 'en' ? 'Nothing listed here yet.' : 'Ainda não há itens nesta categoria.';
        blocks.push(none);
      }
      list.replaceChildren(...blocks);
    });
  }

  fetch(`${apiOrigin}/api/menu`, { headers: { Accept: 'application/json' } })
    .then((response) => (response.ok ? response.json() : null))
    .then((list) => { if (Array.isArray(list)) { items = list; render(); } })
    .catch(() => { /* keep the links to the full menus */ });

  document.querySelectorAll('[data-menu-category]').forEach((button) => button.addEventListener('click', () => setTimeout(render, 0)));
  document.querySelectorAll('[data-language-toggle]').forEach((button) => button.addEventListener('click', () => setTimeout(render, 0)));
})();
