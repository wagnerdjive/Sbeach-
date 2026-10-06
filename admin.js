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
    if (!response.ok) throw new Error(data.message || 'Não foi possível concluir o pedido.');
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
})();
