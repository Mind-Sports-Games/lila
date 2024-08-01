import * as cg from './types';
import * as util from './util';
import { State } from './state';
import { calcCaptKey } from './board';

export type Mutation<A> = (state: State) => A;

// 0,1 animation goal
// 2,3 animation current status
// 4   x-shifting parameter
export type AnimVector = cg.NumberQuadShift;

export type AnimVectors = Map<cg.Key, AnimVector>;
export type AnimCaptures = Map<cg.Key, cg.Piece>;
export type AnimFadings = Map<cg.Key, cg.Piece>;

export type AnimRoles = Map<cg.Key, cg.Role>;
export interface AnimPlan {
  anims: AnimVectors;
  fadings: AnimFadings;
  captures: AnimCaptures;
  tempRole: AnimRoles;
  nextPlan?: AnimPlan;
}

export interface AnimCurrent {
  start: DOMHighResTimeStamp;
  frequency: cg.KHz;
  plan: AnimPlan;
  lastMove?: cg.Key[];
}

interface AnimPiece {
  key: cg.Key;
  pos: cg.Pos;
  piece: cg.Piece;
}
type AnimPieces = Map<cg.Key, AnimPiece>;

interface SamePieces {
  [key: string]: boolean;
}

export function anim<A>(mutation: Mutation<A>, state: State, fadeOnly = false, noCaptSequences = false): A {
  return state.animation.enabled ? animate(mutation, state, fadeOnly, noCaptSequences) : render(mutation, state);
}

export function render<A>(mutation: Mutation<A>, state: State): A {
  const result = mutation(state);
  state.dom.redraw();
  return result;
}

export function animationDuration(state: State) {
  const anim = state.animation.current;
  if (!anim) return 0;
  let plan = anim.plan,
    total;
  const factor = plan.nextPlan && !util.isObjectEmpty(plan.nextPlan.anims) ? 2.2 : 1,
    duration = state.animation.duration / factor;
  total = duration;
  while (plan.nextPlan && !util.isObjectEmpty(plan.nextPlan.anims)) {
    plan = plan.nextPlan;
    total += duration;
  }
  return total;
}

function makePiece(key: cg.Key, boardSize: cg.BoardSize, piece: cg.Piece): AnimPiece {
  return {
    key: key,
    pos: util.key2pos(key, boardSize),
    piece: piece,
  };
}

function closer(piece: AnimPiece, pieces: AnimPiece[]): AnimPiece | undefined {
  return pieces.sort((p1, p2) => {
    return util.distanceSq(piece.pos, p1.pos) - util.distanceSq(piece.pos, p2.pos);
  })[0];
}

function ghostPiece(piece: cg.Piece): cg.Piece {
  if (piece.role === 'man')
    return { role: 'ghostman', playerIndex: piece.playerIndex, promoted: piece.promoted, kingMoves: piece.kingMoves };
  else if (piece.role === 'king')
    return { role: 'ghostking', playerIndex: piece.playerIndex, promoted: piece.promoted, kingMoves: piece.kingMoves };
  else
    return { role: piece.role, playerIndex: piece.playerIndex, promoted: piece.promoted, kingMoves: piece.kingMoves };
}

function isPromotablePos(playerIndex: cg.PlayerIndex, pos: cg.Pos, boardSize: cg.BoardSize): boolean {
  return (playerIndex === 'p1' && pos[1] === 1) || (playerIndex === 'p2' && pos[1] === boardSize[1]);
}

function computePlan(prevPieces: cg.Pieces, current: State, fadeOnly = false, noCaptSequences = false): AnimPlan {
  const missingsW: AnimPiece[] = [],
    missingsB: AnimPiece[] = [];
  let newsW: AnimPiece[] = [],
    newsB: AnimPiece[] = [];
  const prePieces: AnimPieces = new Map(),
    samePieces: SamePieces = {},
    bs = current.boardSize,
    variant = (current.movable && current.movable.variant) || (current.premovable && current.premovable.variant);
  let curP: cg.Piece | undefined,
    preP: AnimPiece | undefined,
    i: any,
    prevGhosts = 0;
  for (const [i, p] of prevPieces) {
    prePieces.set(i, makePiece(i, bs, p));
    if (p.role === 'ghostman' || p.role === 'ghostking') prevGhosts++;
  }
  for (const key of util.allKeys) {
    curP = current.pieces.get(key);
    preP = prePieces.get(key);
    if (curP) {
      if (preP) {
        if (!util.samePiece(curP, preP.piece)) {
          if (preP.piece.playerIndex === 'p1') missingsW.push(preP);
          else missingsB.push(preP);
          if (curP.playerIndex === 'p1') newsW.push(makePiece(key, bs, curP));
          else newsB.push(makePiece(key, bs, curP));
        }
      } else {
        if (curP.playerIndex === 'p1') newsW.push(makePiece(key, bs, curP));
        else newsB.push(makePiece(key, bs, curP));
      }
    } else if (preP) {
      if (preP.piece.playerIndex === 'p1') missingsW.push(preP);
      else missingsB.push(preP);
    }
  }

  const plan: AnimPlan = { anims: new Map(), captures: new Map(), tempRole: new Map(), fadings: new Map() };
  let nextPlan: AnimPlan = { anims: new Map(), captures: new Map(), tempRole: new Map(), fadings: new Map() };

  if (newsW.length > 1 && missingsW.length > 0) {
    newsW = newsW.sort((p1, p2) => {
      return util.distanceSq(missingsW[0].pos, p1.pos) - util.distanceSq(missingsW[0].pos, p2.pos);
    });
  }
  if (newsB.length > 1 && missingsB.length > 0) {
    newsB = newsB.sort((p1, p2) => {
      return util.distanceSq(missingsB[0].pos, p1.pos) - util.distanceSq(missingsB[0].pos, p2.pos);
    });
  }

  //Never animate capture sequences with ghosts on board, fixes retriggered animation when startsquare is touched again later in the sequence
  const captAnim =
    !noCaptSequences && (prevGhosts === 0 || current.animateFrom) && current.lastMove && current.lastMove.length > 2;
  const animateFrom = current.animateFrom || 0;

  //Animate captures with same start/end square
  if (
    !fadeOnly &&
    captAnim &&
    current.lastMove &&
    current.lastMove[animateFrom] === current.lastMove[current.lastMove.length - 1]
  ) {
    const doubleKey = current.lastMove[animateFrom];
    curP = current.pieces.get(doubleKey);
    preP = prePieces.get(doubleKey);
    if (!!curP && !!preP && curP.playerIndex === 'p1' && missingsB.length !== 0) {
      missingsW.push(preP);
      newsW.push(makePiece(doubleKey, bs, curP));
    } else if (!!curP && !!preP && curP.playerIndex === 'p2' && missingsW.length !== 0) {
      missingsB.push(preP);
      newsB.push(makePiece(doubleKey, bs, curP));
    }
  }

  const missings: AnimPiece[] = missingsW.concat(missingsB),
    news: AnimPiece[] = newsW.concat(newsB);

  if (news.length && missings.length && !fadeOnly) {
    news.forEach(newP => {
      let maybePromote = false,
        filteredMissings = missings.filter(
          p =>
            !samePieces[p.key] &&
            newP.piece.playerIndex === p.piece.playerIndex &&
            (newP.piece.role === p.piece.role ||
              (p.piece.role === 'man' &&
                newP.piece.role === 'king' &&
                isPromotablePos(newP.piece.playerIndex, newP.pos, bs)) ||
              (p.piece.role === 'king' &&
                newP.piece.role === 'man' &&
                isPromotablePos(p.piece.playerIndex, p.pos, bs))),
        );
      if (!filteredMissings.length && (variant === 'russian' || variant === 'pool' || variant === 'english')) {
        maybePromote = true;
        filteredMissings = missings.filter(
          p =>
            !samePieces[p.key] &&
            newP.piece.playerIndex === p.piece.playerIndex &&
            ((p.piece.role === 'man' && newP.piece.role === 'king') ||
              (p.piece.role === 'king' && newP.piece.role === 'man')),
        );
      }
      preP = closer(newP, filteredMissings);
      if (preP) {
        samePieces[preP.key] = true;
        let tempRole: cg.Role | undefined =
          preP.piece.role === 'man' &&
          newP.piece.role === 'king' &&
          (maybePromote || isPromotablePos(newP.piece.playerIndex, newP.pos, bs))
            ? 'man'
            : undefined;
        if (
          captAnim &&
          current.lastMove &&
          current.lastMove[animateFrom] === preP.key &&
          current.lastMove[current.lastMove.length - 1] === newP.key
        ) {
          let lastPos: cg.Pos = util.key2pos(current.lastMove[animateFrom + 1], bs),
            newPos: cg.Pos;
          plan.anims.set(newP.key, getVector(preP.pos, lastPos));
          plan.nextPlan = nextPlan;
          if (tempRole) plan.tempRole.set(newP.key, tempRole);

          const captKeys: Array<cg.Key> = new Array<cg.Key>();
          let captKey = calcCaptKey(prevPieces, bs, preP.pos[0], preP.pos[1], lastPos[0], lastPos[1]);
          if (captKey) {
            const piece = prevPieces.get(captKey);
            if (piece) {
              captKeys.push(captKey);
              prevPieces.set(captKey, ghostPiece(piece));
            }
          }

          plan.captures = new Map();
          missings.forEach(p => {
            if (p.piece.playerIndex !== newP.piece.playerIndex) {
              if (captKeys.indexOf(p.key) !== -1) plan.captures.set(p.key, ghostPiece(p.piece));
              else plan.captures.set(p.key, p.piece);
            }
          });

          let newPlan: AnimPlan = { anims: new Map(), captures: new Map(), tempRole: new Map(), fadings: new Map() };
          for (i = animateFrom + 2; i < current.lastMove.length; i++) {
            newPos = util.key2pos(current.lastMove[i], bs);
            const v = getVector(lastPos, newPos);
            v[2] = lastPos[0] - newP.pos[0];
            v[3] = lastPos[1] - newP.pos[1];
            nextPlan.anims.set(newP.key, v);
            nextPlan.nextPlan = newPlan;
            if (tempRole) {
              if (variant === 'russian' && isPromotablePos(newP.piece.playerIndex, lastPos, bs)) {
                tempRole = undefined;
              } else {
                nextPlan.tempRole.set(newP.key, tempRole);
              }
            }
            captKey = calcCaptKey(prevPieces, bs, lastPos[0], lastPos[1], newPos[0], newPos[1]);
            if (captKey) {
              const piece = prevPieces.get(captKey);
              if (piece) {
                captKeys.push(captKey);
                prevPieces.set(captKey, ghostPiece(piece));
              }
            }

            nextPlan.captures = new Map();
            missings.forEach(p => {
              if (p.piece.playerIndex !== newP.piece.playerIndex) {
                if (captKeys.indexOf(p.key) !== -1) nextPlan.captures.set(p.key, ghostPiece(p.piece));
                else nextPlan.captures.set(p.key, p.piece);
              }
            });

            lastPos = newPos;
            nextPlan = newPlan;
            newPlan = { anims: new Map(), captures: new Map(), tempRole: new Map(), fadings: new Map() };
          }
        } else {
          plan.anims.set(newP.key, getVector(preP.pos, newP.pos));
          if (tempRole) plan.tempRole.set(newP.key, tempRole);
        }
      }
    });
  }
  return plan;
}

function getVector(preP: cg.Pos, newP: cg.Pos): AnimVector {
  if (preP[1] % 2 === 0 && newP[1] % 2 === 0) return [preP[0] - newP[0], preP[1] - newP[1], 0, 0, -0.5];
  else if (preP[1] % 2 !== 0 && newP[1] % 2 === 0) return [preP[0] - newP[0] + 0.5, preP[1] - newP[1], 0, 0, -0.5];
  else if (preP[1] % 2 === 0 && newP[1] % 2 !== 0) return [preP[0] - newP[0] - 0.5, preP[1] - newP[1], 0, 0, 0];
  else return [preP[0] - newP[0], preP[1] - newP[1], 0, 0, 0];
}

function step(state: State, now: DOMHighResTimeStamp): void {
  let cur = state.animation.current;
  if (cur === undefined) {
    // animation was canceled :(
    if (!state.dom.destroyed) state.dom.redrawNow();
    return;
  }
  let rest = 1 - (now - cur.start) * cur.frequency;
  if (rest <= 0) {
    if (cur.plan.nextPlan && !util.isObjectEmpty(cur.plan.nextPlan.anims)) {
      state.animation.current = {
        start: performance.now(),
        frequency: 2.2 / state.animation.duration,
        plan: cur.plan.nextPlan,
        lastMove: state.lastMove,
      };
      cur = state.animation.current;
      rest = 1 - (performance.now() - cur.start) * cur.frequency;
    } else state.animation.current = undefined;
  }

  if (state.animation.current !== undefined) {
    if (rest > 0.999) rest = 0.999;
    const ease = easing(rest);
    for (const animVec of cur.plan.anims) {
      const cfg = animVec[1];
      cfg[2] = cfg[0] * ease;
      cfg[3] = cfg[1] * ease;
    }
    state.dom.redrawNow(true); // optimisation: don't render SVG changes during animations
    requestAnimationFrame((now = performance.now()) => step(state, now));
  } else state.dom.redrawNow();
}

function animate<A>(mutation: Mutation<A>, state: State, fadeOnly = false, noCaptSequences = false): A {
  // clone state before mutating it
  const prevPieces: cg.Pieces = new Map(state.pieces);

  const result = mutation(state);
  const plan = computePlan(prevPieces, state, fadeOnly, noCaptSequences);
  if (!util.isObjectEmpty(plan.anims)) {
    const alreadyRunning = state.animation.current && state.animation.current.start;
    state.animation.current = {
      start: performance.now(),
      frequency: (plan.nextPlan && !util.isObjectEmpty(plan.nextPlan.anims) ? 2.2 : 1) / state.animation.duration,
      plan: plan,
      lastMove: state.lastMove,
    };
    if (!alreadyRunning) step(state, performance.now());
  } else {
    if (state.animation.current && !sameArray(state.animation.current.lastMove, state.lastMove)) {
      state.animation.current = undefined;
    }
    // don't animate, just render right away
    state.dom.redraw();
  }
  return result;
}

function sameArray(ar1?: Array<any>, ar2?: Array<any>) {
  if (!ar1 && !ar2) return true;
  if (!ar1 || !ar2 || ar1.length !== ar2.length) return false;
  for (let i = 0; i < ar1.length; i++) {
    if (ar1[i] !== ar2[i]) return false;
  }
  return true;
}

// https://gist.github.com/gre/1650294
function easing(t: number): number {
  return t < 0.5 ? 4 * t * t * t : (t - 1) * (2 * t - 2) * (2 * t - 2) + 1;
}
