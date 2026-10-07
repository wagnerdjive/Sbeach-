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
      headers: { Accept: 'application/json', Authorization: authHeader, ...(options.body && !(options.body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}) }
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

  // ---- WhatsApp -------------------------------------------------------------
  // Opens a chat with the customer in the staff member's own WhatsApp (app or web) with the message already written;
  // staff read it, adjust it if they want, and press send. Nothing is sent from the server.
  const MOZAMBIQUE = '258';
  function whatsappNumber(phone) {
    // Leading zeros are a dialling prefix (00 258…) or a local trunk zero (082…), never part of the number.
    let digits = String(phone || '').replace(/\D/g, '').replace(/^0+/, '');
    if (digits.length === 9 && digits.startsWith('8')) digits = MOZAMBIQUE + digits; // local Mozambican number without +258
    return digits.length >= 10 && digits.length <= 15 ? digits : null;
  }
  function whatsappButton(phone, text) {
    const number = whatsappNumber(phone);
    const button = actionButton('WhatsApp', () => window.open(`https://wa.me/${number}?text=${encodeURIComponent(text)}`, '_blank', 'noopener'));
    button.className = 'whatsapp-button';
    if (!number) { button.disabled = true; button.title = 'Número sem indicativo de país: não é possível abrir o WhatsApp.'; }
    else button.title = `Abrir conversa com ${phone}`;
    return button;
  }
  const dmy = (isoDate) => isoDate.split('-').reverse().join('/');
  function reservationMessage(r) {
    const when = `${dmy(r.requestedDate)} às ${String(r.requestedTime).slice(0, 5)}`;
    const who = `${r.partySize} pessoa${r.partySize === 1 ? '' : 's'}`;
    const place = r.venue === 'NO_PREFERENCE' ? 'South Beach' : venueLabels[r.venue];
    if (r.status === 'CONFIRMED') return `Olá ${r.fullName}! A sua reserva ${r.reference} no South Beach está confirmada para ${when} (${who}, ${place}). Até breve! Para alterar, responda a esta mensagem.`;
    if (r.status === 'CANCELLED') return `Olá ${r.fullName}. A sua reserva ${r.reference} (${dmy(r.requestedDate)}) no South Beach foi cancelada. Se quiser reservar outra data, responda a esta mensagem.`;
    return `Olá ${r.fullName}! Recebemos o seu pedido de reserva ${r.reference} para ${when} (${who}, ${place}). Vamos confirmar a disponibilidade consigo em breve.`;
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
      if (r.status === 'CANCELLED') { actions.append(whatsappButton(r.phone, reservationMessage(r))); continue; }
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
      actions.append(...buttons, whatsappButton(r.phone, reservationMessage(r)));
    }
  }

  async function load() {
    const query = filter.value ? `?status=${encodeURIComponent(filter.value)}&size=100` : '?size=100';
    const page = await api('/api/admin/reservations' + query);
    render(page.content || []);
  }

  function signOut() {
    authHeader = null;
    if (typeof stopCamera === 'function') stopCamera();
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
  const loaders = { reservations: () => load(), events: () => loadEvents(), orders: () => loadOrders(), entry: () => loadEntry(), content: () => loadContent(), menu: () => loadMenu(), gallery: () => loadGallery(), reports: () => loadReports() };
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
  const orderStatusLabels = { PENDING: 'Pendente', PAID: 'Paga', EXPIRED: 'Expirada', CANCELLED: 'Cancelada', REFUNDED: 'Reembolsada' };

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

  const posterInput = eventForm.elements.poster, posterPreview = $('[data-poster-preview]');
  const posterImage = posterPreview.querySelector('img');
  const posterLimit = 2 * 1024 * 1024, posterTypes = ['image/jpeg', 'image/png', 'image/webp'];
  let objectUrls = [];
  const trackUrl = (blob) => { const url = URL.createObjectURL(blob); objectUrls.push(url); return url; };
  const releaseUrls = () => { objectUrls.forEach((url) => URL.revokeObjectURL(url)); objectUrls = []; };

  // The poster of a draft event is not public, so staff load it with their credentials.
  async function posterBlobUrl(eventId) {
    const response = await fetch(`${apiOrigin}/api/admin/events/${eventId}/poster`, { headers: { Authorization: authHeader } });
    if (!response.ok) return null;
    return trackUrl(await response.blob());
  }
  function showPoster(url) {
    posterImage.src = url || '';
    posterPreview.hidden = !url;
    $('[data-remove-poster]').hidden = !(editingEventId && eventsCache.find((e) => e.id === editingEventId)?.posterUrl && posterInput.files.length === 0);
  }
  posterInput.addEventListener('change', () => {
    const file = posterInput.files[0];
    if (!file) { showPoster(null); return; }
    if (!posterTypes.includes(file.type) || file.size > posterLimit) {
      posterInput.value = '';
      say(message, 'O cartaz deve ser JPEG, PNG ou WebP com até 2 MB.');
      showPoster(null);
      return;
    }
    say(message, '');
    showPoster(trackUrl(file));
  });
  $('[data-remove-poster]').addEventListener('click', async () => {
    if (!editingEventId || !confirm('Remover o cartaz deste evento?')) return;
    try {
      await api(`/api/admin/events/${editingEventId}/poster`, { method: 'DELETE' });
      await loadEvents();
      showPoster(null);
      say(message, 'Cartaz removido.', true);
    } catch (error) { say(message, error.message); }
  });

  function closeForms() { eventForm.hidden = true; typeForm.hidden = true; releaseUrls(); }
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
    showPoster(null);
    if (event && event.posterUrl) posterBlobUrl(event.id).then((url) => { if (editingEventId === event.id && !eventForm.hidden) showPoster(url); });
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
      const saved = await api(editingEventId ? `/api/admin/events/${editingEventId}` : '/api/admin/events', { method: editingEventId ? 'PUT' : 'POST', body: JSON.stringify(body) });
      const file = posterInput.files[0];
      let posterError = null;
      if (file) {
        const upload = new FormData();
        upload.append('file', file);
        try { await api(`/api/admin/events/${saved.id}/poster`, { method: 'PUT', body: upload }); }
        catch (error) { posterError = error; }
      }
      closeForms();
      await loadEvents();
      if (posterError) say(message, `Evento guardado, mas o cartaz não foi carregado: ${posterError.message}`);
      else say(message, file ? 'Evento e cartaz guardados.' : 'Evento guardado.', true);
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
      if (event.posterUrl) {
        const thumb = node('img', undefined, 'admin-poster-thumb');
        thumb.alt = `Cartaz de ${event.title}`;
        posterBlobUrl(event.id).then((url) => { if (url) thumb.src = url; });
        head.append(thumb);
      }
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
    releaseUrls();
    eventsCache = await api('/api/admin/events');
    renderEvents(eventsCache);
  }

  // ---- Orders --------------------------------------------------------------
  const orderRows = $('[data-order-rows]'), ordersEmpty = $('[data-orders-empty]'), orderFilter = $('[data-order-filter]');

  async function loadOrders() {
    eventsCache = await api('/api/admin/events');
    const names = new Map(eventsCache.flatMap((event) => event.ticketTypes.map((type) => [type.id, `${type.name} (${event.title})`])));
    const eventOfType = new Map(eventsCache.flatMap((event) => event.ticketTypes.map((type) => [type.id, event.title])));
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
      actions.className = 'admin-row-actions';
      if (order.status === 'PENDING') {
        const pay = actionButton('Marcar como paga', async () => {
          if (!confirm(`Confirma que recebeu o pagamento de ${money(order.totalMinor)} da encomenda ${order.reference}? Isto emite os bilhetes.`)) return;
          pay.disabled = true;
          try {
            await api(`/api/admin/orders/${encodeURIComponent(order.reference)}/mark-paid`, { method: 'POST' });
            await loadOrders();
            say(message, `${order.reference}: paga. Bilhetes emitidos — copie a ligação para os enviar ao cliente.`, true);
          } catch (error) { say(message, error.message); pay.disabled = false; }
        });
        actions.append(pay);
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
      if (order.status === 'PAID') {
        const refund = actionButton('Reembolsar', async () => {
          if (!confirm(`Só confirme depois de ter devolvido ${money(order.totalMinor)} a ${order.fullName}.\n\nOs bilhetes da encomenda ${order.reference} ficam anulados e os lugares voltam à venda. Não pode ser desfeito.`)) return;
          const note = prompt('Nota do reembolso (opcional, ex.: motivo e como foi devolvido):', '');
          if (note === null) return;
          refund.disabled = true;
          try {
            await api(`/api/admin/orders/${encodeURIComponent(order.reference)}/refund`, { method: 'POST', body: JSON.stringify({ note: note.trim() || null }) });
            await loadOrders();
            say(message, `${order.reference}: reembolsada. Bilhetes anulados.`, true);
          } catch (error) { say(message, error.message); refund.disabled = false; }
        });
        actions.append(refund);
      }
      if (order.status === 'PENDING' || order.status === 'PAID') {
        const title = eventOfType.get(order.items[0]?.ticketTypeId) || 'South Beach';
        const text = order.status === 'PAID' && order.ticketsUrl
          ? `Olá ${order.fullName}! Recebemos o seu pagamento (encomenda ${order.reference}, ${title}). Os seus bilhetes com QR estão aqui: ${order.ticketsUrl} — mostre o QR à entrada. Até breve no South Beach!`
          : `Olá ${order.fullName}! Reservámos os seus bilhetes para ${title} (encomenda ${order.reference}, total ${money(order.totalMinor)}) até às ${new Intl.DateTimeFormat('pt-PT', { timeZone: 'Africa/Maputo', timeStyle: 'short' }).format(new Date(order.expiresAt))}. Como prefere pagar? M-Pesa, e-Mola ou numerário no local.`;
        actions.append(whatsappButton(order.phone, text));
      }
      if (order.refundNote) actions.append(node('small', order.refundNote, 'content-key'));
      if (order.ticketsUrl) {
        actions.append(actionButton('Copiar ligação dos bilhetes', async () => {
          try { await navigator.clipboard.writeText(order.ticketsUrl); say(message, 'Ligação copiada. Envie-a ao cliente.', true); }
          catch (_) { prompt('Copie a ligação dos bilhetes:', order.ticketsUrl); }
        }), actionButton('Abrir', () => window.open(order.ticketsUrl, '_blank', 'noopener')));
      }
    }
  }

  // ---- Entry (gate) ---------------------------------------------------------
  const entryEvent = $('[data-entry-event]'), entryForm = $('[data-entry-form]'), entryResult = $('[data-entry-result]');
  const entryStats = $('[data-entry-stats]'), cameraButton = $('[data-entry-camera]'), video = $('[data-entry-video]');
  const outcomes = {
    ADMITTED: ['ok', 'ENTRADA AUTORIZADA', (r) => r.ticketType],
    ALREADY_USED: ['bad', 'JÁ UTILIZADO', (r) => `Entrou às ${showDate(r.usedAt)} · ${r.ticketType || ''}`],
    WRONG_EVENT: ['bad', 'OUTRO EVENTO', (r) => `Este bilhete é de: ${r.eventTitle || 'outro evento'}`],
    VOID: ['bad', 'BILHETE ANULADO', () => ''],
    NOT_FOUND: ['bad', 'CÓDIGO DESCONHECIDO', () => 'Confira o código ou peça outro bilhete.']
  };

  async function refreshStats() {
    if (!entryEvent.value) { entryStats.textContent = ''; return; }
    const stats = await api(`/api/admin/events/${entryEvent.value}/entry-stats`);
    entryStats.textContent = `Entraram ${stats.admitted} de ${stats.issued} bilhetes emitidos`;
  }

  async function loadEntry() {
    const events = await api('/api/admin/events');
    const previous = entryEvent.value;
    entryEvent.replaceChildren(...events.filter((event) => event.status !== 'CANCELLED').map((event) => new Option(`${event.title} — ${showDate(event.startsAt)}`, event.id)));
    if (previous) entryEvent.value = previous;
    await refreshStats();
    entryForm.elements.code.focus();
  }

  async function validate(code) {
    if (!entryEvent.value) { say(message, 'Escolha o evento.'); return; }
    try {
      const result = await api('/api/admin/check-in', { method: 'POST', body: JSON.stringify({ eventId: Number(entryEvent.value), code }) });
      const [kind, title, detail] = outcomes[result.outcome] || ['bad', result.outcome, () => ''];
      entryResult.className = `entry-result ${kind}`;
      entryResult.replaceChildren(node('strong', title), node('span', detail(result)));
      await refreshStats();
    } catch (error) { entryResult.className = 'entry-result bad'; entryResult.replaceChildren(node('strong', 'ERRO'), node('span', error.message)); }
  }

  entryEvent.addEventListener('change', () => refreshStats().catch((error) => say(message, error.message)));
  entryForm.addEventListener('submit', async (submitEvent) => {
    submitEvent.preventDefault();
    const input = entryForm.elements.code;
    const code = input.value.trim();
    input.value = '';
    if (code) await validate(code);
    input.focus();
  });

  // Camera scanning needs the browser's BarcodeDetector (Chrome/Android) and a secure page (https or localhost).
  let stream = null, scanTimer = null, lastScan = { code: '', at: 0 };
  function stopCamera() {
    clearInterval(scanTimer); scanTimer = null;
    if (stream) stream.getTracks().forEach((track) => track.stop());
    stream = null; video.hidden = true; cameraButton.textContent = 'Usar a câmara';
  }
  if ('BarcodeDetector' in window && navigator.mediaDevices?.getUserMedia) {
    cameraButton.hidden = false;
    cameraButton.addEventListener('click', async () => {
      if (stream) { stopCamera(); return; }
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
        video.srcObject = stream; video.hidden = false; await video.play();
        cameraButton.textContent = 'Parar a câmara';
        const detector = new BarcodeDetector({ formats: ['qr_code'] });
        scanTimer = setInterval(async () => {
          try {
            const [found] = await detector.detect(video);
            const code = found?.rawValue?.trim();
            // The same QR stays in view for a while: ignore repeats within 4 seconds.
            if (code && (code !== lastScan.code || Date.now() - lastScan.at > 4000)) { lastScan = { code, at: Date.now() }; await validate(code); }
          } catch (_) { /* a frame without a readable code */ }
        }, 400);
      } catch (_) { say(message, 'Não foi possível abrir a câmara. Use o campo de código ou um leitor de QR.'); stopCamera(); }
    });
  }
  tabs.forEach((tab) => tab.addEventListener('click', () => { if (tab.dataset.tab !== 'entry') stopCamera(); }));

  // ---- Sales reports ----------------------------------------------------------
  const reportEvent = $('[data-report-event]'), reportTiles = $('[data-report-tiles]');
  const dayFormat = new Intl.DateTimeFormat('pt-PT', { day: '2-digit', month: 'short', timeZone: 'UTC' });

  function tile(label, value, hint) {
    const box = node('div', undefined, 'report-tile');
    box.append(node('span', label), node('strong', value));
    if (hint) box.append(node('small', hint));
    return box;
  }

  function renderReport(report) {
    reportTiles.replaceChildren(
      tile('Receita (paga)', money(report.revenueMinor)),
      tile('Bilhetes vendidos', String(report.ticketsSold), `${report.paidOrders} encomenda(s) paga(s)`),
      ...(report.refundedMinor ? [tile('Reembolsado', money(report.refundedMinor), 'já descontado da receita')] : []),
      tile('Entraram', String(report.admitted), report.ticketsSold ? `${Math.round((report.admitted / report.ticketsSold) * 100)}% dos vendidos` : ''),
      tile('Reservados (por pagar)', String(report.byType.reduce((sum, type) => sum + type.held, 0))));
    const types = $('[data-report-types]');
    types.replaceChildren();
    report.byType.forEach((type) => {
      const tr = types.insertRow();
      cell(tr, type.name); cell(tr, money(type.priceMinor)); cell(tr, type.capacity); cell(tr, type.sold); cell(tr, type.held);
      cell(tr, type.available); cell(tr, money(type.revenueMinor)); cell(tr, type.admitted);
    });
    const days = $('[data-report-days]');
    days.replaceChildren();
    if (!report.byDay.length) days.append(node('p', 'Ainda não há vendas pagas.', 'admin-empty'));
    const peak = Math.max(1, ...report.byDay.map((day) => day.tickets));
    report.byDay.forEach((day) => {
      const bar = node('div', undefined, 'report-bar');
      bar.style.setProperty('--w', `${Math.max(2, Math.round((day.tickets / peak) * 100))}%`);
      bar.append(node('span', dayFormat.format(new Date(`${day.date}T00:00:00Z`))), node('i'), node('strong', `${day.tickets} bilhete(s) · ${money(day.revenueMinor)}`));
      days.append(bar);
    });
    const statuses = $('[data-report-statuses]');
    statuses.replaceChildren();
    if (!report.byStatus.length) statuses.append(node('p', 'Sem encomendas.', 'admin-empty'));
    report.byStatus.forEach((row) => {
      const chip = node('span', undefined, 'report-chip');
      chip.append(badge(row.status, orderStatusLabels), document.createTextNode(` ${row.orders} encomenda(s) · ${row.tickets} bilhete(s)`));
      statuses.append(chip);
    });
  }

  async function loadReports() {
    const all = await api('/api/admin/reports/events');
    const previous = reportEvent.value;
    reportEvent.replaceChildren(...all.map((report) => new Option(`${report.title} — ${showDate(report.startsAt)}`, report.eventId)));
    if (previous && all.some((report) => String(report.eventId) === previous)) reportEvent.value = previous;
    const rows = $('[data-report-all]');
    rows.replaceChildren();
    all.forEach((report) => {
      const tr = rows.insertRow();
      cell(tr, report.title); cell(tr, showDate(report.startsAt)); cell(tr, report.ticketsSold); cell(tr, money(report.revenueMinor)); cell(tr, report.admitted);
    });
    const current = all.find((report) => String(report.eventId) === reportEvent.value);
    if (current) renderReport(current);
    else { reportTiles.replaceChildren(); $('[data-report-types]').replaceChildren(); $('[data-report-days]').replaceChildren(); $('[data-report-statuses]').replaceChildren(); }
  }

  reportEvent.addEventListener('change', () => loadReports().catch((error) => say(message, error.message)));
  $('[data-refresh-report]').addEventListener('click', () => loadReports().then(() => say(message, '')).catch((error) => say(message, error.message)));
  $('[data-report-csv]').addEventListener('click', async () => {
    if (!reportEvent.value) { say(message, 'Escolha um evento.'); return; }
    try {
      const response = await fetch(`${apiOrigin}/api/admin/reports/events/${reportEvent.value}/orders.csv`, { headers: { Authorization: authHeader } });
      if (!response.ok) throw new Error('Não foi possível gerar o ficheiro.');
      const url = URL.createObjectURL(await response.blob());
      const link = document.createElement('a');
      link.href = url; link.download = `encomendas-evento-${reportEvent.value}.csv`;
      document.body.append(link); link.click(); link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) { say(message, error.message); }
  });

  // ---- Gallery ---------------------------------------------------------------
  const photoForm = $('[data-photo-form]'), photoGrid = $('[data-photo-grid]'), photoEmpty = $('[data-photo-empty]');
  const photoPreview = $('[data-photo-preview]'), photoPreviewImage = photoPreview.querySelector('img');
  const categoryLabels = { SPACES: 'Espaços', EVENTS: 'Eventos passados' }, sizeLabels = { NORMAL: 'Normal', WIDE: 'Larga', TALL: 'Alta' };
  let photosCache = [], editingPhotoId = null, uploadedUrl = null;
  const imageSrc = (url) => (url && url.startsWith('/api/') ? `${apiOrigin}${url}` : url);

  function closePhotoForm() { photoForm.hidden = true; photoForm.reset(); uploadedUrl = null; photoPreview.hidden = true; }
  document.querySelectorAll('[data-cancel-form]').forEach((button) => button.addEventListener('click', closePhotoForm));

  function openPhotoForm(photo) {
    closeForms(); closePhotoForm();
    editingPhotoId = photo ? photo.id : null;
    $('[data-photo-form-title]').textContent = photo ? 'Editar fotografia' : 'Nova fotografia';
    const f = photoForm.elements;
    if (photo) {
      f.imageUrl.value = photo.imageUrl; f.captionPt.value = photo.captionPt; f.captionEn.value = photo.captionEn ?? '';
      f.category.value = photo.category; f.size.value = photo.size; f.visible.value = String(photo.visible);
      photoPreviewImage.src = imageSrc(photo.imageUrl); photoPreview.hidden = false;
    }
    photoForm.hidden = false;
    f.captionPt.focus();
  }

  photoForm.elements.file.addEventListener('change', async () => {
    const file = photoForm.elements.file.files[0];
    if (!file) return;
    if (file.size > 3 * 1024 * 1024) { say(message, 'A imagem deve ter até 3 MB.'); photoForm.elements.file.value = ''; return; }
    const upload = new FormData(); upload.append('file', file);
    try {
      uploadedUrl = (await api('/api/admin/media', { method: 'POST', body: upload })).url;
      photoForm.elements.imageUrl.value = uploadedUrl;
      photoPreviewImage.src = imageSrc(uploadedUrl); photoPreview.hidden = false;
      say(message, 'Imagem carregada. Preencha a legenda e guarde.', true);
    } catch (error) { say(message, error.message); }
    photoForm.elements.file.value = '';
  });
  photoForm.elements.imageUrl.addEventListener('change', () => {
    const value = photoForm.elements.imageUrl.value.trim();
    photoPreview.hidden = !value; if (value) photoPreviewImage.src = imageSrc(value);
  });

  photoForm.addEventListener('submit', async (submitEvent) => {
    submitEvent.preventDefault();
    const f = photoForm.elements;
    const body = { imageUrl: f.imageUrl.value.trim(), category: f.category.value, size: f.size.value, captionPt: f.captionPt.value.trim(),
      captionEn: f.captionEn.value.trim() || null, visible: f.visible.value === 'true' };
    if (!body.imageUrl) { say(message, 'Carregue uma imagem ou indique o endereço https.'); return; }
    try {
      await api(editingPhotoId ? `/api/admin/gallery/${editingPhotoId}` : '/api/admin/gallery', { method: editingPhotoId ? 'PUT' : 'POST', body: JSON.stringify(body) });
      closePhotoForm(); await loadGallery(); say(message, 'Fotografia guardada.', true);
    } catch (error) { say(message, error.message); }
  });
  $('[data-new-photo]').addEventListener('click', () => openPhotoForm(null));
  $('[data-refresh-gallery]').addEventListener('click', () => loadGallery().then(() => say(message, '')).catch((error) => say(message, error.message)));

  async function move(index, delta) {
    const target = index + delta;
    if (target < 0 || target >= photosCache.length) return;
    const ids = photosCache.map((photo) => photo.id);
    [ids[index], ids[target]] = [ids[target], ids[index]];
    try { photosCache = await api('/api/admin/gallery-order', { method: 'PUT', body: JSON.stringify({ ids }) }); renderGallery(); say(message, 'Ordem guardada.', true); }
    catch (error) { say(message, error.message); }
  }

  function renderGallery() {
    photoGrid.replaceChildren();
    photoEmpty.hidden = photosCache.length > 0;
    photosCache.forEach((photo, index) => {
      const image = node('img'); image.src = imageSrc(photo.imageUrl); image.alt = photo.captionPt; image.loading = 'lazy';
      const tags = node('p', `${categoryLabels[photo.category]} · ${sizeLabels[photo.size]}${photo.visible ? '' : ' · OCULTA'}`, 'photo-tags');
      const buttons = node('div', undefined, 'admin-row-actions');
      const up = actionButton('↑', () => move(index, -1)); up.disabled = index === 0; up.setAttribute('aria-label', `Mover ${photo.captionPt} para cima`);
      const down = actionButton('↓', () => move(index, 1)); down.disabled = index === photosCache.length - 1; down.setAttribute('aria-label', `Mover ${photo.captionPt} para baixo`);
      const remove = actionButton('Remover', async () => {
        if (!confirm(`Remover “${photo.captionPt}” da galeria?`)) return;
        try { await api(`/api/admin/gallery/${photo.id}`, { method: 'DELETE' }); await loadGallery(); say(message, 'Fotografia removida.', true); }
        catch (error) { say(message, error.message); }
      });
      buttons.append(up, down, actionButton('Editar', () => openPhotoForm(photo)), remove);
      const card = node('article', undefined, photo.visible ? 'photo-card' : 'photo-card photo-hidden');
      card.append(image, node('h3', photo.captionPt), tags, buttons);
      photoGrid.append(card);
    });
  }

  async function loadGallery() { photosCache = await api('/api/admin/gallery'); renderGallery(); }

  // ---- Menus ------------------------------------------------------------------
  const menuForm = $('[data-menu-form]'), menuRows = $('[data-menu-rows]'), menuEmpty = $('[data-menu-empty]');
  const groupForm = $('[data-group-form]'), groupLists = $('[data-group-lists]');
  const filterVenue = $('[data-filter-venue]'), filterGroup = $('[data-filter-group]');
  const menuVenueLabels = { RESTAURANT: 'Restaurante', BEACH: 'Beach Bar', SPORTS: 'Sports Bar' }, menuKindLabels = { FOOD: 'Comida', DRINKS: 'Bebidas' };
  let menuCache = [], groupsCache = [], editingMenuId = null, editingGroupId = null, menuLimit = 100;

  const groupsOf = (venue) => groupsCache.filter((group) => group.venue === venue);

  // ---- Menu divisions
  function resetGroupForm() {
    editingGroupId = null; groupForm.reset(); groupForm.elements.venue.disabled = false;
    $('[data-group-submit]').textContent = 'Adicionar divisão'; $('[data-cancel-group]').hidden = true;
  }
  $('[data-cancel-group]').addEventListener('click', resetGroupForm);

  groupForm.addEventListener('submit', async (submitEvent) => {
    submitEvent.preventDefault();
    const f = groupForm.elements;
    const body = { venue: f.venue.value, namePt: f.namePt.value.trim(), nameEn: f.nameEn.value.trim() || null };
    try {
      await api(editingGroupId ? `/api/admin/menu-groups/${editingGroupId}` : '/api/admin/menu-groups', { method: editingGroupId ? 'PUT' : 'POST', body: JSON.stringify(body) });
      resetGroupForm(); await loadMenu(); say(message, 'Divisão guardada.', true);
    } catch (error) { say(message, error.message); }
  });

  async function moveGroup(venue, index, delta) {
    const list = groupsOf(venue), target = index + delta;
    if (target < 0 || target >= list.length) return;
    const ids = list.map((group) => group.id);
    [ids[index], ids[target]] = [ids[target], ids[index]];
    try { groupsCache = await api('/api/admin/menu-group-order', { method: 'PUT', body: JSON.stringify({ venue, ids }) }); renderGroups(); say(message, 'Ordem guardada.', true); }
    catch (error) { say(message, error.message); }
  }

  function renderGroups() {
    groupLists.replaceChildren();
    Object.entries(menuVenueLabels).forEach(([venue, label]) => {
      const list = groupsOf(venue);
      const block = node('div', undefined, 'menu-group-block');
      block.append(node('h3', `${label} — ordem de apresentação`));
      if (!list.length) block.append(node('p', 'Ainda sem divisões. Os itens deste espaço aparecem numa só lista.', 'admin-hint'));
      list.forEach((group, index) => {
        const count = menuCache.filter((item) => item.groupId === group.id).length;
        const row = node('div', undefined, 'menu-group-row');
        row.append(node('span', `${index + 1}. ${group.namePt}${group.nameEn ? ` / ${group.nameEn}` : ''}`, 'menu-group-name'), node('small', `${count} ${count === 1 ? 'item' : 'itens'}`));
        const buttons = node('div', undefined, 'admin-row-actions');
        const up = actionButton('↑', () => moveGroup(venue, index, -1)); up.disabled = index === 0; up.setAttribute('aria-label', `Mover ${group.namePt} para cima`);
        const down = actionButton('↓', () => moveGroup(venue, index, 1)); down.disabled = index === list.length - 1; down.setAttribute('aria-label', `Mover ${group.namePt} para baixo`);
        const edit = actionButton('Editar', () => {
          editingGroupId = group.id; const f = groupForm.elements;
          f.venue.value = group.venue; f.venue.disabled = true; f.namePt.value = group.namePt; f.nameEn.value = group.nameEn ?? '';
          $('[data-group-submit]').textContent = 'Guardar divisão'; $('[data-cancel-group]').hidden = false; groupForm.scrollIntoView({ block: 'nearest' }); f.namePt.focus();
        });
        const remove = actionButton('Remover', async () => {
          if (count) { say(message, `“${group.namePt}” ainda tem ${count} itens. Mude-os de divisão ou remova-os primeiro.`); return; }
          if (!confirm(`Remover a divisão “${group.namePt}”?`)) return;
          try { await api(`/api/admin/menu-groups/${group.id}`, { method: 'DELETE' }); await loadMenu(); say(message, 'Divisão removida.', true); }
          catch (error) { say(message, error.message); }
        });
        buttons.append(up, down, edit, remove); row.append(buttons); block.append(row);
      });
      groupLists.append(block);
    });
    fillFilterGroups();
  }

  function fillFilterGroups() {
    const venue = filterVenue.value, keep = filterGroup.value;
    filterGroup.replaceChildren(new Option('Todas', ''), new Option('Sem divisão', 'none'));
    (venue ? groupsOf(venue) : groupsCache).forEach((group) => filterGroup.append(new Option(venue ? group.namePt : `${menuVenueLabels[group.venue]} · ${group.namePt}`, String(group.id))));
    filterGroup.value = [...filterGroup.options].some((option) => option.value === keep) ? keep : '';
  }
  filterVenue.addEventListener('change', () => { fillFilterGroups(); menuLimit = 100; renderMenu(); });
  filterGroup.addEventListener('change', () => { menuLimit = 100; renderMenu(); });

  // ---- Menu items
  function fillItemGroups(selected) {
    const venue = menuForm.elements.venue.value;
    const select = menuForm.elements.groupId;
    select.replaceChildren(new Option('— Sem divisão —', ''));
    groupsOf(venue).forEach((group) => select.append(new Option(group.namePt, String(group.id))));
    select.value = selected != null && groupsOf(venue).some((group) => group.id === selected) ? String(selected) : '';
    fillSectionSuggestions();
  }
  function fillSectionSuggestions() {
    const venue = menuForm.elements.venue.value, group = menuForm.elements.groupId.value;
    const names = new Set(menuCache.filter((item) => item.venue === venue && String(item.groupId ?? '') === group).map((item) => item.sectionPt));
    $('#menu-sections').replaceChildren(...[...names].map((name) => new Option(name)));
  }
  menuForm.elements.venue.addEventListener('change', () => fillItemGroups(null));
  menuForm.elements.groupId.addEventListener('change', fillSectionSuggestions);

  function closeMenuForm() { menuForm.hidden = true; menuForm.reset(); editingMenuId = null; }
  $('[data-cancel-menu-form]').addEventListener('click', closeMenuForm);

  function openMenuForm(item) {
    closeForms(); closeMenuForm();
    editingMenuId = item ? item.id : null;
    $('[data-menu-form-title]').textContent = item ? 'Editar item' : 'Novo item';
    const f = menuForm.elements;
    if (item) {
      f.venue.value = item.venue; f.kind.value = item.kind; f.sectionPt.value = item.sectionPt; f.sectionEn.value = item.sectionEn ?? '';
      f.namePt.value = item.namePt; f.nameEn.value = item.nameEn ?? ''; f.descriptionPt.value = item.descriptionPt ?? ''; f.descriptionEn.value = item.descriptionEn ?? '';
      f.price.value = item.priceCents == null ? '' : String(item.priceCents / 100); f.visible.value = String(item.visible);
    } else if (filterVenue.value) f.venue.value = filterVenue.value;
    fillItemGroups(item ? item.groupId : (/^\d+$/.test(filterGroup.value) ? Number(filterGroup.value) : null));
    menuForm.hidden = false;
    f.namePt.focus();
  }

  menuForm.addEventListener('submit', async (submitEvent) => {
    submitEvent.preventDefault();
    const f = menuForm.elements, text = (field) => f[field].value.trim() || null;
    const price = f.price.value.trim();
    const body = { venue: f.venue.value, kind: f.kind.value, groupId: f.groupId.value ? Number(f.groupId.value) : null,
      sectionPt: f.sectionPt.value.trim(), sectionEn: text('sectionEn'), namePt: f.namePt.value.trim(),
      nameEn: text('nameEn'), descriptionPt: text('descriptionPt'), descriptionEn: text('descriptionEn'),
      priceCents: price === '' ? null : Math.round(Number(price) * 100), visible: f.visible.value === 'true' };
    try {
      await api(editingMenuId ? `/api/admin/menu/${editingMenuId}` : '/api/admin/menu', { method: editingMenuId ? 'PUT' : 'POST', body: JSON.stringify(body) });
      closeMenuForm(); await loadMenu(); say(message, 'Item guardado.', true);
    } catch (error) { say(message, error.message); }
  });
  $('[data-new-menu-item]').addEventListener('click', () => openMenuForm(null));
  $('[data-refresh-menu]').addEventListener('click', () => loadMenu().then(() => say(message, '')).catch((error) => say(message, error.message)));

  async function moveMenuItem(item, delta) {
    // Moves within the rows currently shown, so the arrows work with a filter on.
    const shown = menuCache.filter(matchesFilter), at = shown.indexOf(item), neighbour = shown[at + delta];
    if (!neighbour) return;
    const ids = menuCache.map((entry) => entry.id), i = ids.indexOf(item.id), k = ids.indexOf(neighbour.id);
    [ids[i], ids[k]] = [ids[k], ids[i]];
    try { menuCache = await api('/api/admin/menu-order', { method: 'PUT', body: JSON.stringify({ ids }) }); renderMenu(); say(message, 'Ordem guardada.', true); }
    catch (error) { say(message, error.message); }
  }

  function matchesFilter(item) {
    if (filterVenue.value && item.venue !== filterVenue.value) return false;
    if (filterGroup.value === 'none') return item.groupId == null;
    return !filterGroup.value || String(item.groupId) === filterGroup.value;
  }

  function renderMenu() {
    menuRows.replaceChildren();
    const shown = menuCache.filter(matchesFilter);
    menuEmpty.hidden = shown.length > 0;
    shown.slice(0, menuLimit).forEach((item, index) => {
      const tr = node('tr');
      cell(tr, menuVenueLabels[item.venue]); cell(tr, `${item.groupPt ? item.groupPt + ' · ' : ''}${item.sectionPt}`); cell(tr, item.namePt);
      cell(tr, item.priceCents == null ? '—' : money(item.priceCents)); cell(tr, item.visible ? 'Visível' : 'Oculto');
      const buttons = node('div', undefined, 'admin-row-actions');
      const up = actionButton('↑', () => moveMenuItem(item, -1)); up.disabled = index === 0; up.setAttribute('aria-label', `Mover ${item.namePt} para cima`);
      const down = actionButton('↓', () => moveMenuItem(item, 1)); down.disabled = index === shown.length - 1; down.setAttribute('aria-label', `Mover ${item.namePt} para baixo`);
      const remove = actionButton('Remover', async () => {
        if (!confirm(`Remover “${item.namePt}” do menu?`)) return;
        try { await api(`/api/admin/menu/${item.id}`, { method: 'DELETE' }); await loadMenu(); say(message, 'Item removido.', true); }
        catch (error) { say(message, error.message); }
      });
      buttons.append(up, down, actionButton('Editar', () => openMenuForm(item)), remove);
      const actions = node('td'); actions.append(buttons); tr.append(actions);
      menuRows.append(tr);
    });
    if (shown.length > menuLimit) {
      const tr = node('tr'), td = tr.insertCell();
      td.colSpan = 6;
      td.append(actionButton(`Mostrar mais (${shown.length - menuLimit} em falta)`, () => { menuLimit += 100; renderMenu(); }));
      menuRows.append(tr);
    }
  }

  async function loadMenu() {
    [groupsCache, menuCache] = await Promise.all([api('/api/admin/menu-groups'), api('/api/admin/menu')]);
    renderGroups(); renderMenu();
  }

  // ---- Site content (CMS) ---------------------------------------------------
  const contentPage = $('[data-content-page]'), contentFields = $('[data-content-fields]'), contentEmpty = $('[data-content-empty]');
  const cms = () => window.SouthBeachCms;
  const tagLabels = { H1: 'Título principal', H2: 'Título', H3: 'Subtítulo', P: 'Parágrafo', SMALL: 'Nota', A: 'Contacto' };
  let contentRows = [];
  const mediaSrc = (value) => (value && value.startsWith('/api/') ? cms().apiOrigin + value : value);

  function field(labelText, key, control) {
    const wrap = node('label', undefined, 'content-field');
    wrap.append(node('span', labelText), control);
    if (key) wrap.append(node('small', key));
    return wrap;
  }
  function textarea(value, rows) {
    const area = node('textarea');
    area.rows = rows; area.maxLength = 2000; area.value = value;
    return area;
  }
  const rowsFor = (text) => Math.min(8, Math.max(2, text.split('\n').length + Math.floor(text.length / 90)));

  function addRow(parent, row) {
    contentRows.push(row);
    parent.append(row.element);
  }

  function textRow(element, saved, doc) {
    const key = element.dataset.cms;
    const defaultPt = cms().toSource(element);
    const defaultEn = defaultPt.split('\n').map((line) => line.split(/(\*[^*]+\*)/).map((part) => {
      if (part.length > 2 && part.startsWith('*') && part.endsWith('*')) return `*${cms().translatePiece(part.slice(1, -1))}*`;
      return cms().translatePiece(part);
    }).join('')).join('\n');
    const edit = saved[key] || {};
    const pt = textarea(edit.pt ?? defaultPt, rowsFor(edit.pt ?? defaultPt)), en = textarea(edit.en ?? defaultEn, rowsFor(edit.en ?? defaultEn));
    const reset = node('button', 'Repor original'); reset.type = 'button';
    reset.addEventListener('click', () => { pt.value = defaultPt; en.value = defaultEn; });
    const warning = node('small', '', 'content-warning');
    const check = () => { warning.textContent = pt.value.trim() !== defaultPt && en.value.trim() === defaultEn ? 'O inglês ainda é a tradução do texto original: será usado o português.' : ''; };
    pt.addEventListener('input', check); en.addEventListener('input', check); check();
    const grid = node('div', undefined, 'content-pair');
    grid.append(field('Português', null, pt), field('English', null, en));
    const box = node('div', undefined, 'content-row');
    box.append(node('h4', element.classList.contains('eyebrow') ? 'Etiqueta' : (tagLabels[element.tagName] || 'Texto')), grid, warning, reset, node('small', key, 'content-key'));
    addRow(contentFields, { key, element: box, read() {
      const ptValue = pt.value.trim(), enValue = en.value.trim();
      if (ptValue === defaultPt && enValue === defaultEn) return null;
      return { pt: ptValue === defaultPt ? null : ptValue, en: enValue === defaultEn ? null : enValue, dirty: true };
    }, had: Boolean(saved[key]) });
    // A link attached to this text (phone, email) is edited right under it.
    if (element.dataset.cmsHref) linkRow(element.dataset.cmsHref, element.getAttribute('href'), saved, box);
  }

  function linkRow(key, defaultHref, saved, parent) {
    const input = node('input'); input.type = 'text'; input.maxLength = 480; input.value = saved[key]?.pt ?? defaultHref ?? '';
    const box = node('div', undefined, 'content-row content-sub');
    box.append(field('Ligação (ex.: tel:+258…, mailto:…, https://…)', key, input));
    addRow(parent, { key, element: box, read() { const value = input.value.trim(); return value === (defaultHref ?? '') ? null : { pt: value, en: null }; }, had: Boolean(saved[key]) });
  }

  function imageRow(key, label, defaultSrc, saved, isBackground) {
    let value = saved[key]?.pt || '';
    const preview = node('img', undefined, 'content-preview'); preview.alt = '';
    const status = node('small', '', 'content-key');
    const show = () => {
      const shown = value ? mediaSrc(value) : (isBackground ? '' : defaultSrc);
      preview.hidden = !shown; if (shown) preview.src = shown;
      status.textContent = value ? 'Imagem alterada' : 'Imagem original da página';
    };
    const file = node('input'); file.type = 'file'; file.accept = 'image/jpeg,image/png,image/webp';
    file.addEventListener('change', async () => {
      const chosen = file.files[0]; if (!chosen) return;
      if (chosen.size > 3 * 1024 * 1024) { say(message, 'A imagem deve ter até 3 MB.'); file.value = ''; return; }
      const upload = new FormData(); upload.append('file', chosen);
      try { value = (await api('/api/admin/media', { method: 'POST', body: upload })).url; show(); say(message, 'Imagem carregada. Carregue em “Guardar alterações” para a publicar.', true); }
      catch (error) { say(message, error.message); }
      file.value = '';
    });
    const reset = node('button', 'Repor original'); reset.type = 'button';
    reset.addEventListener('click', () => { value = ''; show(); });
    const box = node('div', undefined, 'content-row');
    box.append(node('h4', label), preview, file, reset, status, node('small', key, 'content-key'));
    show();
    addRow(contentFields, { key, element: box, read() { return value ? { pt: value, en: null } : null; }, had: Boolean(saved[key]) });
  }

  async function loadContent() {
    const page = contentPage.value;
    $('[data-content-preview]').href = `${page}.html`;
    contentFields.replaceChildren(); contentRows = [];
    const [saved, html] = await Promise.all([
      fetch(`${cms().apiOrigin}/api/content`).then((r) => (r.ok ? r.json() : {})),
      fetch(`${page}.html`).then((r) => { if (!r.ok) throw new Error('Não foi possível ler a página.'); return r.text(); })
    ]);
    const doc = new DOMParser().parseFromString(html, 'text/html');
    const seen = new Set();
    doc.querySelectorAll('[data-cms], [data-cms-image], [data-cms-bg]').forEach((element, index) => {
      if (element.dataset.cms) {
        if (seen.has(element.dataset.cms)) return; seen.add(element.dataset.cms);
        textRow(element, saved, doc);
      } else if (element.dataset.cmsImage) {
        if (seen.has(element.dataset.cmsImage)) return; seen.add(element.dataset.cmsImage);
        imageRow(element.dataset.cmsImage, 'Imagem', element.getAttribute('src'), saved, false);
      } else if (element.dataset.cmsBg) {
        if (seen.has(element.dataset.cmsBg)) return; seen.add(element.dataset.cmsBg);
        imageRow(element.dataset.cmsBg, 'Imagem de fundo', '', saved, true);
      }
    });
    contentEmpty.hidden = contentRows.length > 0;
  }

  contentPage.addEventListener('change', () => loadContent().catch((error) => say(message, error.message)));
  $('[data-content-save]').addEventListener('click', async () => {
    const entries = {};
    for (const row of contentRows) {
      const value = row.read();
      if (value) entries[row.key] = { pt: value.pt ?? null, en: value.en ?? null };
      else if (row.had) entries[row.key] = null; // back to the original: remove the saved edit
    }
    if (!Object.keys(entries).length) { say(message, 'Não há alterações para guardar.'); return; }
    try {
      await api('/api/admin/content', { method: 'PUT', body: JSON.stringify({ entries }) });
      await loadContent();
      say(message, 'Conteúdo guardado. As visitas vêem as alterações em menos de um minuto.', true);
    } catch (error) { say(message, error.message); }
  });
  orderFilter.addEventListener('change', () => loadOrders().catch((error) => say(message, error.message)));
  $('[data-refresh-orders]').addEventListener('click', () => loadOrders().then(() => say(message, '')).catch((error) => say(message, error.message)));
})();
