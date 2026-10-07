// Quiet transitions: sections fade and rise as they scroll into view, on every page and also for content that loads later
// (event cards, gallery, menus, albums). Without JS, or with reduced motion, everything is simply visible.
(() => {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches || !('IntersectionObserver' in window)) return;
  const selector = [
    // home
    '.refined .intro > *', '.refined .section-heading', '.refined .experience-card', '.refined .food-copy', '.refined .events-copy',
    '.refined .events-photo', '.refined .gallery-heading', '.refined .visit-top', '.refined .visit-details > div',
    // inner pages
    '.refined .page-intro > div', '.refined .story-section > *', '.refined .concept-section .eyebrow', '.refined .concept-section h2',
    '.refined .concept-grid article', '.refined .feature-strip article', '.refined .sports-features article', '.refined .image-text-band > div',
    '.refined .page-cta > *', '.refined .about-photo-band > div', '.refined .hours-section > *',
    '.refined .menus-heading', '.refined .menu-tabs', '.refined .menu-groups-nav', '.refined .menu-section',
    '.refined .gallery-page-heading', '.refined .gallery-filters', '.refined .gallery-card',
    '.refined .contact-details > *', '.refined .map-panel', '.refined .event-state > *',
    '.refined .events-list-heading', '.refined .event-card', '.refined .past-event-grid > a', '.refined .past-events .section-heading',
    '.refined .inquiry-copy', '.refined .inquiry-form',
    '.refined .reservation-intro', '.refined .reservation-form-heading', '.refined .reservation-form', '.refined .reservation-note',
    '.refined .ticket-preview-heading', '.refined .live-event-head', '.refined .ticket-option', '.refined .checkout-card',
    '.refined .popular-heading', '.refined .drink-card', '.refined .popular-more',
    '.refined .past-event-head > *', '.refined .pass-card, .refined .pass-card > *'
  ].join(',');
  document.documentElement.classList.add('reveal-ready');

  const seen = new WeakSet();
  const observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return;
      const node = entry.target;
      node.classList.add('is-visible');
      observer.unobserve(node);
      // Once shown, give the element back its own transitions (hover effects and so on).
      setTimeout(() => { node.removeAttribute('data-reveal'); node.classList.remove('is-visible'); node.style.transitionDelay = ''; }, 1300);
    });
  }, { threshold: 0.08, rootMargin: '0px 0px -5% 0px' });

  function register(root) {
    root.querySelectorAll(selector).forEach((node) => {
      if (seen.has(node)) return;
      seen.add(node);
      const index = node.parentElement ? [...node.parentElement.children].indexOf(node) : 0;
      node.setAttribute('data-reveal', '');
      node.style.transitionDelay = `${(index % 4) * 80}ms`;
      observer.observe(node);
    });
  }

  register(document);
  let queued = false;
  new MutationObserver(() => {
    if (queued) return;
    queued = true;
    requestAnimationFrame(() => { queued = false; register(document); });
  }).observe(document.body, { childList: true, subtree: true });
})();
