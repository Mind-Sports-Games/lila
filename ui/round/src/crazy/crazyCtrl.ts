import { isPlayerTurn } from 'game/game';
import { dragNewPiece } from 'chessground/drag';
import { setDropMode, cancelDropMode } from 'chessground/drop';
import RoundController from '../ctrl';
import * as cg from 'chessground/types';
import { RoundData } from '../interfaces';

export const pieceRoles: cg.Role[] = ['p-piece', 'n-piece', 'b-piece', 'r-piece', 'q-piece'];
export const pieceShogiRoles: cg.Role[] = ['p-piece', 'l-piece', 'n-piece', 's-piece', 'g-piece', 'b-piece', 'r-piece'];

export function drag(ctrl: RoundController, e: cg.MouchEvent): void {
  if (e.button !== undefined && e.button !== 0) return; // only touch or left click
  if (ctrl.replaying() || !ctrl.isPlaying()) return;
  const el = e.target as HTMLElement,
    role = el.getAttribute('data-role') as cg.Role,
    color = el.getAttribute('data-color') as cg.Color,
    number = el.getAttribute('data-nb');
  if (!role || !color || number === '0') return;
  e.stopPropagation();
  e.preventDefault();
  dragNewPiece(ctrl.chessground.state, { color, role }, e);
}

let dropWithKey = false;
let dropWithDrag = false;
let mouseIconsLoaded = false;

export function valid(data: RoundData, role: cg.Role, key: cg.Key): boolean {
  if (crazyKeys.length === 0) dropWithDrag = true;
  else {
    dropWithKey = true;
    if (!mouseIconsLoaded) preloadMouseIcons(data);
  }

  if (!isPlayerTurn(data)) return false;

  if (role === 'p-piece' && (key[1] === '1' || key[1] === '8')) return false;

  const dropStr = data.possibleDrops;

  if (typeof dropStr === 'undefined' || dropStr === null) return true;

  const drops = dropStr.match(/.{2}/g) || [];

  return drops.includes(key);
}

export function onEnd() {
  const store = playstrategy.storage.make('crazyKeyHist');
  if (dropWithKey) store.set(10);
  else if (dropWithDrag) {
    const cur = parseInt(store.get()!);
    if (cur > 0 && cur <= 10) store.set(cur - 1);
    else if (cur !== 0) store.set(3);
  }
}

export const crazyKeys: Array<number> = [];

export function init(ctrl: RoundController) {
  const k = window.Mousetrap;

  let activeCursor: string | undefined;

  const setDrop = () => {
    if (activeCursor) document.body.classList.remove(activeCursor);
    if (crazyKeys.length > 0) {
      const dropRoles = ctrl.data.game.variant.key === 'shogi' ? pieceShogiRoles : pieceRoles,
        role = dropRoles[crazyKeys[crazyKeys.length - 1] - 1],
        color = ctrl.data.player.color,
        crazyData = ctrl.data.crazyhouse;
      if (!crazyData) return;
      const nb = crazyData.pockets[color === 'white' ? 0 : 1][role];
      setDropMode(ctrl.chessground.state, nb > 0 ? { color, role } : undefined);
      if (ctrl.data.game.variant.key === 'shogi'){
        activeCursor = `cursor-${role}-shogi`;
      }else{
        activeCursor = `cursor-${color}-${role}-chess`;
      }
      document.body.classList.add(activeCursor);
    } else {
      cancelDropMode(ctrl.chessground.state);
      activeCursor = undefined;
    }
  };

  // This case is needed if the pocket piece becomes available while
  // the corresponding drop key is active.
  //
  // When the drop key is first pressed, the cursor will change, but
  // chessground.setDropMove(state, undefined) is called, which means
  // clicks on the board will not drop a piece.
  // If the piece becomes available, we call into chessground again.
  playstrategy.pubsub.on('ply', () => {
    if (crazyKeys.length > 0) setDrop();
  });
  const numDropPieces = ctrl.data.game.variant.key == 'crazyhouse' ? 5 : 7;
  for (let i = 1; i <= numDropPieces; i++) {
    const iStr = i.toString();
    k.bind(iStr, () => {
      if (!crazyKeys.includes(i)) {
        crazyKeys.push(i);
        setDrop();
      }
    }).bind(
      iStr,
      () => {
        const idx = crazyKeys.indexOf(i);
        if (idx >= 0) {
          crazyKeys.splice(idx, 1);
          if (idx === crazyKeys.length) {
            setDrop();
          }
        }
      },
      'keyup'
    );
  }

  const resetKeys = () => {
    if (crazyKeys.length > 0) {
      crazyKeys.length = 0;
      setDrop();
    }
  };

  window.addEventListener('blur', resetKeys);

  // Handle focus on input bars – these will hide keyup events
  window.addEventListener(
    'focus',
    e => {
      if (e.target && (e.target as HTMLElement).localName === 'input') resetKeys();
    },
    { capture: true }
  );

  if (playstrategy.storage.get('crazyKeyHist') !== '0') preloadMouseIcons(ctrl.data);
}

// zh keys has unacceptable jank when cursors need to dl,
// so preload when the feature might be used.
// Images are used in _zh.scss, which should be kept in sync.
function preloadMouseIcons(data: RoundData) {
  const colorKey = data.player.color[0];
  const colorNum = data.player.color == 'white' ? '0' : '1';
  for (const pKey of 'PNBRQ') fetch(playstrategy.assetUrl(`piece/cburnett/${colorKey}${pKey}.svg`));
  for (const pKey of ['FU', 'KY', 'KE', 'GI', 'KI', 'KA', 'HI'])
    fetch(playstrategy.assetUrl(`piece/shogi/${colorNum}${pKey}.svg`));
  mouseIconsLoaded = true;
}
