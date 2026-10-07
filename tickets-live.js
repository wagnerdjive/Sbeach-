(() => {
  const root = document.querySelector('[data-live-tickets]');
  const preview = document.querySelector('.ticket-preview');
  if (!root || !preview) return;

  const base = String(window.SOUTH_BEACH_API_BASE || '').trim().replace(/\/+$/, '');
  const isLocalPreview = ['localhost', '127.0.0.1'].includes(location.hostname) && location.port === '8000';
  const apiOrigin = base || (isLocalPreview || location.protocol === 'file:' ? 'http://localhost:8080' : location.origin);

  const copy = {
    pt: {
      label: 'À VENDA', total: 'Total', tickets: 'Bilhetes seleccionados', none: 'Escolha a quantidade de bilhetes.',
      name: 'Nome completo', phone: 'Telefone de contacto', email: 'Email', optional: '(opcional)',
      submit: 'Reservar bilhetes', sending: 'A reservar…', soldOut: 'Esgotado', notOnSale: 'Fora de venda', left: 'restantes',
      payNote: 'O pagamento online ainda não está activo. Reservar guarda os bilhetes durante {minutes} minutos; a equipa contacta-o para concluir.',
      ok: 'Bilhetes reservados com a referência {reference} até às {time}. O pagamento ainda não está activo: a equipa vai contactá-lo para concluir a compra.',
      conflict: 'Já não há bilhetes suficientes ou a venda terminou. Os valores foram actualizados.',
      bad: 'Verifique os dados e a quantidade escolhida e tente novamente.',
      down: 'Não foi possível reservar agora. Tente novamente ou contacte a equipa.',
      less: 'Diminuir quantidade', more: 'Aumentar quantidade',
      back: '← Todos os eventos', missing: 'Este evento não está disponível.', order: 'O seu pedido', categories: 'Escolha os bilhetes'
    },
    en: {
      label: 'ON SALE', total: 'Total', tickets: 'Selected tickets', none: 'Choose how many tickets you want.',
      name: 'Full name', phone: 'Contact phone', email: 'Email', optional: '(optional)',
      submit: 'Reserve tickets', sending: 'Reserving…', soldOut: 'Sold out', notOnSale: 'Not on sale', left: 'left',
      payNote: 'Online payment is not active yet. Reserving holds your tickets for {minutes} minutes; the team will contact you to complete the purchase.',
      ok: 'Tickets reserved under reference {reference} until {time}. Payment is not active yet: the team will contact you to complete the purchase.',
      conflict: 'There are not enough tickets left or the sale has ended. Figures have been refreshed.',
      bad: 'Check your details and the quantities and try again.',
      down: 'We could not reserve right now. Try again or contact the team.',
      less: 'Decrease quantity', more: 'Increase quantity',
      back: '← All events', missing: 'This event is not available.', order: 'Your order', categories: 'Choose your tickets'
    }
  };
  const holdMinutes = 15;
  const lang = () => { try { return localStorage.getItem('southBeachLanguage') === 'en' ? 'en' : 'pt'; } catch (_) { return 'pt'; } };
  const t = (key, values = {}) => Object.entries(values).reduce((s, [k, v]) => s.replace(`{${k}}`, v), copy[lang()][key]);
  const locale = () => (lang() === 'en' ? 'en-GB' : 'pt-PT');
  const money = (minor) => `${(minor / 100).toLocaleString(locale(), { maximumFractionDigits: 2 })} MZN`;
  const when = (iso) => new Intl.DateTimeFormat(locale(), { timeZone: 'Africa/Maputo', dateStyle: 'full', timeStyle: 'short' }).format(new Date(iso));
  const clock = (iso) => new Intl.DateTimeFormat(locale(), { timeZone: 'Africa/Maputo', timeStyle: 'short' }).format(new Date(iso));

  const el = (tag, props = {}, ...children) => {
    const node = Object.assign(document.createElement(tag), props);
    node.append(...children);
    return node;
  };
  const refreshers = [];
  let lastOrder = null; // the reference of the order just placed, to offer "receive on WhatsApp"

  function buildEvent(event) {
    const quantities = new Map(event.ticketTypes.map((type) => [type.id, 0]));
    const status = el('p', { className: 'live-message', role: 'status' });
    let message = null;
    const count = el('strong');
    const total = el('strong');
    const hint = el('p', { className: 'checkout-hint' });
    const submit = el('button', { className: 'button button-dark checkout-button', type: 'submit' });
    const inputs = {};
    const field = (name, type, autocomplete, required, labelKey) => {
      const input = el('input', { name, type, autocomplete, required, maxLength: name === 'email' ? 120 : 100 });
      inputs[name] = input;
      const label = el('label', { className: 'live-field' });
      const text = el('span');
      label.append(text, input);
      return { label, text, labelKey, required };
    };
    const fields = [field('fullName', 'text', 'name', true, 'name'), field('phone', 'tel', 'tel', true, 'phone'), field('email', 'email', 'email', false, 'email')];

    const options = el('div', { className: 'ticket-options' });
    const optionsTitle = el('p', { className: 'ticket-options-title' });
    options.append(optionsTitle);
    refreshers.push(() => { optionsTitle.textContent = t('categories'); });
    const typeViews = event.ticketTypes.map((type) => {
      const canBuy = type.onSale && type.available > 0;
      const output = el('output', { textContent: '0' });
      const minus = el('button', { type: 'button', textContent: '−', disabled: !canBuy });
      const plus = el('button', { type: 'button', textContent: '+', disabled: !canBuy });
      const price = el('strong', { textContent: money(type.priceMinor) });
      const note = el('small');
      const step = (delta) => {
        const max = Math.min(type.maxPerOrder, type.available);
        quantities.set(type.id, Math.max(0, Math.min(max, quantities.get(type.id) + delta)));
        output.textContent = String(quantities.get(type.id));
        article.classList.toggle('is-selected', quantities.get(type.id) > 0);
        update();
      };
      minus.addEventListener('click', () => step(-1));
      plus.addEventListener('click', () => step(1));
      const article = el('article', { className: 'ticket-option' },
        el('div', {}, el('span', { className: 'ticket-type', textContent: type.name }), el('h3', { textContent: type.description || type.name })),
        el('div', { className: 'ticket-option-action' }, price, note, el('div', { className: 'quantity-control' }, minus, output, plus)));
      if (!canBuy) article.dataset.unavailable = '';
      options.append(article);
      return { type, minus, plus, note };
    });

    function update() {
      const picked = event.ticketTypes.reduce((sum, type) => sum + quantities.get(type.id), 0);
      const cost = event.ticketTypes.reduce((sum, type) => sum + quantities.get(type.id) * type.priceMinor, 0);
      count.textContent = String(picked);
      total.textContent = picked ? money(cost) : '—';
      submit.disabled = picked === 0 || submit.dataset.busy === '1';
      hint.textContent = picked ? '' : t('none');
    }
    function applyLanguage() {
      typeViews.forEach(({ type, minus, plus, note }) => {
        note.textContent = !type.onSale ? t('notOnSale') : type.available === 0 ? t('soldOut') : `${type.available} ${t('left')}`;
        minus.setAttribute('aria-label', t('less'));
        plus.setAttribute('aria-label', t('more'));
      });
      fields.forEach(({ text, labelKey, required }) => { text.textContent = t(labelKey) + (required ? '' : ` ${t('optional')}`); });
      if (submit.dataset.busy !== '1') submit.textContent = t('submit');
      noteLine.textContent = t('payNote', { minutes: holdMinutes });
      if (message) status.textContent = t(message.key, message.values);
      update();
    }
    const setMessage = (key, values, ok = false) => { message = key ? { key, values } : null; status.textContent = key ? t(key, values) : ''; status.classList.toggle('ok', ok); };

    const noteLine = el('p', { className: 'checkout-hint' });
    const whatsapp = el('div', { className: 'live-whatsapp' });
    const paintWhatsApp = () => { const button = lastOrder && window.SouthBeachWhatsApp?.link(lastOrder); whatsapp.replaceChildren(...(button ? [button] : [])); };
    const orderTitle = el('h3', { className: 'checkout-title' });
    const form = el('form', { className: 'checkout-card' }, orderTitle,
      el('div', { className: 'checkout-line' }, el('span', { className: 'live-count-label' }), count),
      el('div', { className: 'checkout-line checkout-total' }, el('span', { className: 'live-total-label' }), total),
      hint, ...fields.map((f) => f.label), submit, noteLine, status);
    form.append(whatsapp);
    refreshers.push(paintWhatsApp);
    const countLabel = form.querySelector('.live-count-label');
    const totalLabel = form.querySelector('.live-total-label');
    refreshers.push(() => { countLabel.textContent = t('tickets'); totalLabel.textContent = t('total'); orderTitle.textContent = t('order'); });

    form.addEventListener('submit', async (submitEvent) => {
      submitEvent.preventDefault();
      if (!form.reportValidity()) return;
      const items = event.ticketTypes.filter((type) => quantities.get(type.id) > 0).map((type) => ({ ticketTypeId: type.id, quantity: quantities.get(type.id) }));
      submit.dataset.busy = '1'; submit.disabled = true; submit.textContent = t('sending');
      setMessage(null);
      try {
        const response = await fetch(`${apiOrigin}/api/orders`, {
          method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
          body: JSON.stringify({ eventSlug: event.slug, fullName: inputs.fullName.value, phone: inputs.phone.value, email: inputs.email.value.trim() || null, items })
        });
        if (response.status === 201) {
          const order = await response.json();
          lastOrder = order.reference;
          await window.SouthBeachWhatsApp?.ready;
          paintWhatsApp();
          quantities.forEach((_, id) => quantities.set(id, 0));
          form.reset();
          setMessage('ok', { reference: order.reference, time: clock(order.expiresAt) }, true);
          await load(true);
          return;
        }
        setMessage(response.status === 409 ? 'conflict' : response.status === 400 ? 'bad' : 'down');
        if (response.status === 409) await load(true);
      } catch (_) {
        setMessage('down');
      } finally {
        delete submit.dataset.busy;
        submit.textContent = t('submit');
        update();
      }
    });

    const info = el('div', { className: 'live-event-info' },
      el('p', { className: 'eyebrow' }), el('h2', { textContent: event.title }),
      el('p', { className: 'live-event-meta', textContent: `${when(event.startsAt)}  ·  ${event.location}` }));
    if (event.description) info.append(el('p', { className: 'live-event-about', textContent: event.description }));
    const head = el('div', { className: 'live-event-head' });
    if (event.posterUrl) {
      head.classList.add('has-poster');
      head.append(el('img', { className: 'live-poster', src: apiOrigin + event.posterUrl, alt: `Cartaz — ${event.title}`, loading: 'lazy' }));
    }
    head.append(info);
    const eyebrow = info.querySelector('.eyebrow');
    refreshers.push(() => { eyebrow.textContent = t('label'); });
    refreshers.push(applyLanguage);
    const wrapper = el('article', { className: 'live-event' }, head, el('div', { className: 'ticket-layout' }, options, form));
    return wrapper;
  }

  const wanted = new URLSearchParams(location.search).get('evento');
  const backLink = el('a', { className: 'live-back', href: 'events.html#bilhetes' });

  function show(nodes) {
    refreshers.push(() => { backLink.textContent = t('back'); });
    root.replaceChildren(backLink, ...nodes);
    refreshers.forEach((run) => run());
    root.hidden = false;
    preview.hidden = true;
  }

  async function load(refresh = false) {
    try {
      if (!wanted) {
        // The sale screen belongs to one event: with no event chosen, send the visitor to the list.
        const response = await fetch(`${apiOrigin}/api/events`, { headers: { Accept: 'application/json' } });
        if (response.ok && (await response.json()).some((event) => event.ticketTypes.length > 0)) location.replace('events.html#bilhetes');
        return;
      }
      const response = await fetch(`${apiOrigin}/api/events/${encodeURIComponent(wanted)}`, { headers: { Accept: 'application/json' } });
      if (response.status === 404) {
        refreshers.length = 0;
      show([el('p', { className: 'live-missing', textContent: t('missing') })]);
        return;
      }
      if (!response.ok) { document.documentElement.classList.remove('sale-view'); return; }
      const event = await response.json();
      // Re-render after a purchase to show fresh stock, but keep the confirmation message visible.
      const keep = refresh ? root.querySelector('.live-message')?.textContent : '';
      const keepOk = refresh && root.querySelector('.live-message.ok') !== null;
      refreshers.length = 0;
      show([buildEvent(event)]);
      if (keep) {
        const status = root.querySelector('.live-message');
        status.textContent = keep; status.classList.toggle('ok', keepOk);
      }
    } catch (_) { document.documentElement.classList.remove('sale-view'); /* keep the demo preview when the API is unreachable */ }
  }

  document.querySelector('[data-language-toggle]')?.addEventListener('click', () => setTimeout(() => refreshers.forEach((run) => run()), 0));
  load();
})();
