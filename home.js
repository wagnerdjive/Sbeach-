// Quiet fade-in as home page sections scroll into view. Without JS, or with reduced motion, everything is simply visible.
(() => {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches || !('IntersectionObserver' in window)) return;
  const targets = document.querySelectorAll('.home .intro > *, .home .section-heading, .home .experience-card, .home .food-copy, .home .events-copy, .home .events-photo, .home .gallery-heading, .home .visit-top, .home .visit-details > div');
  if (!targets.length) return;
  document.documentElement.classList.add('reveal-ready');
  const observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return;
      entry.target.classList.add('is-visible');
      observer.unobserve(entry.target);
    });
  }, { threshold: 0.12, rootMargin: '0px 0px -6% 0px' });
  targets.forEach((target, index) => {
    target.setAttribute('data-reveal', '');
    target.style.transitionDelay = `${(index % 3) * 90}ms`;
    observer.observe(target);
  });
})();
