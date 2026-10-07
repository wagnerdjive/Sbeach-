(() => {
  const base = String(window.SOUTH_BEACH_API_BASE || '').trim().replace(/\/+$/, '');
  const isLocalPreview = ['localhost', '127.0.0.1'].includes(location.hostname) && location.port === '8000';
  const apiOrigin = base || (isLocalPreview || location.protocol === 'file:' ? 'http://localhost:8080' : location.origin);
  const token = new URLSearchParams(location.search).get('t');
  const note = document.querySelector('[data-pass-note]');
  const list = document.querySelector('[data-pass-list]');
  const printButton = document.querySelector('[data-pass-print]');

  const copy = {
    pt: { title: 'Os seus bilhetes', loading: 'A carregar…', invalid: 'Esta ligação não é válida. Confirme a mensagem que recebeu ou contacte o South Beach.', down: 'Não foi possível carregar os bilhetes. Tente novamente dentro de instantes.',
      unpaid: 'A encomenda {ref} ainda aguarda a confirmação do pagamento. Os bilhetes aparecem aqui assim que a equipa a confirmar.', cancelled: 'A encomenda {ref} foi cancelada ou expirou, por isso não tem bilhetes.',
      ready: 'Encomenda {ref} · mostre o código QR à entrada, no telemóvel ou impresso. Cada código admite uma só pessoa, uma só vez.', used: 'JÁ UTILIZADO', valid: 'VÁLIDO', voided: 'ANULADO', print: 'Imprimir ou guardar em PDF', ticket: 'Bilhete' },
    en: { title: 'Your tickets', loading: 'Loading…', invalid: 'This link is not valid. Check the message you received or contact South Beach.', down: 'We could not load your tickets. Please try again shortly.',
      unpaid: 'Order {ref} is still waiting for payment confirmation. Your tickets will appear here as soon as the team confirms it.', cancelled: 'Order {ref} was cancelled or expired, so it has no tickets.',
      ready: 'Order {ref} · show the QR code at the entrance, on your phone or printed. Each code admits one person, once.', used: 'ALREADY USED', valid: 'VALID', voided: 'VOIDED', print: 'Print or save as PDF', ticket: 'Ticket' }
  };
  const lang = () => { try { return localStorage.getItem('southBeachLanguage') === 'en' ? 'en' : 'pt'; } catch (_) { return navigator.language?.startsWith('en') ? 'en' : 'pt'; } };
  const t = (key, ref = '') => copy[lang()][key].replace('{ref}', ref);
  const when = (iso) => new Intl.DateTimeFormat(lang() === 'en' ? 'en-GB' : 'pt-PT', { timeZone: 'Africa/Maputo', dateStyle: 'full', timeStyle: 'short' }).format(new Date(iso));
  const el = (tag, props = {}, ...children) => { const node = Object.assign(document.createElement(tag), props); node.append(...children); return node; };

  document.querySelector('[data-pass-title]').textContent = t('title');
  printButton.textContent = t('print');
  printButton.addEventListener('click', () => window.print());

  async function load() {
    if (!token) { note.textContent = t('invalid'); return; }
    try {
      const response = await fetch(`${apiOrigin}/api/tickets/${encodeURIComponent(token)}`, { headers: { Accept: 'application/json' } });
      if (response.status === 404) { note.textContent = t('invalid'); return; }
      if (!response.ok) { note.textContent = t('down'); return; }
      const pass = await response.json();
      if (pass.orderStatus === 'PENDING') { note.textContent = t('unpaid', pass.orderReference); return; }
      if (pass.orderStatus !== 'PAID') { note.textContent = t('cancelled', pass.orderReference); return; }
      note.textContent = t('ready', pass.orderReference);
      pass.tickets.forEach((ticket, index) => {
        const stateText = ticket.status === 'USED' ? t('used') : ticket.status === 'VOID' ? t('voided') : t('valid');
        list.append(el('article', { className: `pass-card pass-${ticket.status.toLowerCase()}` },
          el('div', { className: 'pass-card-info' },
            el('p', { className: 'eyebrow', textContent: `${t('ticket')} ${index + 1}/${pass.tickets.length} · ${ticket.ticketType}` }),
            el('h2', { textContent: ticket.eventTitle }),
            el('p', { textContent: `${when(ticket.eventStartsAt)} · ${ticket.eventLocation}` }),
            el('span', { className: 'pass-state', textContent: stateText })),
          el('div', { className: 'pass-qr' },
            el('img', { src: `${apiOrigin}/api/tickets/qr/${encodeURIComponent(ticket.code)}`, alt: `QR ${ticket.code}`, width: 220, height: 220 }),
            el('code', { textContent: ticket.code }))));
      });
      printButton.hidden = pass.tickets.length === 0;
    } catch (_) { note.textContent = t('down'); }
  }
  load();
})();
