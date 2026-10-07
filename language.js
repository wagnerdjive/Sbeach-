(() => {
  const translations = window.SouthBeachTranslations || {};

  const titles = {
    'South Beach Maputo — Restaurante, Beach Bar & Sports Bar': 'South Beach Maputo — Restaurant, Beach Bar & Sports Bar',
    'Sobre o South Beach — Maputo': 'About South Beach — Maputo',
    'Restaurante — South Beach Maputo': 'Restaurant — South Beach Maputo',
    'Beach Bar — South Beach Maputo': 'Beach Bar — South Beach Maputo',
    'Sports Bar — South Beach Maputo': 'Sports Bar — South Beach Maputo',
    'Menus — South Beach Maputo': 'Menus — South Beach Maputo',
    'Eventos — South Beach Maputo': 'Events — South Beach Maputo',
    'Galeria — South Beach Maputo': 'Gallery — South Beach Maputo',
    'Contactos e localização — South Beach Maputo': 'Contact & location — South Beach Maputo',
    'Reservas — South Beach Maputo': 'Reservations — South Beach Maputo'
  };
  const descriptions = {
    'Descubra o restaurante, o beach bar, o sports bar e os eventos do South Beach, na Marginal de Maputo.': 'Discover the restaurant, beach bar, sports bar and events at South Beach on Maputo’s Marginal.',
    'Conheça o South Beach, um espaço de encontro à beira-mar em Maputo.': 'Discover South Beach, a seaside meeting place in Maputo.',
    'Descubra o restaurante South Beach em Maputo e consulte o menu actual.': 'Discover the South Beach restaurant in Maputo and view the current menu.',
    'Descubra o Beach Bar do South Beach em Maputo.': 'Discover the South Beach Beach Bar in Maputo.',
    'Descubra o Sports Bar do South Beach em Maputo.': 'Discover the South Beach Sports Bar in Maputo.',
    'Explore os menus actuais do restaurante, beach bar e sports bar do South Beach.': 'Explore the current restaurant, beach bar and sports bar menus at South Beach.',
    'Veja eventos e informações sobre eventos privados no South Beach, Maputo.': 'Explore events and private event information at South Beach, Maputo.',
    'Veja imagens do South Beach e de eventos realizados em Maputo.': 'View images of South Beach and past events in Maputo.',
    'Encontre a localização, os contactos e os horários do South Beach em Maputo.': 'Find South Beach’s location, contact details and opening hours in Maputo.',
    'Peça uma reserva no South Beach Maputo. A equipa confirmará a disponibilidade por contacto directo.': 'Request a reservation at South Beach Maputo. The team will confirm availability directly with you.'
  };

  const originalNodes = [];
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  let node;
  while ((node = walker.nextNode())) {
    const text = node.nodeValue.trim();
    // Text owned by the content editor (data-cms) is translated by cms.js, not here.
    if (translations[text] && !node.parentElement?.closest('[data-cms]')) originalNodes.push({ node, original: node.nodeValue, text });
  }
  const originalAttributes = [];
  document.querySelectorAll('[aria-label], [alt], [placeholder], [title]').forEach((element) => {
    ['aria-label', 'alt', 'placeholder', 'title'].forEach((name) => {
      const value = element.getAttribute(name);
      if (value && translations[value]) originalAttributes.push({ element, name, value });
    });
  });
  const originalTitle = document.title;
  const description = document.querySelector('meta[name="description"]');
  const originalDescription = description?.content || '';

  function applyLanguage(language) {
    originalNodes.forEach(({ node, original, text }) => {
      const leading = original.match(/^\s*/)?.[0] || '';
      const trailing = original.match(/\s*$/)?.[0] || '';
      node.nodeValue = `${leading}${language === 'en' ? translations[text] : text}${trailing}`;
    });
    originalAttributes.forEach(({ element, name, value }) => {
      element.setAttribute(name, language === 'en' ? translations[value] : value);
    });
    document.documentElement.lang = language === 'en' ? 'en' : 'pt-MZ';
    document.title = language === 'en' ? (titles[originalTitle] || originalTitle) : originalTitle;
    if (description) description.content = language === 'en' ? (descriptions[originalDescription] || originalDescription) : originalDescription;
    document.querySelectorAll('[data-language-toggle]').forEach((button) => {
      button.textContent = language === 'en' ? 'PT' : 'EN';
      button.setAttribute('aria-label', language === 'en' ? 'Switch to Portuguese' : 'Switch to English');
      button.setAttribute('aria-pressed', String(language === 'en'));
    });
    const menuButton = document.querySelector('.menu-toggle');
    if (menuButton) {
      const isOpen = menuButton.getAttribute('aria-expanded') === 'true';
      menuButton.setAttribute('aria-label', language === 'en'
        ? (isOpen ? 'Close menu' : 'Open menu')
        : (isOpen ? 'Fechar menu' : 'Abrir menu'));
    }
    try { localStorage.setItem('southBeachLanguage', language); } catch (_) {}
  }

  let language = 'pt';
  try { language = localStorage.getItem('southBeachLanguage') === 'en' ? 'en' : 'pt'; } catch (_) {}
  applyLanguage(language);
  document.querySelectorAll('[data-language-toggle]').forEach((button) => {
    button.addEventListener('click', () => applyLanguage(language = language === 'pt' ? 'en' : 'pt'));
  });
})();
