// A page drawing one game family ships that family's piece sheet alone. <body> names the sets
// of the others: written by the page's script when its data lists them, by the server
// otherwise. Content of another family arriving later asks for its sheet here.
const attr = 'data-piece-sets';

const named = (): Map<string, string> =>
  new Map(
    (document.body.getAttribute(attr) || '')
      .split(',')
      .filter(Boolean)
      .map(pair => pair.split(':') as [string, string]),
  );

export const ensureFamily = (family: string): void => {
  const id = `piece-sprite-${family}`,
    set = named().get(family);
  if (!set || document.getElementById(id)) return;
  const link = document.createElement('link');
  link.id = id;
  link.rel = 'stylesheet';
  link.href = playstrategy.assetUrl(`piece-css/${family}-${set}.css`);
  document.head.appendChild(link);
};

// a mini game carries its family among its classes
export const ensureFor = (el: Element): void => {
  for (const family of named().keys()) if (el.classList.contains(family)) ensureFamily(family);
};

// from a page whose data already lists the sets (round, analysis)
export const register = (sets?: { name: string; gameFamily: string }[]): void => {
  if (!sets?.length) return;
  document.body.setAttribute(attr, sets.map(s => `${s.gameFamily}:${s.name}`).join(','));
  document.querySelectorAll('.mini-game').forEach(ensureFor); // any drawn before this call
};

// a set picked for a family whose sheet is not here yet
export const remember = (family: string, set: string): void => {
  const sets = named();
  if (!sets.has(family)) return;
  sets.set(family, set);
  document.body.setAttribute(attr, [...sets].map(pair => pair.join(':')).join(','));
};
