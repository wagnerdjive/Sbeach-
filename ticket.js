(() => {
  const base = String(window.SOUTH_BEACH_API_BASE || '').trim().replace(/\/+$/, '');
  const isLocalPreview = ['localhost', '127.0.0.1'].includes(location.hostname) && location.port === '8000';
  const apiOrigin = base || (isLocalPreview || location.protocol === 'file:' ? 'http://localhost:8080' : location.origin);
  const token = new URLSearchParams(location.search).get('t');
  const note = document.querySelector('[data-pass-note]');
  const list = document.querySelector('[data-pass-list]');
  const printButton = document.querySelector('[data-pass-print]');
  const pdfButton = document.querySelector('[data-pass-pdf]');

  const copy = {
    pt: { title: 'Os seus bilhetes', loading: 'A carregar…', invalid: 'Esta ligação não é válida. Confirme a mensagem que recebeu ou contacte o South Beach.', down: 'Não foi possível carregar os bilhetes. Tente novamente dentro de instantes.',
      refunded: 'A encomenda {ref} foi reembolsada: os bilhetes abaixo estão anulados e já não dão entrada.', unpaid: 'A encomenda {ref} ainda aguarda a confirmação do pagamento. Os bilhetes aparecem aqui assim que a equipa a confirmar.', cancelled: 'A encomenda {ref} foi cancelada ou expirou, por isso não tem bilhetes.',
      ready: 'Encomenda {ref} · mostre o código QR à entrada, no telemóvel ou impresso. Cada código admite uma só pessoa, uma só vez.', used: 'JÁ UTILIZADO', valid: 'VÁLIDO', voided: 'ANULADO', print: 'Imprimir', pdf: 'Descarregar PDF', ticket: 'Bilhete',
      kicker: 'SOUTH BEACH · MAPUTO', where: 'Local', ticketOf: '{n} de {total}', order: 'Encomenda', map: 'Ver no mapa', nextDay: 'dia seguinte',
      howto: 'Mostre este código à entrada. Cada código vale para uma pessoa e uma só entrada.' },
    en: { title: 'Your tickets', loading: 'Loading…', invalid: 'This link is not valid. Check the message you received or contact South Beach.', down: 'We could not load your tickets. Please try again shortly.',
      refunded: 'Order {ref} was refunded: the tickets below are void and no longer admit anyone.', unpaid: 'Order {ref} is still waiting for payment confirmation. Your tickets will appear here as soon as the team confirms it.', cancelled: 'Order {ref} was cancelled or expired, so it has no tickets.',
      ready: 'Order {ref} · show the QR code at the entrance, on your phone or printed. Each code admits one person, once.', used: 'ALREADY USED', valid: 'VALID', voided: 'VOIDED', print: 'Print', pdf: 'Download PDF', ticket: 'Ticket',
      kicker: 'SOUTH BEACH · MAPUTO', where: 'Venue', ticketOf: '{n} of {total}', order: 'Order', map: 'View on map', nextDay: 'next day',
      howto: 'Show this code at the entrance. Each code is valid for one person and one entry.' }
  };
  const lang = () => { try { return localStorage.getItem('southBeachLanguage') === 'en' ? 'en' : 'pt'; } catch (_) { return navigator.language?.startsWith('en') ? 'en' : 'pt'; } };
  const t = (key, ref = '') => copy[lang()][key].replace('{ref}', ref);
  const locale = () => (lang() === 'en' ? 'en-GB' : 'pt-PT');
  const fmt = (options) => new Intl.DateTimeFormat(locale(), { timeZone: 'Africa/Maputo', ...options });
  const dayKey = (date) => fmt({ year: 'numeric', month: 'numeric', day: 'numeric' }).format(date);
  // "sábado, 24 de outubro de 2026" and "16:00 – 23:00" (with "dia seguinte" when it ends after midnight)
  const dateLine = (iso) => { const text = fmt({ weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }).format(new Date(iso)); return text.charAt(0).toUpperCase() + text.slice(1); };
  const timeLine = (startIso, endIso) => {
    const start = new Date(startIso), time = (d) => fmt({ hour: '2-digit', minute: '2-digit' }).format(d);
    if (!endIso) return time(start);
    const end = new Date(endIso);
    return `${time(start)} – ${time(end)}${dayKey(start) === dayKey(end) ? '' : ` (${t('nextDay')})`}`;
  };
  const el = (tag, props = {}, ...children) => { const node = Object.assign(document.createElement(tag), props); node.append(...children); return node; };

  document.querySelector('[data-pass-title]').textContent = t('title');
  printButton.textContent = t('print');
  pdfButton.textContent = t('pdf');
  printButton.addEventListener('click', () => window.print());

  async function load() {
    if (!token) { note.textContent = t('invalid'); return; }
    try {
      const response = await fetch(`${apiOrigin}/api/tickets/${encodeURIComponent(token)}`, { headers: { Accept: 'application/json' } });
      if (response.status === 404) { note.textContent = t('invalid'); return; }
      if (!response.ok) { note.textContent = t('down'); return; }
      const pass = await response.json();
      if (pass.orderStatus === 'PENDING') { note.textContent = t('unpaid', pass.orderReference); return; }
      if (pass.orderStatus === 'PAID') note.textContent = t('ready', pass.orderReference);
      else if (pass.orderStatus === 'REFUNDED') note.textContent = t('refunded', pass.orderReference); // the voided tickets are still listed below
      else { note.textContent = t('cancelled', pass.orderReference); return; }
      pass.tickets.forEach((ticket, index) => {
        const stateText = ticket.status === 'USED' ? t('used') : ticket.status === 'VOID' ? t('voided') : t('valid');
        const poster = ticket.eventPosterUrl ? el('img', { className: 'pass-poster', src: apiOrigin + ticket.eventPosterUrl, alt: '' }) : null;
        const top = el('div', { className: 'pass-top' },
          ...(poster ? [poster] : []),
          el('div', { className: 'pass-heading' },
            el('p', { className: 'pass-kicker', textContent: t('kicker') }),
            el('h2', { textContent: ticket.eventTitle }),
            el('p', { className: 'pass-when', textContent: dateLine(ticket.eventStartsAt) }),
            el('p', { className: 'pass-when pass-time', textContent: timeLine(ticket.eventStartsAt, ticket.eventEndsAt) })),
          el('span', { className: 'pass-type', textContent: ticket.ticketType }));
        const qr = el('div', { className: 'pass-qr' },
          el('img', { src: `${apiOrigin}/api/tickets/qr/${encodeURIComponent(ticket.code)}`, alt: `QR ${ticket.code}`, width: 260, height: 260 }),
          el('code', { textContent: ticket.code }));
        const map = el('a', { className: 'pass-map', href: `https://maps.google.com/?q=${encodeURIComponent(ticket.eventLocation)}`, target: '_blank', rel: 'noreferrer', textContent: `${t('map')} ↗\ufe0e` });
        const details = el('div', { className: 'pass-meta' },
          el('span', { className: 'pass-state', textContent: stateText }),
          el('dl', {},
            el('dt', { textContent: t('where') }), el('dd', {}, ticket.eventLocation, el('br'), map),
            el('dt', { textContent: t('ticket') }), el('dd', { textContent: t('ticketOf').replace('{n}', index + 1).replace('{total}', pass.tickets.length) }),
            el('dt', { textContent: t('order') }), el('dd', { textContent: pass.orderReference })),
          el('p', { className: 'pass-howto', textContent: t('howto') }));
        list.append(el('article', { className: `pass-card pass-${ticket.status.toLowerCase()}` }, top,
          el('div', { className: 'pass-tear', ariaHidden: 'true' }), el('div', { className: 'pass-bottom' }, qr, details)));
      });
      printButton.hidden = pass.tickets.length === 0;
      pdfButton.hidden = pass.tickets.length === 0;
      pdfButton.href = `${apiOrigin}/api/tickets/${encodeURIComponent(token)}/pdf`;
      pdfButton.setAttribute('download', `Bilhetes-${pass.orderReference}.pdf`);
    } catch (_) { note.textContent = t('down'); }
  }
  load();
})();
