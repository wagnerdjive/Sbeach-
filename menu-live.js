// Shows the dishes and drinks managed by staff in each menu panel, one group and one section at a time.
// A space with no items keeps the link to its full menu.
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
  const chosen = {}; // group and section picked in each space and kind; kept across re-renders and language switches

  // Groups items by a key, in order of first appearance. Items without a key share one unnamed group.
  function bucket(list, keyOf) {
    const map = new Map();
    list.forEach((item) => {
      const key = keyOf(item);
      if (!map.has(key)) map.set(key, []);
      map.get(key).push(item);
    });
    return map;
  }

  function pills(className, labels, current, onPick) {
    const nav = document.createElement('div');
    nav.className = className;
    labels.forEach(({ key, label }) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.textContent = label;
      button.setAttribute('aria-pressed', String(key === current));
      button.addEventListener('click', () => onPick(key));
      nav.append(button);
    });
    return nav;
  }

  function dish(item) {
    const li = document.createElement('li');
    const name = document.createElement('span');
    name.className = 'menu-dish-name';
    name.textContent = pick(item.namePt, item.nameEn);
    li.append(name);
    if (item.priceCents != null) {
      const leader = document.createElement('span');
      leader.className = 'menu-leader';
      leader.setAttribute('aria-hidden', 'true');
      li.append(leader);
      const cost = document.createElement('span');
      cost.className = 'menu-dish-price';
      cost.textContent = price(item.priceCents);
      li.append(cost);
    }
    if (item.descriptionPt) {
      const description = document.createElement('p');
      description.textContent = pick(item.descriptionPt, item.descriptionEn);
      li.append(description);
    }
    return li;
  }

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
      const blocks = [];
      if (!shown.length && own.length) {
        const none = document.createElement('p');
        none.className = 'menu-empty';
        none.textContent = language() === 'en' ? 'Nothing listed here yet.' : 'Ainda não há itens nesta categoria.';
        blocks.push(none);
      } else if (shown.length) {
        const key = `${list.dataset.menuItems}:${kind}`;
        const state = (chosen[key] ||= {});
        const groups = bucket(shown, (item) => item.groupPt || '');
        if (!groups.has(state.group)) state.group = [...groups.keys()][0];
        if (groups.size > 1) {
          blocks.push(pills('menu-groups-nav', [...groups].map(([group, rows]) => ({ key: group, label: pick(group, rows[0].groupEn) })),
            state.group, (group) => { state.group = group; render(); }));
        }
        const sections = bucket(groups.get(state.group), (item) => item.sectionPt);
        if (!sections.has(state.section)) state.section = [...sections.keys()][0];
        if (sections.size > 1) {
          blocks.push(pills('menu-sections-nav', [...sections].map(([section, rows]) => ({ key: section, label: pick(section, rows[0].sectionEn) })),
            state.section, (section) => { state.section = section; render(); }));
        }
        const rows = sections.get(state.section);
        const block = document.createElement('section');
        block.className = 'menu-section';
        const heading = document.createElement('h4');
        heading.textContent = pick(state.section, rows[0].sectionEn);
        const dishes = document.createElement('ul');
        rows.forEach((item) => dishes.append(dish(item)));
        block.append(heading, dishes);
        blocks.push(block);
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
