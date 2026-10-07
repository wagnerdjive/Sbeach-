// Door staff sign in here with the limited access the admin created. It can only read and admit tickets.
(() => {
  const base = String(window.SOUTH_BEACH_API_BASE || '').trim().replace(/\/+$/, '');
  const isLocalPreview = ['localhost', '127.0.0.1'].includes(location.hostname) && location.port === '8000';
  const apiOrigin = base || (isLocalPreview || location.protocol === 'file:' ? 'http://localhost:8080' : location.origin);
  const $ = (selector) => document.querySelector(selector);
  const loginForm = $('[data-login]'), panel = $('[data-panel]'), logout = $('[data-logout]'), loginMessage = $('[data-login-message]');
  // The access stays only for this tab (it survives a reload during the event, and disappears when the tab is closed).
  let authHeader = null;
  try { authHeader = sessionStorage.getItem('gateAuth'); } catch (_) { /* no storage */ }

  async function api(path, options = {}) {
    const response = await fetch(apiOrigin + path, {
      ...options, headers: { Accept: 'application/json', Authorization: authHeader, ...(options.body ? { 'Content-Type': 'application/json' } : {}) }
    });
    if (response.status === 401) { signOut('A sessão terminou ou o acesso deixou de ser válido. Peça um novo código.'); throw new Error('Sessão inválida.'); }
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || 'Não foi possível concluir o pedido.');
    return data;
  }

  const gate = window.SouthBeachGate.mount($('[data-gate-root]'), { api });

  function signOut(text = '') {
    authHeader = null; try { sessionStorage.removeItem('gateAuth'); } catch (_) { /* ignore */ }
    gate.stop(); panel.hidden = true; logout.hidden = true; loginForm.hidden = false;
    loginMessage.textContent = text;
  }

  async function enter() {
    await gate.load();
    loginForm.hidden = true; panel.hidden = false; logout.hidden = false;
  }

  loginForm.addEventListener('submit', async (submitEvent) => {
    submitEvent.preventDefault();
    const data = new FormData(loginForm);
    // The code is shown in capitals without confusing characters: tolerate spaces, dashes and lower case when typing it.
    const username = String(data.get('username')).trim().toLowerCase();
    const code = String(data.get('password')).replace(/[\s-]/g, '').toUpperCase();
    authHeader = 'Basic ' + btoa(`${username}:${code}`);
    loginMessage.textContent = '';
    try {
      await enter();
      try { sessionStorage.setItem('gateAuth', authHeader); } catch (_) { /* ignore */ }
      loginForm.reset();
    } catch (error) {
      authHeader = null;
      loginMessage.textContent = error.message === 'Sessão inválida.' ? 'Utilizador ou código incorrectos, ou o acesso terminou.' : error.message;
    }
  });
  logout.addEventListener('click', () => signOut());

  if (authHeader) enter().catch(() => signOut());
})();
