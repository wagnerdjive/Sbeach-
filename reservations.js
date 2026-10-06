(() => {
  const dateInput = document.querySelector('[data-reservation-date]');
  if (!dateInput) return;
  const parts = new Intl.DateTimeFormat('en', { timeZone: 'Africa/Maputo', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
  const part = (name) => parts.find((item) => item.type === name)?.value || '';
  const today = `${part('year')}-${part('month')}-${part('day')}`;
  dateInput.min = today;
  dateInput.addEventListener('change', () => {
    if (dateInput.value && dateInput.value < today) dateInput.setCustomValidity(document.documentElement.lang === 'en' ? 'Choose today or a future date.' : 'Escolha hoje ou uma data futura.');
    else dateInput.setCustomValidity('');
  });
})();
