import type AnalyseCtrl from '../../ctrl';
import { readDestsAbalone } from 'stratutils';

export const configure = (ctrl: AnalyseCtrl): void => {
  ctrl.controlConfig.mutateCgOpts = (_node, config) => {
    const abaloneDests = readDestsAbalone(ctrl.node.dests);
    if (config.movable && abaloneDests !== null) {
      if (!config.movable.playerIndex && !ctrl.embed) config.movable.playerIndex = config.turnPlayerIndex;
      const canMove = config.movable.playerIndex === config.turnPlayerIndex;
      config.movable.dests = ((canMove && abaloneDests) || new Map()) as unknown as typeof config.movable.dests;
    }
  };
};
