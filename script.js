const menuButton = document.querySelector('.menu-toggle');
const nav = document.querySelector('#primary-nav');

menuButton?.addEventListener('click', () => {
  const isOpen = menuButton.getAttribute('aria-expanded') === 'true';
  const isEnglish = document.documentElement.lang === 'en';
  menuButton.setAttribute('aria-expanded', String(!isOpen));
  menuButton.setAttribute('aria-label', isOpen ? (isEnglish ? 'Open menu' : 'Abrir menu') : (isEnglish ? 'Close menu' : 'Fechar menu'));
  nav?.classList.toggle('is-open', !isOpen);
});

nav?.querySelectorAll('a').forEach((link) => {
  link.addEventListener('click', () => {
    nav.classList.remove('is-open');
    menuButton?.setAttribute('aria-expanded', 'false');
    menuButton?.setAttribute('aria-label', document.documentElement.lang === 'en' ? 'Open menu' : 'Abrir menu');
    nav.querySelectorAll('details[open]').forEach((group) => group.removeAttribute('open'));
  });
});

const year = document.querySelector('#year');
if (year) year.textContent = String(new Date().getFullYear());

const menuTabs = [...document.querySelectorAll('[data-menu-tab]')];
const menuPanels = [...document.querySelectorAll('[data-menu-panel]')];
function activateMenu(key, updateHash = false) {
  menuTabs.forEach((tab) => {
    const selected = tab.dataset.menuTab === key;
    tab.setAttribute('aria-selected', String(selected));
    tab.tabIndex = selected ? 0 : -1;
  });
  menuPanels.forEach((panel) => {
    panel.hidden = panel.dataset.menuPanel !== key;
  });
  if (updateHash && history.replaceState) history.replaceState(null, '', `#${key}`);
}
menuTabs.forEach((tab, index) => {
  tab.addEventListener('click', () => activateMenu(tab.dataset.menuTab, true));
  tab.addEventListener('keydown', (event) => {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    const nextIndex = event.key === 'Home' ? 0 : event.key === 'End' ? menuTabs.length - 1 : (index + (event.key === 'ArrowRight' ? 1 : menuTabs.length - 1)) % menuTabs.length;
    menuTabs[nextIndex].focus();
    activateMenu(menuTabs[nextIndex].dataset.menuTab, true);
  });
});
if (menuTabs.length) activateMenu(location.hash.slice(1) || menuTabs[0].dataset.menuTab);

const menuCategoryButtons = [...document.querySelectorAll('[data-menu-category]')];
function activateMenuCategory(category) {
  menuPanels.forEach((panel) => {
    panel.querySelectorAll('.menu-tags li[data-category]').forEach((tag) => {
      tag.hidden = tag.dataset.category !== category;
    });
  });
  menuCategoryButtons.forEach((button) => {
    button.setAttribute('aria-pressed', String(button.dataset.menuCategory === category));
  });
}
menuCategoryButtons.forEach((button) => {
  button.addEventListener('click', () => activateMenuCategory(button.dataset.menuCategory));
});
if (menuCategoryButtons.length) activateMenuCategory('food');

const galleryFilters = [...document.querySelectorAll('[data-gallery-filter]')];
const galleryCards = [...document.querySelectorAll('[data-gallery-item]')];
function filterGallery(filter) {
  galleryCards.forEach((card) => {
    card.hidden = filter !== 'all' && card.dataset.galleryItem !== filter;
  });
  galleryFilters.forEach((button) => button.setAttribute('aria-pressed', String(button.dataset.galleryFilter === filter)));
}
galleryFilters.forEach((button) => button.addEventListener('click', () => filterGallery(button.dataset.galleryFilter)));

const lightbox = document.querySelector('#lightbox');
if (lightbox) {
  const lightboxImage = lightbox.querySelector('[data-lightbox-image]');
  const lightboxTitle = lightbox.querySelector('[data-lightbox-title]');
  let currentImages = [];
  let currentIndex = 0;
  function showLightboxImage(index) {
    currentIndex = (index + currentImages.length) % currentImages.length;
    const source = currentImages[currentIndex];
    lightboxImage.src = source.dataset.src;
    lightboxImage.alt = source.dataset.title;
    lightboxTitle.textContent = source.dataset.title;
  }
  document.querySelectorAll('[data-lightbox-open]').forEach((button) => {
    button.addEventListener('click', () => {
      currentImages = galleryCards.filter((card) => !card.hidden).map((card) => card.querySelector('[data-lightbox-open]'));
      currentIndex = currentImages.indexOf(button);
      showLightboxImage(currentIndex);
      lightbox.showModal();
    });
  });
  lightbox.querySelector('[data-lightbox-close]')?.addEventListener('click', () => lightbox.close());
  lightbox.querySelector('[data-lightbox-prev]')?.addEventListener('click', () => showLightboxImage(currentIndex - 1));
  lightbox.querySelector('[data-lightbox-next]')?.addEventListener('click', () => showLightboxImage(currentIndex + 1));
  lightbox.addEventListener('click', (event) => {
    if (event.target === lightbox) lightbox.close();
  });
  document.addEventListener('keydown', (event) => {
    if (!lightbox.open) return;
    if (event.key === 'ArrowLeft') showLightboxImage(currentIndex - 1);
    if (event.key === 'ArrowRight') showLightboxImage(currentIndex + 1);
  });
}

document.querySelectorAll('[data-mail-form]').forEach((form) => {
  form.addEventListener('submit', (event) => {
    event.preventDefault();
    if (!form.reportValidity()) return;
    const values = new FormData(form);
    const subject = form.dataset.mailSubject || 'Pedido de informação — South Beach';
    const body = [...values.entries()].map(([key, value]) => `${key}: ${value}`).join('\n');
    const status = form.querySelector('[data-form-status]');
    if (status) status.textContent = document.documentElement.lang === 'en'
      ? 'Opening your email app with the request filled in. Your request is sent only after you confirm and send the email.'
      : 'A abrir o seu programa de email com o pedido preenchido. A reserva ou pedido só é enviado quando confirmar o envio no email.';
    location.href = `mailto:turigest@southbeach.co.mz?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
  });
});
