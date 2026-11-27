export const favourites = (): Set<string> => new Set((document.body.dataset.fav || '').split(' ').filter(k => k));

export const setFavourites = (favs: Set<string>): void => {
  if (favs.size) document.body.dataset.fav = [...favs].sort().join(' ');
  else delete document.body.dataset.fav;
};

export const favMark = (keys: string[], favs: Set<string>): Record<string, boolean> => {
  const nb = keys.filter(k => favs.has(k)).length;
  return { fav: nb > 0, 'fav-some': nb > 0 && nb < keys.length };
};

interface TournamentGames {
  variant: { key: string };
  medleyVariantKeys?: string[];
}

export const isFavTournament = (tour: TournamentGames, favs: Set<string>): boolean =>
  (tour.medleyVariantKeys || [tour.variant.key]).some(k => favs.has(k));
