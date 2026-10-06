(() => {
  const form = document.querySelector('[data-reservation-form]');
  const dateInput = document.querySelector('[data-reservation-date]');
  const status = document.querySelector('[data-reservation-status]');
  if (!form || !dateInput || !status) return;

  const copy = {
    pt: {
      oldDate: 'Escolha hoje ou uma data futura.',
      sending: 'A enviar o seu pedido…',
      sent: 'Pedido registado com a referência {reference}. O South Beach ainda precisa de confirmar a disponibilidade consigo.',
      badRequest: 'Verifique os dados do formulário e tente novamente.',
      unavailable: 'Não foi possível registar o pedido agora. Tente novamente ou contacte a equipa por telefone.',
      network: 'Não foi possível ligar ao serviço de reservas. Tente novamente ou contacte a equipa por telefone.'
    },
    en: {
      oldDate: 'Choose today or a future date.',
      sending: 'Sending your request…',
      sent: 'Request saved with reference {reference}. South Beach still needs to confirm availability with you.',
      badRequest: 'Check the form details and try again.',
      unavailable: 'Your request could not be saved right now. Try again or call the team.',
      network: 'The reservation service could not be reached. Try again or call the team.'
    }
  };
  const language = () => document.documentElement.lang === 'en' ? 'en' : 'pt';
  const setStatus = (key, values = {}) => {
    status.textContent = Object.entries(values).reduce((text, [name, value]) => text.replace(`{${name}}`, value), copy[language()][key]);
  };
  const dateParts = new Intl.DateTimeFormat('en', { timeZone: 'Africa/Maputo', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
  const getPart = (name) => dateParts.find((item) => item.type === name)?.value || '';
  const today = `${getPart('year')}-${getPart('month')}-${getPart('day')}`;
  dateInput.min = today;
  dateInput.addEventListener('change', () => dateInput.setCustomValidity(dateInput.value && dateInput.value < today ? copy[language()].oldDate : ''));

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!form.reportValidity()) return;
    const data = Object.fromEntries(new FormData(form).entries());
    data.partySize = Number(data.partySize);
    const base = String(window.SOUTH_BEACH_API_BASE || '').trim().replace(/\/+$/, '');
    const isLocalPreview = ['localhost', '127.0.0.1'].includes(location.hostname) && location.port === '8000';
    const apiOrigin = base || (isLocalPreview || location.protocol === 'file:' ? 'http://localhost:8080' : location.origin);
    const submit = form.querySelector('[type="submit"]');
    submit.disabled = true;
    setStatus('sending');
    try {
      const response = await fetch(`${apiOrigin}/api/reservations`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify(data)
      });
      if (!response.ok) {
        setStatus(response.status === 400 ? 'badRequest' : 'unavailable');
        return;
      }
      const result = await response.json();
      setStatus('sent', { reference: result.reference });
      form.reset();
      dateInput.min = today;
    } catch (_) {
      setStatus('network');
    } finally {
      submit.disabled = false;
    }
  });
})();
