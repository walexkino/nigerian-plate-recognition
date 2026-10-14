(function () {
  'use strict';
  const root = document.documentElement;
  const button = document.querySelector('#theme-toggle');
  let saved;
  try { saved = window.localStorage.getItem('platelab-theme'); } catch (_) {}
  let theme = saved === 'light' || saved === 'dark' ? saved :
    (window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark');
  function apply() {
    root.dataset.theme = theme;
    root.style.colorScheme = theme;
    button.textContent = theme === 'dark' ? '☀ Light mode' : '☾ Dark mode';
    button.setAttribute('aria-pressed', String(theme === 'dark'));
    button.setAttribute('aria-label', `Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`);
  }
  button.onclick = () => {
    theme = theme === 'dark' ? 'light' : 'dark';
    apply();
    try { window.localStorage.setItem('platelab-theme', theme); } catch (_) {}
  };
  apply();
})();
