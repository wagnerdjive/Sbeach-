(() => {
  const base = String(window.SOUTH_BEACH_API_BASE || '').trim().replace(/\/+$/, '');
  const isLocalPreview = ['localhost', '127.0.0.1'].includes(location.hostname) && location.port === '8000';
  const apiOrigin = base || (isLocalPreview || location.protocol === 'file:' ? 'http://localhost:8080' : location.origin);
  const venueLabels = { RESTAURANT: 'Restaurante', BEACH_BAR: 'Beach Bar', SPORTS_BAR: 'Sports Bar', NO_PREFERENCE: 'Sem preferência' };
  const statusLabels = { PENDING: 'Pendente', CONFIRMED: 'Confirmada', CANCELLED: 'Cancelada' };
  const $ = (selector) => document.querySelector(selector);
  // Credentials stay in memory only: closing or reloading the tab signs the user out.
  let authHeader = null;

  const loginForm = $('[data-login]'), panel = $('[data-panel]'), rows = $('[data-rows]');
  const message = $('[data-message]'), loginMessage = $('[data-login-message]');
  const filter = $('[data-filter]'), empty = $('[data-empty]'), logout = $('[data-logout]');

  function say(element, text, ok = false) {
    element.textContent = text;
    element.classList.toggle('ok', ok);
  }

  async function api(path, options = {}) {
    const response = await fetch(apiOrigin + path, {
      ...options,
      headers: { Accept: 'application/json', Authorization: authHeader, ...(options.body ? { 'Content-Type': 'application/json' } : {}) }
    });
    if (response.status === 401) { signOut(); throw new Error('Sessão inválida. Entre novamente.'); }
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      const details = Object.values(data.fields || {}).join('; ');
      throw new Error([data.message, details].filter(Boolean).join(' — ') || 'Não foi possível concluir o pedido.');
    }
    return data;
  }

  function cell(tr, text, className) {
    const td = tr.insertCell();
    td.textContent = text ?? '';
    if (className) td.className = className;
    return td;
  }

  function actionButton(label, onClick) {
    const button = document.createElement('button');
    button.type = 'button';
    button.textContent = label;
    button.addEventListener('click', onClick);
    return button;
  }

  async function changeStatus(reservation, status, venue, buttons) {
    buttons.forEach((button) => { button.disabled = true; });
    try {
      await api(`/api/admin/reservations/${encodeURIComponent(reservation.reference)}/status`, {
        method: 'PATCH', body: JSON.stringify(venue ? { status, venue } : { status })
      });
      await load();
      say(message, `${reservation.reference}: ${statusLabels[status].toLowerCase()}.`, true);
    } catch (error) {
      say(message, error.message);
      buttons.forEach((button) => { button.disabled = false; });
    }
  }

  function render(reservations) {
    rows.replaceChildren();
    empty.hidden = reservations.length > 0;
    for (const r of reservations) {
      const tr = rows.insertRow();
      cell(tr, r.reference);
      cell(tr, `${r.fullName}\n${r.phone}`, 'admin-notes');
      cell(tr, `${r.requestedDate} ${String(r.requestedTime).slice(0, 5)}`);
      cell(tr, r.partySize);
      cell(tr, venueLabels[r.venue] || r.venue);
      cell(tr, [r.occasion, r.notes].filter(Boolean).join('\n'), 'admin-notes');
      const badge = document.createElement('span');
      badge.className = `admin-badge ${r.status}`;
      badge.textContent = statusLabels[r.status] || r.status;
      cell(tr, '').append(badge);
      const actions = document.createElement('div');
      cell(tr, '', 'admin-actions').append(actions);
      if (r.status === 'CANCELLED') continue;
      const buttons = [];
      let venueSelect = null;
      if (r.status === 'PENDING') {
        if (r.venue === 'NO_PREFERENCE') {
          venueSelect = document.createElement('select');
          venueSelect.setAttribute('aria-label', `Espaço para ${r.reference}`);
          for (const key of ['RESTAURANT', 'BEACH_BAR', 'SPORTS_BAR']) venueSelect.add(new Option(venueLabels[key], key));
          actions.append(venueSelect);
        }
        buttons.push(actionButton('Confirmar', () => changeStatus(r, 'CONFIRMED', venueSelect && venueSelect.value, buttons)));
      }
      buttons.push(actionButton('Cancelar', () => {
        if (confirm(`Cancelar a reserva ${r.reference} de ${r.fullName}?`)) changeStatus(r, 'CANCELLED', null, buttons);
      }));
      actions.append(...buttons);
    }
  }

  async function load() {
    const query = filter.value ? `?status=${encodeURIComponent(filter.value)}&size=100` : '?size=100';
    const page = await api('/api/admin/reservations' + query);
    render(page.content || []);
  }

  function signOut() {
    authHeader = null;
    panel.hidden = true; logout.hidden = true; loginForm.hidden = false;
    rows.replaceChildren();
    document.querySelectorAll('[data-tab]').forEach((tab) => tab.setAttribute('aria-selected', String(tab.dataset.tab === 'reservations')));
    document.querySelectorAll('[data-view]').forEach((view) => { view.hidden = view.dataset.view !== 'reservations'; });
  }

  loginForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = new FormData(loginForm);
    authHeader = 'Basic ' + btoa(unescape(encodeURIComponent(`${form.get('username')}:${form.get('password')}`)));
    say(loginMessage, 'A verificar…');
    try {
      await load();
      loginForm.reset(); say(loginMessage, ''); say(message, '');
      loginForm.hidden = true; panel.hidden = false; logout.hidden = false;
    } catch (error) {
      authHeader = null;
      say(loginMessage, error instanceof TypeError ? 'Não foi possível contactar a API.' : (error.message || 'Credenciais inválidas.'));
    }
  });
  filter.addEventListener('change', () => load().catch((error) => say(message, error.message)));
  $('[data-refresh]').addEventListener('click', () => load().then(() => say(message, '')).catch((error) => say(message, error.message)));
  logout.addEventListener('click', signOut);

  // ---- Tabs -------------------------------------------------------------
  const views = Object.fromEntries([...document.querySelectorAll('[data-view]')].map((node) => [node.dataset.view, node]));
  const tabs = [...document.querySelectorAll('[data-tab]')];
  const loaders = { reservations: () => load(), events: () => loadEvents(), orders: () => loadOrders() };
  function showView(name) {
    tabs.forEach((tab) => tab.setAttribute('aria-selected', String(tab.dataset.tab === name)));
    Object.entries(views).forEach(([key, node]) => { node.hidden = key !== name; });
    say(message, '');
    loaders[name]().catch((error) => say(message, error.message));
  }
  tabs.forEach((tab) => tab.addEventListener('click', () => showView(tab.dataset.tab)));

  // ---- Helpers for dates and money (Maputo is UTC+2, no daylight saving) ----
  const money = (minor) => `${(minor / 100).toLocaleString('pt-PT', { minimumFractionDigits: minor % 100 ? 2 : 0, maximumFractionDigits: 2 })} MZN`;
  const maputoFormat = new Intl.DateTimeFormat('pt-PT', { timeZone: 'Africa/Maputo', dateStyle: 'short', timeStyle: 'short' });
  const showDate = (iso) => (iso ? maputoFormat.format(new Date(iso)) : '—');
  const toInstant = (local) => (local ? new Date(`${local}:00+02:00`).toISOString() : null);
  const toLocalInput = (iso) => (iso ? new Date(new Date(iso).getTime() + 2 * 3600 * 1000).toISOString().slice(0, 16) : '');
  const eventStatusLabels = { DRAFT: 'Rascunho', PUBLISHED: 'Publicado', CANCELLED: 'Cancelado' };
  const orderStatusLabels = { PENDING: 'Pendente', PAID: 'Paga', EXPIRED: 'Expirada', CANCELLED: 'Cancelada' };

  function badge(status, labels) {
    const span = document.createElement('span');
    span.className = `admin-badge ${status}`;
    span.textContent = labels[status] || status;
    return span;
  }
  function node(tag, text, className) {
    const element = document.createElement(tag);
    if (text !== undefined) element.textContent = text;
    if (className) element.className = className;
    return element;
  }

  // ---- Events and ticket types -------------------------------------------
  const eventForm = $('[data-event-form]'), typeForm = $('[data-type-form]');
  const eventsBox = $('[data-events]'), eventsEmpty = $('[data-events-empty]');
  let eventsCache = [];
  let editingEventId = null, typeTarget = null;

  function closeForms() { eventForm.hidden = true; typeForm.hidden = true; }
  document.querySelectorAll('[data-cancel-form]').forEach((button) => button.addEventListener('click', closeForms));

  function openEventForm(event) {
    closeForms();
    editingEventId = event ? event.id : null;
    eventForm.reset();
    $('[data-event-form-title]').textContent = event ? `Editar evento — ${event.title}` : 'Novo evento';
    if (event) {
      for (const key of ['slug', 'title', 'location', 'status', 'description']) eventForm.elements[key].value = event[key] ?? '';
      eventForm.elements.startsAt.value = toLocalInput(event.startsAt);
      eventForm.elements.endsAt.value = toLocalInput(event.endsAt);
    }
    eventForm.hidden = false;
    eventForm.elements.title.focus();
  }

  function openTypeForm(event, type) {
    closeForms();
    typeTarget = { eventId: event.id, typeId: type ? type.id : null };
    typeForm.reset();
    $('[data-type-form-title]').textContent = type ? `Editar categoria — ${type.name}` : `Nova categoria — ${event.title}`;
    if (type) {
      typeForm.elements.name.value = type.name;
      typeForm.elements.description.value = type.description ?? '';
      typeForm.elements.price.value = (type.priceMinor / 100).toFixed(2);
      typeForm.elements.capacity.value = type.capacity;
      typeForm.elements.maxPerOrder.value = type.maxPerOrder;
      typeForm.elements.saleStartsAt.value = toLocalInput(type.saleStartsAt);
      typeForm.elements.saleEndsAt.value = toLocalInput(type.saleEndsAt);
    }
    typeForm.hidden = false;
    typeForm.elements.name.focus();
  }

  $('[data-new-event]').addEventListener('click', () => openEventForm(null));
  $('[data-refresh-events]').addEventListener('click', () => loadEvents().then(() => say(message, '')).catch((error) => say(message, error.message)));

  eventForm.addEventListener('submit', async (submitEvent) => {
    submitEvent.preventDefault();
    const f = eventForm.elements;
    const body = {
      slug: f.slug.value.trim(), title: f.title.value.trim(), location: f.location.value.trim(), status: f.status.value,
      description: f.description.value.trim() || null, startsAt: toInstant(f.startsAt.value), endsAt: toInstant(f.endsAt.value)
    };
    try {
      await api(editingEventId ? `/api/admin/events/${editingEventId}` : '/api/admin/events', { method: editingEventId ? 'PUT' : 'POST', body: JSON.stringify(body) });
      closeForms();
      await loadEvents();
      say(message, 'Evento guardado.', true);
    } catch (error) { say(message, error.message); }
  });

  typeForm.addEventListener('submit', async (submitEvent) => {
    submitEvent.preventDefault();
    const f = typeForm.elements;
    const body = {
      name: f.name.value.trim(), description: f.description.value.trim() || null,
      priceMinor: Math.round(Number(f.price.value) * 100), capacity: Number(f.capacity.value), maxPerOrder: Number(f.maxPerOrder.value),
      saleStartsAt: toInstant(f.saleStartsAt.value), saleEndsAt: toInstant(f.saleEndsAt.value)
    };
    const path = typeTarget.typeId ? `/api/admin/ticket-types/${typeTarget.typeId}` : `/api/admin/events/${typeTarget.eventId}/ticket-types`;
    try {
      await api(path, { method: typeTarget.typeId ? 'PUT' : 'POST', body: JSON.stringify(body) });
      closeForms();
      await loadEvents();
      say(message, 'Categoria guardada.', true);
    } catch (error) { say(message, error.message); }
  });

  function renderEvents(events) {
    eventsBox.replaceChildren();
    eventsEmpty.hidden = events.length > 0;
    for (const event of events) {
      const info = node('div');
      info.append(node('h3', event.title), node('p', `${showDate(event.startsAt)} · ${event.location} · /${event.slug}`));
      const actions = node('div', undefined, 'admin-row-actions');
      const edit = actionButton('Editar evento', () => openEventForm(event));
      const add = actionButton('Adicionar categoria', () => openTypeForm(event, null));
      actions.append(badge(event.status, eventStatusLabels), edit, add);
      const head = node('div', undefined, 'admin-event-head');
      head.append(info, actions);

      const table = node('table', undefined, 'admin-table');
      const headRow = table.createTHead().insertRow();
      ['Categoria', 'Preço', 'Capacidade', 'Vendidos', 'Reservados', 'Disponíveis', 'Janela de venda', ''].forEach((label) => headRow.append(node('th', label)));
      const body = table.createTBody();
      for (const type of event.ticketTypes) {
        const tr = body.insertRow();
        cell(tr, type.name); cell(tr, money(type.priceMinor)); cell(tr, type.capacity); cell(tr, type.sold); cell(tr, type.held); cell(tr, type.available);
        cell(tr, type.saleStartsAt || type.saleEndsAt ? `${showDate(type.saleStartsAt)} → ${showDate(type.saleEndsAt)}` : 'Sempre');
        cell(tr, '').append(actionButton('Editar', () => openTypeForm(event, type)));
      }
      const wrap = node('div', undefined, 'admin-table-wrap');
      wrap.style.border = '0';
      if (event.ticketTypes.length) wrap.append(table); else wrap.append(node('p', 'Sem categorias de bilhetes.', 'admin-empty'));
      const article = node('article', undefined, 'admin-event');
      article.append(head, wrap);
      eventsBox.append(article);
    }
  }

  async function loadEvents() {
    eventsCache = await api('/api/admin/events');
    renderEvents(eventsCache);
  }

  // ---- Orders --------------------------------------------------------------
  const orderRows = $('[data-order-rows]'), ordersEmpty = $('[data-orders-empty]'), orderFilter = $('[data-order-filter]');

  async function loadOrders() {
    eventsCache = await api('/api/admin/events');
    const names = new Map(eventsCache.flatMap((event) => event.ticketTypes.map((type) => [type.id, `${type.name} (${event.title})`])));
    const query = orderFilter.value ? `?status=${encodeURIComponent(orderFilter.value)}&size=100` : '?size=100';
    const page = await api('/api/admin/orders' + query);
    orderRows.replaceChildren();
    ordersEmpty.hidden = page.content.length > 0;
    for (const order of page.content) {
      const tr = orderRows.insertRow();
      cell(tr, order.reference);
      cell(tr, [order.fullName, order.phone, order.email].filter(Boolean).join('\n'), 'admin-notes');
      cell(tr, order.items.map((line) => `${line.quantity} × ${names.get(line.ticketTypeId) || `#${line.ticketTypeId}`}`).join('\n'), 'admin-notes');
      cell(tr, money(order.totalMinor));
      cell(tr, '').append(badge(order.status, orderStatusLabels));
      cell(tr, showDate(order.expiresAt));
      const actions = cell(tr, '');
      if (order.status === 'PENDING') {
        const button = actionButton('Cancelar', async () => {
          if (!confirm(`Cancelar a encomenda ${order.reference} e libertar os bilhetes?`)) return;
          button.disabled = true;
          try {
            await api(`/api/admin/orders/${encodeURIComponent(order.reference)}/cancel`, { method: 'POST' });
            await loadOrders();
            say(message, `${order.reference}: cancelada.`, true);
          } catch (error) { say(message, error.message); button.disabled = false; }
        });
        actions.append(button);
      }
    }
  }
  orderFilter.addEventListener('change', () => loadOrders().catch((error) => say(message, error.message)));
  $('[data-refresh-orders]').addEventListener('click', () => loadOrders().then(() => say(message, '')).catch((error) => say(message, error.message)));
})();
