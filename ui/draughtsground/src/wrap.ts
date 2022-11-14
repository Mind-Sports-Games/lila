import { State } from './state';
import {
  playerIndexs,
  translateAway,
  translateAbs,
  posToTranslateAbs,
  key2pos,
  createEl,
  allKeys,
  san2alg,
  invertCoord,
  ranks as allRanks,
  files as allFiles,
} from './util';
import { createElement as createSVG } from './svg';
import { boardFields } from './board';
import { Elements, FieldNumber } from './types';

export default function wrap(element: HTMLElement, s: State, relative: boolean): Elements {
  // .cg-wrap (element passed to Draughtsground)
  //   cg-helper (10.0%)
  //     cg-container (1000%)
  //       cg-board
  //       svg
  //       coords.ranks
  //       coords.files
  //       piece.ghost

  element.innerHTML = '';

  // ensure the cg-wrap class is set
  // so bounds calculation can use the CSS width/height values
  // add that class yourself to the element before calling draughtsground
  // for a slight performance improvement! (avoids recomputing style)
  element.classList.add('cg-wrap');

  playerIndexs.forEach(c => element.classList.toggle('orientation-' + c, s.orientation === c));
  element.classList.toggle('manipulable', !s.viewOnly);

  const helper = createEl('cg-helper');
  element.appendChild(helper);
  const container = createEl('cg-container');
  helper.appendChild(container);

  const board = createEl('cg-board');
  container.appendChild(board);

  let svg: SVGElement | undefined;
  if (s.drawable.visible && !relative) {
    svg = createSVG('svg');
    svg.appendChild(createSVG('defs'));
    container.appendChild(svg);
  }

  if (s.coordinates) {
    if (s.coordinates === 2) {
      if (s.coordSystem === 1) {
        const klasses = 'is64' + (s.orientation === 'p2' ? ' p2' : ' p1');
        container.appendChild(renderCoords(allRanks, 'ranks algebraic ' + klasses));
        container.appendChild(renderCoords(allFiles, 'files algebraic ' + klasses));
      } else {
        const files: number[] = [],
          ranks: number[] = [],
          rankBase = s.boardSize[0] / 2,
          fields = (s.boardSize[0] * s.boardSize[1]) / 2,
          fileSteps = s.boardSize[1] / 2,
          klasses = 'is' + fields * 2 + (s.orientation === 'p2' ? ' p2' : '');
        if (s.coordSystem === 0) {
          if (s.orientation === 'p2') {
            for (let i = 1; i <= rankBase; i++) files.push(i);
            for (let i = fileSteps - 1; i >= 0; i--) ranks.push(rankBase + s.boardSize[0] * i + 1);
          } else {
            for (let i = fields - rankBase + 1; i <= fields; i++) files.push(i);
            for (let i = 0; i < fileSteps; i++) ranks.push(rankBase + s.boardSize[0] * i);
          }
        } else {
          //coordSystem === 2 therefore inverted numbers
          if (s.orientation === 'p2') {
            for (let i = fields; i > fields - rankBase; i--) files.push(i);
            for (let i = 0; i < fileSteps; i++) ranks.push(rankBase + s.boardSize[0] * i);
          } else {
            for (let i = rankBase; i > 0; i--) files.push(i);
            for (let i = fileSteps; i > 0; i--) ranks.push(s.boardSize[0] * i - rankBase + 1);
          }
        }
        container.appendChild(renderCoords(ranks, 'ranks fieldnumbers ' + klasses));
        container.appendChild(renderCoords(files, 'files fieldnumbers ' + klasses));
      }
    } else if (!relative && s.coordinates === 1) {
      renderFieldnumbers(board, s, board.getBoundingClientRect());
    }
  }

  let ghost: HTMLElement | undefined;
  if (s.draggable.showGhost && !relative) {
    ghost = createEl('piece', 'ghost');
    translateAway(ghost);
    container.appendChild(ghost);
  }

  return {
    board,
    container,
    ghost,
    svg,
  };
}

function coordText(coordSystem: number | undefined, san: string): string {
  if (coordSystem === 1) {
    return san2alg[san];
  } else if (coordSystem === 2) {
    return invertCoord(san);
  } else {
    return san;
  }
}

function renderFieldnumbers(element: HTMLElement, s: State, bounds: ClientRect) {
  const asP1 = s.orientation !== 'p2',
    count = boardFields(s);
  for (let f = 1; f <= count; f++) {
    const field = createEl('fieldnumber', 'p2') as FieldNumber,
      san = f.toString(),
      k = allKeys[f - 1];
    field.textContent = coordText(s.coordSystem, san);
    field.cgKey = k;
    const coords = posToTranslateAbs(bounds, s.boardSize)(key2pos(k, s.boardSize), asP1, 0);
    translateAbs(field, [coords['0'], coords['1']]);
    element.appendChild(field);
  }
}

function renderCoords(elems: any[], className: string): HTMLElement {
  const el = createEl('coords', className);
  let f: HTMLElement;
  for (const i in elems) {
    f = createEl('coord');
    f.textContent = elems[i];
    el.appendChild(f);
  }
  return el;
}
