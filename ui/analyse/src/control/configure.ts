import type AnalyseCtrl from '../ctrl';
import { configure as configureBackgammon } from './backgammon';

const BACKGAMMON_VARIANTS = ['backgammon', 'hyper', 'nackgammon'];

export const configureVariantControl = (ctrl: AnalyseCtrl): void => {
  const key = ctrl.data.game.variant.key;
  if (BACKGAMMON_VARIANTS.includes(key)) configureBackgammon(ctrl);
};
