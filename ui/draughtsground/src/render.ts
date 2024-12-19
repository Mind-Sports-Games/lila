import { State } from './state';
import { key2pos, createEl } from './util';
import { p1Pov } from './board';
import * as util from './util';
import { AnimCurrent, AnimVectors, AnimVector, AnimCaptures, AnimRoles } from './anim';
import { DragCurrent } from './drag';
import * as cg from './types';

// `$playerIndex $role`
type PieceName = string;

interface SamePieces {
  [key: string]: boolean;
}
interface SameSquares {
  [key: string]: boolean;
}
interface SquareClasses {
  [key: string]: string;
}

// ported from https://github.com/veloce/lichobile/blob/master/src/js/draughtsground/view.js
// in case of bugs, blame @veloce
export function render(s: State): void {
  const asP1: boolean = p1Pov(s),
    bs = s.boardSize,
    posToTranslate = s.dom.relative ? util.posToTranslateRel(bs) : util.posToTranslateAbs(s.dom.bounds(), bs),
    translate = s.dom.relative ? util.translateRel : util.translateAbs,
    boardEl: HTMLElement = s.dom.elements.board,
    pieces: cg.Pieces = s.pieces,
    curAnim: AnimCurrent | undefined = s.animation.current,
    anims: AnimVectors = curAnim ? curAnim.plan.anims : new Map(),
    temporaryPieces: AnimCaptures = curAnim ? curAnim.plan.captures : new Map(),
    temporaryRoles: AnimRoles = curAnim ? curAnim.plan.tempRole : new Map(),
    curDrag: DragCurrent | undefined = s.draggable.current,
    squares: SquareClasses = computeSquareClasses(s),
    samePieces: SamePieces = {},
    sameSquares: SameSquares = {},
    movedPieces: Map<PieceName, cg.PieceNode[]> = new Map(),
    movedSquares: Map<string, cg.SquareNode[]> = new Map();
  let animDoubleKey =
    curAnim &&
    curAnim.lastMove &&
    curAnim.lastMove.length > 2 &&
    curAnim.lastMove[0] === curAnim.lastMove[curAnim.lastMove.length - 1]
      ? curAnim.lastMove[0]
      : undefined;
  let k: cg.Key,
    el: cg.PieceNode | cg.SquareNode,
    pieceAtKey: cg.Piece | undefined,
    elPieceName: PieceName,
    anim: AnimVector | undefined,
    tempPiece: cg.Piece | undefined,
    tempRole: cg.Role | undefined,
    pMvdset: cg.PieceNode[] | undefined,
    pMvd: cg.PieceNode | undefined,
    sMvdset: cg.SquareNode[] | undefined,
    sMvd: cg.SquareNode | undefined;

  // walk over all board dom elements, apply animations and flag moved pieces
  el = boardEl.firstChild as cg.PieceNode | cg.SquareNode;
  while (el) {
    k = el.cgKey;
    if (isPieceNode(el)) {
      pieceAtKey = pieces.get(k);
      anim = anims.get(k);
      tempPiece = temporaryPieces.get(k);
      tempRole = temporaryRoles.get(k);
      elPieceName = el.cgPiece;
      // if piece not being dragged anymore, remove dragging style
      if (el.cgDragging && (!curDrag || curDrag.orig !== k)) {
        el.classList.remove('dragging');
        translate(el, posToTranslate(key2pos(k, bs), asP1, 0));
        el.cgDragging = false;
      }
      if (el.classList.contains('temporary') && tempPiece) {
        // piece belongs here, check if it still has the right properties
        const fullPieceName = pieceNameOf(tempPiece) + ' temporary';
        if (elPieceName !== fullPieceName) el.className = fullPieceName;
        samePieces[k] = true;
      } else if (pieceAtKey) {
        // there is now a piece at this dom key
        // continue animation if already animating and same piece
        // (otherwise it could animate a captured piece)
        if (anim && el.cgAnimating && elPieceName === pieceNameOf(pieceAtKey)) {
          animDoubleKey = undefined; // only needed to get the animation started
          const pos = key2pos(k, bs);
          pos[0] += anim[2];
          pos[1] += anim[3];
          if (curAnim && curAnim.plan.nextPlan) {
            const atK = curAnim.plan.nextPlan.anims.get(k);
            if (atK) {
              pos[0] += atK[2];
              pos[1] += atK[3];
            }
          }
          el.classList.add('anim');
          if (tempRole) {
            el.className = el.className.replace(pieceAtKey.role, tempRole);
            el.classList.add('temprole');
          } else if (el.classList.contains('temprole')) {
            el.classList.remove('temprole');
            if (pieceAtKey.role === 'king') el.className = el.className.replace('man', 'king');
            else if (pieceAtKey.role === 'man') el.className = el.className.replace('king', 'man');
          }
          translate(el, posToTranslate(pos, asP1, anim[4]));
        } else if (el.cgAnimating) {
          el.cgAnimating = false;
          el.classList.remove('anim');
          if (el.classList.contains('temprole')) {
            el.classList.remove('temprole');
            if (pieceAtKey.role === 'king') el.className = el.className.replace('man', 'king');
            else if (pieceAtKey.role === 'man') el.className = el.className.replace('king', 'man');
          }
          translate(el, posToTranslate(key2pos(k, bs), asP1, 0));
          if (s.addPieceZIndex) el.style.zIndex = posZIndex(key2pos(k, bs), asP1);
        }

        // same piece: flag as same. Exception for capture ending on the start square, as no pieces are added or removed
        if (elPieceName === pieceNameOf(pieceAtKey) && k !== animDoubleKey) {
          samePieces[k] = true;
        }
        // different piece: flag as moved unless it is a fading piece
        else {
          appendValue(movedPieces, elPieceName, el);
        }
      } else {
        // no piece: flag as moved
        appendValue(movedPieces, elPieceName, el);
      }
    } else if (isSquareNode(el)) {
      const cn = el.className;
      if (squares[k] === cn) sameSquares[k] = true;
      else appendValue(movedSquares, cn, el);
    }
    el = el.nextSibling as cg.PieceNode | cg.SquareNode;
  }

  // walk over all squares in current set, apply dom changes to moved squares
  // or append new squares
  for (const sk in squares) {
    if (!sameSquares[sk]) {
      sMvdset = movedSquares.get(squares[sk]);
      sMvd = sMvdset && sMvdset.pop();
      const translation = posToTranslate(key2pos(sk as cg.Key, bs), asP1, 0);
      if (sMvd) {
        sMvd.cgKey = sk as cg.Key;
        translate(sMvd, translation);
      } else {
        const squareNode = createEl('square', squares[sk]) as cg.SquareNode;
        squareNode.cgKey = sk as cg.Key;
        translate(squareNode, translation);
        boardEl.insertBefore(squareNode, boardEl.firstChild);
      }
    }
  }

  // walk over all pieces in current set, apply dom changes to moved pieces
  // or append new pieces
  for (const [k, p] of pieces) {
    anim = anims.get(k);
    tempPiece = temporaryPieces.get(k);
    // @ts-ignore
    tempRole = temporaryRoles && temporaryRoles.get(k);
    if (!samePieces[k] && !tempPiece) {
      pMvdset = movedPieces.get(pieceNameOf(p));
      pMvd = pMvdset && pMvdset.pop();
      // a same piece was moved
      if (pMvd) {
        // apply dom changes
        pMvd.cgKey = k;
        const pos = key2pos(k, bs);
        if (s.addPieceZIndex) pMvd.style.zIndex = posZIndex(pos, asP1);
        let shift: number;
        if (anim) {
          pMvd.cgAnimating = true;
          pMvd.classList.add('anim');
          pos[0] += anim[2];
          pos[1] += anim[3];
          shift = anim[4];
          if (curAnim && curAnim.plan.nextPlan) {
            const atK = curAnim.plan.nextPlan.anims.get(k);
            if (atK) {
              pos[0] += atK[2];
              pos[1] += atK[3];
            }
          }
        } else shift = 0;
        translate(pMvd, posToTranslate(pos, asP1, shift));
      }
      // no piece in moved obj: insert the new piece
      // assumes the new piece is not being dragged
      else {
        const pieceName = pieceNameOf(p),
          pieceNode = createEl('piece', pieceName) as cg.PieceNode,
          pos = key2pos(k, bs);

        pieceNode.cgPiece = pieceName;
        pieceNode.cgKey = k;
        let shift: number;
        if (anim) {
          pieceNode.cgAnimating = true;
          pos[0] += anim[2];
          pos[1] += anim[3];
          shift = anim[4];
          if (tempRole) {
            pieceNode.className = pieceNode.className.replace(p.role, tempRole);
            pieceNode.classList.add('temprole');
          }
        } else shift = 0;
        translate(pieceNode, posToTranslate(pos, asP1, shift));

        if (s.addPieceZIndex) pieceNode.style.zIndex = posZIndex(pos, asP1);

        boardEl.appendChild(pieceNode);
      }
    }
  }

  for (const i in temporaryPieces) {
    k = i as cg.Key;
    tempPiece = temporaryPieces.get(k);
    if (tempPiece && !samePieces[k]) {
      const pieceName = pieceNameOf(tempPiece) + ' temporary',
        pieceNode = createEl('piece', pieceName) as cg.PieceNode,
        pos = key2pos(k, bs);
      pieceNode.cgPiece = pieceName;
      pieceNode.cgKey = k;
      translate(pieceNode, posToTranslate(pos, asP1, 0));
      if (s.addPieceZIndex) pieceNode.style.zIndex = posZIndex(pos, asP1);
      boardEl.appendChild(pieceNode);
    }
  }

  // remove any element that remains in the moved sets
  for (const nodes of movedPieces.values()) removeNodes(s, nodes);
  for (const nodes of movedSquares.values()) removeNodes(s, nodes);
}

export function updateBounds(s: State) {
  if (s.dom.relative) return;
  const asP1: boolean = p1Pov(s),
    posToTranslate = util.posToTranslateAbs(s.dom.bounds(), s.boardSize);
  let el = s.dom.elements.board.firstChild as HTMLElement | undefined;
  while (el) {
    if ((isPieceNode(el) && !el.cgAnimating) || isSquareNode(el) || isFieldNumber(el)) {
      util.translateAbs(el, posToTranslate(key2pos(el.cgKey, s.boardSize), asP1, 0));
    }
    el = el.nextSibling as HTMLElement | undefined;
  }
}

function isPieceNode(el: HTMLElement): el is cg.PieceNode {
  return el.tagName === 'PIECE';
}
function isSquareNode(el: HTMLElement): el is cg.SquareNode {
  return el.tagName === 'SQUARE';
}
function isFieldNumber(el: HTMLElement): el is cg.FieldNumber {
  return el.tagName === 'FIELDNUMBER';
}

function removeNodes(s: State, nodes: HTMLElement[]): void {
  for (const i in nodes) s.dom.elements.board.removeChild(nodes[i]);
}

function posZIndex(pos: cg.Pos, asP1: boolean): string {
  let z = 2 + (pos[1] - 1) * 8 + (8 - pos[0]);
  if (asP1) z = 67 - z;
  return z + '';
}

export function pieceNameOf(piece: cg.Piece): string {
  if (piece.role === 'ghostman') return `${piece.playerIndex} man ghost`;
  else if (piece.role === 'ghostking') {
    if (piece.kingMoves && piece.kingMoves > 0) return `${piece.playerIndex} king ghost king${piece.kingMoves}`;
    else return `${piece.playerIndex} king ghost`;
  } else if (piece.role === 'king' && piece.kingMoves && piece.kingMoves > 0)
    return `${piece.playerIndex} king king${piece.kingMoves}`;
  else return `${piece.playerIndex} ${piece.role}`;
}

function computeSquareClasses(s: State): SquareClasses {
  const squares: SquareClasses = {};
  let i: any, k: cg.Key;
  if (s.lastMove && s.highlight.lastMove)
    for (i in s.lastMove) {
      if (s.lastMove[i] !== s.selected) addSquare(squares, s.lastMove[i], 'last-move');
    }
  if (s.selected) {
    addSquare(squares, s.selected, 'selected');
    if (s.movable.showDests) {
      const dests = s.movable.dests && s.movable.dests.get(s.selected);
      if (dests)
        for (i in dests) {
          k = dests[i];
          addSquare(squares, k, 'move-dest' + (s.pieces.has(k) ? ' oc' : ''));
        }
      const pDests = s.premovable.dests;
      if (pDests)
        for (i in pDests) {
          k = pDests[i];
          addSquare(squares, k, 'premove-dest' + (s.pieces.has(k) ? ' oc' : ''));
        }
    }
  }
  const premove = s.premovable.current;
  if (premove) for (i in premove) addSquare(squares, premove[i], 'current-premove');
  else if (s.predroppable.current) addSquare(squares, s.predroppable.current.key, 'current-premove');

  const o = s.exploding;
  if (o) for (i in o.keys) addSquare(squares, o.keys[i], 'exploding' + o.stage);

  return squares;
}

function addSquare(squares: SquareClasses, key: cg.Key, klass: string): void {
  if (squares[key]) squares[key] += ' ' + klass;
  else squares[key] = klass;
}

function appendValue<K, V>(map: Map<K, V[]>, key: K, value: V): void {
  const arr = map.get(key);
  if (arr) arr.push(value);
  else map.set(key, [value]);
}
