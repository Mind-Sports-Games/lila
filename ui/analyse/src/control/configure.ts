import type AnalyseCtrl from '../ctrl';
import { configure as configureBackgammon } from './variants/backgammon';
import { configure as configureGo } from './variants/go';
import { configure as configureAmazons } from './variants/amazons';
import { configure as configureLoa } from './variants/loa';
import { configure as configureRacingKings } from './variants/racingKings';
import { configure as configureDameo } from './variants/dameo';
import { configure as configureTogyzkumalak } from './variants/togyzkumalak';
import { configure as configureAbalone } from './variants/abalone';
import { configure as configureFlipello } from './variants/flipello';
import { configure as configureCrazy } from './variants/crazy';

type Configure = (ctrl: AnalyseCtrl) => void;

const byFamily: Partial<Record<GameFamilyKey, Configure>> = {
  backgammon: configureBackgammon,
  go: configureGo,
  amazons: configureAmazons,
  loa: configureLoa,
  abalone: configureAbalone,
  flipello: configureFlipello,
  shogi: configureCrazy,
};

const byKey: Partial<Record<VariantKey, Configure>> = {
  racingKings: configureRacingKings,
  dameo: configureDameo,
  togyzkumalak: configureTogyzkumalak,
  bestemshe: configureTogyzkumalak,
  crazyhouse: configureCrazy,
};

export const configureVariantControl = (ctrl: AnalyseCtrl): void => {
  (byFamily[ctrl.data.game.gameFamily] ?? byKey[ctrl.data.game.variant.key])?.(ctrl);
};
