import { winningChances } from 'ceval';
import * as cg from 'chessground/types';
import { opposite } from 'chessground/util';
import { DrawModifiers, DrawShape } from 'chessground/draw';
import AnalyseCtrl from './ctrl';
import { variantClassFromKey } from 'stratops/variants/util';

function pieceDrop(key: cg.Key, role: cg.Role, playerIndex: PlayerIndex): DrawShape {
  return {
    orig: key,
    piece: {
      playerIndex,
      role,
      scale: 0.8,
    },
    brush: 'green',
  };
}

export function makeShapesFromUci(
  playerIndex: PlayerIndex,
  uci: Uci,
  brush: string,
  variant: VariantKey = 'standard',
  modifiers?: DrawModifiers,
): DrawShape[] {
  const move = variantClassFromKey(variant).parseLexicalUci(uci);
  if (move === undefined) return [];

  const to = move.to;
  const dropRole = move.dropRole;
  if (dropRole !== undefined) return [{ orig: to, brush }, pieceDrop(to, dropRole, playerIndex)];

  const shapes: DrawShape[] = [
    {
      orig: move.from,
      dest: to,
      brush,
      modifiers,
    },
  ];
  if (move.promotion) shapes.push(pieceDrop(to, move.promotion, playerIndex));
  return shapes;
}

export function compute(ctrl: AnalyseCtrl): DrawShape[] {
  const variant = ctrl.data.game.variant.key;
  const playerIndex = ctrl.node.fen.includes(' w ') ? 'p1' : 'p2';
  const rPlayerIndex = opposite(playerIndex);
  if (ctrl.practice) {
    const hovering = ctrl.practice.hovering();
    if (hovering) return makeShapesFromUci(playerIndex, hovering.uci, 'green', variant);
    const hint = ctrl.practice.hinting();
    if (hint) {
      if (hint.mode === 'move') return makeShapesFromUci(playerIndex, hint.uci, 'paleBlue', variant);
      else
        return [
          {
            orig: (hint.uci[1] === '@' ? hint.uci.slice(2, 4) : hint.uci.slice(0, 2)) as Key,
            brush: 'paleBlue',
          },
        ];
    }
    return [];
  }
  const instance = ctrl.getCeval();
  const hovering = ctrl.explorer.hovering() || instance.hovering();
  const { eval: nEval = {} as Partial<Tree.ServerEval>, fen: nFen, ceval: nCeval, threat: nThreat } = ctrl.node;

  let shapes: DrawShape[] = [],
    badNode;
  if (ctrl.retro && (badNode = ctrl.retro.showBadNode())) {
    return makeShapesFromUci(playerIndex, badNode.uci!, 'paleRed', variant, {
      lineWidth: 8,
    });
  }
  if (hovering && hovering.fen === nFen)
    shapes = shapes.concat(makeShapesFromUci(playerIndex, hovering.uci, 'paleBlue', variant));
  if (ctrl.showAutoShapes() && ctrl.showComputer()) {
    if (nEval.best) shapes = shapes.concat(makeShapesFromUci(rPlayerIndex, nEval.best, 'paleGreen', variant));
    if (!hovering && parseInt(instance.multiPv())) {
      let nextBest = ctrl.nextNodeBest();
      if (!nextBest && instance.enabled() && nCeval) nextBest = nCeval.pvs[0].moves[0];
      if (nextBest) shapes = shapes.concat(makeShapesFromUci(playerIndex, nextBest, 'paleBlue', variant));
      if (instance.enabled() && nCeval && nCeval.pvs[1] && !(ctrl.threatMode() && nThreat && nThreat.pvs.length > 2)) {
        nCeval.pvs.forEach(function (pv) {
          if (pv.moves[0] === nextBest) return;
          const shift = winningChances.povDiff(playerIndex, nCeval.pvs[0], pv);
          if (shift >= 0 && shift < 0.2) {
            shapes = shapes.concat(
              makeShapesFromUci(playerIndex, pv.moves[0], 'paleGrey', variant, {
                lineWidth: Math.round(12 - shift * 50), // 12 to 2
              }),
            );
          }
        });
      }
    }
  }
  if (instance.enabled() && ctrl.threatMode() && nThreat) {
    const [pv0, ...pv1s] = nThreat.pvs;

    shapes = shapes.concat(makeShapesFromUci(rPlayerIndex, pv0.moves[0], pv1s.length > 0 ? 'paleRed' : 'red'));

    pv1s.forEach(function (pv) {
      const shift = winningChances.povDiff(rPlayerIndex, pv, pv0);
      if (shift >= 0 && shift < 0.2) {
        shapes = shapes.concat(
          makeShapesFromUci(rPlayerIndex, pv.moves[0], 'paleRed', variant, {
            lineWidth: Math.round(11 - shift * 45), // 11 to 2
          }),
        );
      }
    });
  }
  if (ctrl.showMoveAnnotation() && ctrl.showComputer()) {
    const { uci, glyphs, san } = ctrl.node;
    if (uci && san && glyphs && glyphs.length > 0) {
      const glyph = glyphs[0];
      const svg = (glyphToSvg as Dictionary<string>)[glyph.symbol];
      if (svg) {
        const move = variantClassFromKey(variant).parseLexicalUci(uci)!;
        const destSquare = san.startsWith('O-O') // castle, short or long
          ? move.to[1] === '1' // p1 castle
            ? san === 'O-O-O'
              ? 'c1'
              : 'g1'
            : san === 'O-O-O'
              ? 'c8'
              : 'g8'
          : move.to;
        shapes = shapes.concat({
          orig: destSquare,
          customSvg: svg,
          brush: 'paleRed',
        });
      }
    }
  }
  return shapes;
}

// We can render glyphs as text, but people are used to these SVGs as the "Big 5" glyphs
// and right now they look better
const prependDropShadow = (svgBase: string) =>
  `<defs><filter id="shadow"><feDropShadow dx="4" dy="7" stdDeviation="5" flood-opacity="0.5" /></filter></defs>
<g transform="translate(71 -12) scale(0.4)">${svgBase}</g>`;
// NOTE:
//   Base svg was authored with Inkscape.
///  Inkscape's output includes unnecessary attributes so they are cleaned up manually.
//   On Inkscape, by using "Object to Path", text is converted to path, which enables consistent layout on browser.
//   Wrap it by `transform="translate(...) scale(...)"` so that it sits at the right top corner.
//   Small tweak (e.g. changing color, scaling size, etc...) can be done by directly modifying svg below.
const glyphToSvg: Dictionary<string> = {
  // Inaccuracy
  '?!': prependDropShadow(`
  <circle style="fill:#56b4e9;filter:url(#shadow)" cx="50" cy="50" r="50" />
  <path fill="#fff" d="M37.734 21.947c-3.714 0-7.128.464-10.242 1.393-3.113.928-6.009 2.13-8.685 3.605l4.343 8.766c2.35-1.202 4.644-2.157 6.883-2.867a22.366 22.366 0 0 1 6.799-1.065c2.294 0 4.07.464 5.326 1.393 1.311.874 1.967 2.186 1.967 3.933 0 1.748-.546 3.277-1.639 4.588-1.038 1.257-2.786 2.758-5.244 4.506-2.786 2.021-4.751 3.961-5.898 5.819-1.147 1.857-1.721 4.15-1.721 6.88v2.952h10.568v-2.377c0-1.147.137-2.103.41-2.868.328-.764.93-1.557 1.803-2.376.874-.82 2.104-1.803 3.688-2.95 2.13-1.584 3.906-3.058 5.326-4.424 1.42-1.42 2.485-2.95 3.195-4.59.71-1.638 1.065-3.576 1.065-5.816 0-4.206-1.584-7.675-4.752-10.406-3.114-2.731-7.51-4.096-13.192-4.096zm24.745.819l2.048 39.084h9.75l2.047-39.084zM35.357 68.73c-1.966 0-3.632.52-4.998 1.557-1.365.983-2.047 2.732-2.047 5.244 0 2.404.682 4.152 2.047 5.244 1.366 1.038 3.032 1.557 4.998 1.557 1.912 0 3.55-.519 4.916-1.557 1.366-1.092 2.05-2.84 2.05-5.244 0-2.512-.684-4.26-2.05-5.244-1.365-1.038-3.004-1.557-4.916-1.557zm34.004 0c-1.966 0-3.632.52-4.998 1.557-1.365.983-2.049 2.732-2.049 5.244 0 2.404.684 4.152 2.05 5.244 1.365 1.038 3.03 1.557 4.997 1.557 1.912 0 3.55-.519 4.916-1.557 1.366-1.092 2.047-2.84 2.047-5.244 0-2.512-.681-4.26-2.047-5.244-1.365-1.038-3.004-1.557-4.916-1.557z"/>
`),

  // Mistake
  '?': prependDropShadow(`
  <circle style="fill:#e69f00;filter:url(#shadow)" cx="50" cy="50" r="50" />
  <path fill="#fff" d="M40.436 60.851q0-4.66 1.957-7.83 1.958-3.17 6.712-6.619 4.195-2.983 5.967-5.127 1.864-2.237 1.864-5.22 0-2.983-2.237-4.475-2.144-1.585-6.06-1.585-3.915 0-7.737 1.212t-7.83 3.263l-4.941-9.975q4.568-2.517 9.881-4.101 5.314-1.585 11.653-1.585 9.695 0 15.008 4.661 5.407 4.661 5.407 11.839 0 3.822-1.212 6.619-1.212 2.796-3.635 5.22-2.424 2.33-6.06 5.034-2.703 1.958-4.195 3.356-1.491 1.398-2.05 2.703-.467 1.305-.467 3.263v2.703H40.436zm-1.492 18.924q0-4.288 2.33-5.966 2.331-1.771 5.687-1.771 3.263 0 5.594 1.771 2.33 1.678 2.33 5.966 0 4.102-2.33 5.966-2.331 1.772-5.594 1.772-3.356 0-5.686-1.772-2.33-1.864-2.33-5.966z"/>
`),

  // Blunder
  '??': prependDropShadow(`
  <circle style="fill:#df5353;filter:url(#shadow)" cx="50" cy="50" r="50" />
  <path fill="#fff" d="M31.8 22.22c-3.675 0-7.052.46-10.132 1.38-3.08.918-5.945 2.106-8.593 3.565l4.298 8.674c2.323-1.189 4.592-2.136 6.808-2.838a22.138 22.138 0 0 1 6.728-1.053c2.27 0 4.025.46 5.268 1.378 1.297.865 1.946 2.16 1.946 3.89s-.541 3.242-1.622 4.539c-1.027 1.243-2.756 2.73-5.188 4.458-2.756 2-4.7 3.918-5.836 5.755-1.134 1.837-1.702 4.107-1.702 6.808v2.92h10.457v-2.35c0-1.135.135-2.082.406-2.839.324-.756.918-1.54 1.783-2.35.864-.81 2.079-1.784 3.646-2.918 2.107-1.568 3.863-3.026 5.268-4.376 1.405-1.405 2.46-2.92 3.162-4.541.703-1.621 1.054-3.54 1.054-5.755 0-4.161-1.568-7.592-4.702-10.294-3.08-2.702-7.43-4.052-13.05-4.052zm38.664 0c-3.675 0-7.053.46-10.133 1.38-3.08.918-5.944 2.106-8.591 3.565l4.295 8.674c2.324-1.189 4.593-2.136 6.808-2.838a22.138 22.138 0 0 1 6.728-1.053c2.27 0 4.026.46 5.269 1.378 1.297.865 1.946 2.16 1.946 3.89s-.54 3.242-1.62 4.539c-1.027 1.243-2.757 2.73-5.189 4.458-2.756 2-4.7 3.918-5.835 5.755-1.135 1.837-1.703 4.107-1.703 6.808v2.92h10.457v-2.35c0-1.135.134-2.082.404-2.839.324-.756.918-1.54 1.783-2.35.865-.81 2.081-1.784 3.648-2.918 2.108-1.568 3.864-3.026 5.269-4.376 1.405-1.405 2.46-2.92 3.162-4.541.702-1.621 1.053-3.54 1.053-5.755 0-4.161-1.567-7.592-4.702-10.294-3.08-2.702-7.43-4.052-13.05-4.052zM29.449 68.504c-1.945 0-3.593.513-4.944 1.54-1.351.973-2.027 2.703-2.027 5.188 0 2.378.676 4.108 2.027 5.188 1.35 1.027 3 1.54 4.944 1.54 1.892 0 3.512-.513 4.863-1.54 1.35-1.08 2.026-2.81 2.026-5.188 0-2.485-.675-4.215-2.026-5.188-1.351-1.027-2.971-1.54-4.863-1.54zm38.663 0c-1.945 0-3.592.513-4.943 1.54-1.35.973-2.026 2.703-2.026 5.188 0 2.378.675 4.108 2.026 5.188 1.351 1.027 2.998 1.54 4.943 1.54 1.891 0 3.513-.513 4.864-1.54 1.351-1.08 2.027-2.81 2.027-5.188 0-2.485-.676-4.215-2.027-5.188-1.35-1.027-2.973-1.54-4.864-1.54z"/>
`),

  // Interesting move
  '!?': prependDropShadow(`
  <circle style="fill:#ea45d8;filter:url(#shadow)" cx="50" cy="50" r="50"/>
    <path fill="#fff" d="M60.823 58.9q0-4.098 1.72-6.883 1.721-2.786 5.9-5.818 3.687-2.622 5.243-4.506 1.64-1.966 1.64-4.588t-1.967-3.933q-1.885-1.393-5.326-1.393t-6.8 1.065q-3.36 1.065-6.883 2.868l-4.343-8.767q4.015-2.212 8.685-3.605 4.67-1.393 10.242-1.393 8.521 0 13.192 4.097 4.752 4.096 4.752 10.405 0 3.36-1.065 5.818-1.066 2.458-3.196 4.588-2.13 2.048-5.326 4.424-2.376 1.72-3.687 2.95-1.31 1.229-1.802 2.376-.41 1.147-.41 2.868v2.376h-10.57zm-1.311 16.632q0-3.77 2.048-5.244 2.049-1.557 4.998-1.557 2.868 0 4.916 1.557 2.049 1.475 2.049 5.244 0 3.605-2.049 5.244-2.048 1.556-4.916 1.556-2.95 0-4.998-1.556-2.048-1.64-2.048-5.244zM36.967 61.849h-9.75l-2.049-39.083h13.847zM25.004 75.532q0-3.77 2.049-5.244 2.048-1.557 4.998-1.557 2.867 0 4.916 1.557 2.048 1.475 2.048 5.244 0 3.605-2.048 5.244-2.049 1.556-4.916 1.556-2.95 0-4.998-1.556-2.049-1.64-2.049-5.244z" vector-effect="non-scaling-stroke"/>
`),

  // Good move
  '!': prependDropShadow(`
  <circle style="fill:#22ac38;filter:url(#shadow)" cx="50" cy="50" r="50" />
  <path fill="#fff" d="M54.967 62.349h-9.75l-2.049-39.083h13.847zM43.004 76.032q0-3.77 2.049-5.244 2.048-1.557 4.998-1.557 2.867 0 4.916 1.557 2.048 1.475 2.048 5.244 0 3.605-2.048 5.244-2.049 1.556-4.916 1.556-2.95 0-4.998-1.556-2.049-1.64-2.049-5.244z" vector-effect="non-scaling-stroke"/>
`),

  // Brilliant move
  '!!': prependDropShadow(`
  <circle style="fill:#168226;filter:url(#shadow)" cx="50" cy="50" r="50" />
  <path fill="#fff" d="M71.967 62.349h-9.75l-2.049-39.083h13.847zM60.004 76.032q0-3.77 2.049-5.244 2.048-1.557 4.998-1.557 2.867 0 4.916 1.557 2.048 1.475 2.048 5.244 0 3.605-2.048 5.244-2.049 1.556-4.916 1.556-2.95 0-4.998-1.556-2.049-1.64-2.049-5.244zM37.967 62.349h-9.75l-2.049-39.083h13.847zM26.004 76.032q0-3.77 2.049-5.244 2.048-1.557 4.998-1.557 2.867 0 4.916 1.557 2.048 1.475 2.048 5.244 0 3.605-2.048 5.244-2.049 1.556-4.916 1.556-2.95 0-4.998-1.556-2.049-1.64-2.049-5.244z" vector-effect="non-scaling-stroke"/>
`),

  // Correct move in a puzzle
  '✓': prependDropShadow(`
  <circle style="fill:#22ac38;filter:url(#shadow)" cx="50" cy="50" r="50" />
  <path fill="#fff" d="M87 32.8q0 2-1.4 3.2L51 70.6 44.6 77q-1.7 1.3-3.4 1.3-1.8 0-3.1-1.3L14.3 53.3Q13 52 13 50q0-2 1.3-3.2l6.4-6.5Q22.4 39 24 39q1.9 0 3.2 1.3l14 14L72.7 23q1.3-1.3 3.2-1.3 1.6 0 3.3 1.3l6.4 6.5q1.3 1.4 1.3 3.4z"/>
`),

  // Incorrect move in a puzzle
  '✗': prependDropShadow(`
  <circle style="fill:#df5353;filter:url(#shadow)" cx="50" cy="50" r="50" />
  <path fill="#fff" d="M79.4 68q0 1.8-1.4 3.2l-6.7 6.7q-1.4 1.4-3.5 1.4-1.9 0-3.3-1.4L50 63.4 35.5 78q-1.4 1.4-3.3 1.4-2 0-3.5-1.4L22 71.2q-1.4-1.4-1.4-3.3 0-1.7 1.4-3.5L36.5 50 22 35.4Q20.6 34 20.6 32q0-1.7 1.4-3.5l6.7-6.5q1.2-1.4 3.5-1.4 2 0 3.3 1.4L50 36.6 64.5 22q1.2-1.4 3.3-1.4 2.3 0 3.5 1.4l6.7 6.5q1.4 1.8 1.4 3.5 0 2-1.4 3.3L63.5 49.9 78 64.4q1.4 1.8 1.4 3.5z"/>
`),
};
