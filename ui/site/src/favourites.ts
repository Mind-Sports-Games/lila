import * as xhr from 'common/xhr';
import { favourites, setFavourites } from 'common/favourites';

playstrategy.load.then(() => {
  const root = document.querySelector<HTMLElement>('main.favourites');
  if (!root) return;

  let favs = favourites();

  const groupKeys = (el: HTMLElement): string[] =>
    [...el.closest('.favourites__group')!.querySelectorAll<HTMLElement>('.series-links [data-key]')].map(
      chip => chip.dataset.key!,
    );

  const setStar = (button: HTMLElement, on: boolean, some: boolean) => {
    button.setAttribute('aria-pressed', String(on));
    button.title = (on ? root.dataset.remove : root.dataset.add) || '';
    const star = button.querySelector('.fav-star');
    star?.classList.toggle('on', on);
    star?.classList.toggle('some', some);
  };

  const render = (set: Set<string>) => {
    favs = set;
    root.querySelectorAll<HTMLElement>('.series-links [data-key]').forEach(chip => {
      const on = set.has(chip.dataset.key!);
      chip.classList.toggle('fav', on);
      chip.setAttribute('aria-pressed', String(on));
    });
    root.querySelectorAll<HTMLElement>('.favourites__star[data-key]').forEach(star => {
      setStar(star, set.has(star.dataset.key!), false);
    });
    root.querySelectorAll<HTMLElement>('.favourites__group-star').forEach(star => {
      const keys = groupKeys(star);
      const nb = keys.filter(k => set.has(k)).length;
      setStar(star, nb === keys.length, nb > 0 && nb < keys.length);
      const count = star.parentElement?.querySelector<HTMLElement>('.favourites__count');
      if (count) count.textContent = nb === keys.length ? count.dataset.all! : count.dataset.of!.replace('%s', `${nb}`);
    });
    setFavourites(set);
  };

  root.addEventListener('click', e => {
    const target = (e.target as HTMLElement).closest<HTMLElement>('[data-url]');
    if (!target) return;
    e.preventDefault();
    const on = target.getAttribute('aria-pressed') !== 'true';
    const previous = favs;
    const next = new Set(previous);
    (target.dataset.key ? [target.dataset.key] : groupKeys(target)).forEach(k => (on ? next.add(k) : next.delete(k)));
    render(next);
    xhr.json(xhr.url(target.dataset.url!, { v: on ? 1 : 0 }), { method: 'post' }).then(
      (res: { favourites: string[] }) => render(new Set(res.favourites)),
      () => render(previous),
    );
  });
});
