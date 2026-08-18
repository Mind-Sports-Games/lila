let cache: 'init' | 'rec' | boolean = 'init';

export default function (): boolean {
  if (typeof cache == 'string') {
    if (cache == 'init')
      // only once
      window.addEventListener('resize', () => {
        cache = 'rec';
      }); // recompute on resize
    cache = !!getComputedStyle(document.body).getPropertyValue('--col3');
  }
  return cache;
}
