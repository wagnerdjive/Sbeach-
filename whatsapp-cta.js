// "Receive on WhatsApp": a button that opens a chat with the business number with the order or reservation reference already written.
// The customer only taps send; that opens WhatsApp's 24-hour window and the site answers with the tickets or the booking status.
// Hidden unless the site reports a WhatsApp number (the feature is switched on in the server settings).
(() => {
  const base = String(window.SOUTH_BEACH_API_BASE || '').trim().replace(/\/+$/, '');
  const isLocalPreview = ['localhost', '127.0.0.1'].includes(location.hostname) && location.port === '8000';
  const apiOrigin = base || (isLocalPreview || location.protocol === 'file:' ? 'http://localhost:8080' : location.origin);
  const english = () => { try { return localStorage.getItem('southBeachLanguage') === 'en'; } catch (_) { return false; } };
  const copy = {
    TK: { pt: ['Receber os bilhetes por WhatsApp', 'Quero receber os bilhetes da encomenda {ref}'], en: ['Receive the tickets on WhatsApp', 'I would like to receive the tickets of order {ref}'] },
    SB: { pt: ['Receber a confirmação por WhatsApp', 'Quero receber a confirmação da reserva {ref}'], en: ['Receive the confirmation on WhatsApp', 'I would like to receive the confirmation of reservation {ref}'] }
  };
  let number = null;
  const ready = fetch(`${apiOrigin}/api/whatsapp/info`, { headers: { Accept: 'application/json' } })
    .then((response) => (response.ok ? response.json() : null))
    .then((data) => { number = data && data.number ? String(data.number).replace(/\D/g, '') : null; })
    .catch(() => { number = null; });

  /** Returns the button for an order (TK-…) or reservation (SB-…) reference, or null when WhatsApp is not available. */
  function link(reference) {
    const kind = String(reference).slice(0, 2);
    if (!number || !copy[kind]) return null;
    const [label, text] = copy[kind][english() ? 'en' : 'pt'];
    const anchor = document.createElement('a');
    anchor.className = 'whatsapp-cta';
    anchor.href = `https://wa.me/${number}?text=${encodeURIComponent(text.replace('{ref}', reference))}`;
    anchor.target = '_blank';
    anchor.rel = 'noopener noreferrer';
    anchor.textContent = label;
    return anchor;
  }

  window.SouthBeachWhatsApp = { ready, link };
})();
