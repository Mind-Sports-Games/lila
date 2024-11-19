import * as keyboard from '../keyboard';
import * as promotion from '../promotion';
import * as util from '../util';
import crazyView from '../crazy/crazyView';
import RoundController from '../ctrl';
import { h, VNode } from 'snabbdom';
import { plyStep } from '../round';
import { finished } from 'game/status';
import { Position, MaterialDiff, MaterialDiffSide, CheckCount } from '../interfaces';
import { read as fenRead, readPocket as pocketRead } from 'chessground/fen';
import * as cg from 'chessground/types';
import { render as keyboardMove } from '../keyboardMove';
import { render as renderGround } from '../ground';
import { renderTable } from './table';
import { Player } from 'game';

function renderMaterial(
  material: MaterialDiffSide,
  score: number,
  position: Position,
  noMaterial: boolean,
  checks?: number,
) {
  if (noMaterial) return;
  const children: VNode[] = [];
  let role: string, i: number;
  for (role in material) {
    if (material[role] > 0) {
      const content: VNode[] = [];
      for (i = 0; i < material[role]; i++) content.push(h('mpiece.' + role));
      children.push(h('div', content));
    }
  }
  if (checks) for (i = 0; i < checks; i++) children.push(h('div', h('mpiece.k-piece')));
  if (score > 0) children.push(h('score', '+' + score));
  return h('div.material.material-' + position, children);
}

function renderPlayerScore(
  score: number,
  position: Position,
  playerIndex: string,
  variantKey: VariantKey,
  captures: boolean,
): VNode {
  const defaultMancalaRole = 's';
  const children: VNode[] = [];
  if (variantKey === 'togyzkumalak' || variantKey === 'bestemshe') {
    let part1Score = 0;
    let part2Score = 0;
    let part2Offset = false;
    if (score <= 10) {
      part1Score = score;
      part2Score = 0;
    } else if (score <= 20) {
      part1Score = 10;
      part2Score = score - 10;
    } else {
      part1Score = Math.min((score % 20) + 10, 20);
      part2Score = Math.max(score % 20, 10);
      if (part2Score === 10) part2Offset = true;
    }

    const pieceClassPart1 = `piece.${defaultMancalaRole}${part1Score.toString()}-piece.part1.`;
    const pieceClassPart2 = `piece.${defaultMancalaRole}${part2Score.toString()}${part2Offset ? 'o' : ''}-piece.part2.`;

    children.push(h(pieceClassPart1 + playerIndex));
    if (score > 10) {
      children.push(h(pieceClassPart2 + playerIndex));
    }
    return h('div.game-score.game-score-' + position, { attrs: { 'data-score': score } }, children);
  } else if (variantKey === 'go9x9' || variantKey === 'go13x13' || variantKey === 'go19x19') {
    children.push(h('piece.p-piece.' + playerIndex, { attrs: { 'data-score': score } }));
    const capturesClass = captures ? '.captures' : '';
    return h(
      'div.game-score.game-score-' + position + '.' + playerIndex + capturesClass,
      {
        attrs: {
          title: captures ? 'Captures' : 'Score',
        },
      },
      children,
    );
  } else if (variantKey === 'backgammon' || variantKey === 'hyper' || variantKey === 'nackgammon') {
    for (let i = 0; i < score; i++) {
      children.push(h('piece.side-piece.' + playerIndex + (i === 0 ? ' first' : '')));
    }
    return h('div.game-score.game-score-' + position, { attrs: { 'data-score': score } }, children);
  } else if (variantKey === 'oware') {
    children.push(
      h(`piece.${defaultMancalaRole}${score.toString()}-piece.` + playerIndex + `.captures`, {
        attrs: { 'data-score': score },
      }),
    );
    return h('div.game-score.game-score-' + position, children);
  } else if (variantKey === 'abalone') {
    children.push(h('piece.s-piece.' + playerIndex, { attrs: { 'data-score': score } }));
    return h('div.game-score.game-score-' + position, children);
  } else {
    children.push(h('piece.p-piece.' + playerIndex, { attrs: { 'data-score': score } }));
    return h('div.game-score.game-score-' + position, children);
  }
}

function renderPlayerScoreNames(player: Player, opponent: Player): VNode | undefined {
  const children: VNode[] = [];
  const playerNames = {
    p1: player.user ? player.user.username : player.playerName,
    p2: opponent.user ? opponent.user.username : opponent.playerName,
  };
  children.push(h('div.game-score-name.p1.text', playerNames.p1));
  children.push(h('div.game-score-name.vs.text', 'vs'));
  children.push(h('div.game-score-name.p2.text', playerNames.p2));
  return h('div.game-score-names', children);
}

function wheel(ctrl: RoundController, e: WheelEvent): void {
  if (!ctrl.isPlaying()) {
    e.preventDefault();
    if (e.deltaY > 0) keyboard.next(ctrl);
    else if (e.deltaY < 0) keyboard.prev(ctrl);
    ctrl.redraw();
  }
}

const emptyMaterialDiff: MaterialDiff = {
  p1: {},
  p2: {},
};

export function main(ctrl: RoundController): VNode {
  const d = ctrl.data,
    cgState = ctrl.chessground && ctrl.chessground.state,
    topPlayerIndex = d[ctrl.flip ? 'player' : 'opponent'].playerIndex,
    bottomPlayerIndex = d[ctrl.flip ? 'opponent' : 'player'].playerIndex,
    boardSize = d.game.variant.boardSize,
    variantKey = d.game.variant.key;
  let topScore = 0,
    bottomScore = 0,
    captures = false;
  if (d.hasGameScore) {
    switch (variantKey) {
      case 'flipello10':
      case 'flipello': {
        const pieces = cgState ? cgState.pieces : fenRead(plyStep(ctrl.data, ctrl.ply).fen, boardSize, variantKey);
        const p1Score = util.getPlayerScore(variantKey, pieces, 'p1');
        const p2Score = util.getPlayerScore(variantKey, pieces, 'p2');
        topScore = topPlayerIndex === 'p1' ? p1Score : p2Score;
        bottomScore = topPlayerIndex === 'p2' ? p1Score : p2Score;
        break;
      }
      case 'abalone': {
        const fen = plyStep(ctrl.data, ctrl.ply).fen;
        const p1Score = util.getAbaloneScore(fen, 'p1');
        const p2Score = util.getAbaloneScore(fen, 'p2');
        topScore = topPlayerIndex === 'p1' ? p1Score : p2Score;
        bottomScore = topPlayerIndex === 'p2' ? p1Score : p2Score;
        break;
      }
      case 'oware':
      case 'togyzkumalak':
      case 'bestemshe': {
        //oware stores the score in the board fen so we can do this instead
        const fen = plyStep(ctrl.data, ctrl.ply).fen;
        const p1Score = util.getMancalaScore(fen, 'p1');
        const p2Score = util.getMancalaScore(fen, 'p2');
        topScore = topPlayerIndex === 'p1' ? p1Score : p2Score;
        bottomScore = topPlayerIndex === 'p2' ? p1Score : p2Score;
        break;
      }
      case 'go9x9':
      case 'go13x13':
      case 'go19x19': {
        const fen = plyStep(ctrl.data, ctrl.ply).fen;
        if (
          ctrl.data.deadStoneOfferState &&
          ctrl.data.deadStoneOfferState !== 'RejectedOffer' &&
          ctrl.data.currentSelectedSquares &&
          ctrl.data.currentSelectedSquares.length > 0
        ) {
          const p1Score = ctrl.data.calculatedCGGoScores
            ? ctrl.data.calculatedCGGoScores.p1
            : util.getGoScore(fen, 'p1');
          const p2Score = ctrl.data.calculatedCGGoScores
            ? ctrl.data.calculatedCGGoScores.p2 + util.getGoKomi(fen)
            : util.getGoScore(fen, 'p2');
          topScore = topPlayerIndex === 'p1' ? p1Score : p2Score;
          bottomScore = topPlayerIndex === 'p2' ? p1Score : p2Score;
        } else if (
          (finished(ctrl.data) && !ctrl.replaying()) ||
          (ctrl.data.deadStoneOfferState && ctrl.data.deadStoneOfferState !== 'RejectedOffer')
        ) {
          const p1Score = util.getGoScore(fen, 'p1');
          const p2Score = util.getGoScore(fen, 'p2');
          topScore = topPlayerIndex === 'p1' ? p1Score : p2Score;
          bottomScore = topPlayerIndex === 'p2' ? p1Score : p2Score;
        } else {
          const p1Score = util.getGoCaptures(fen, 'p1');
          const p2Score = util.getGoCaptures(fen, 'p2');
          topScore = topPlayerIndex === 'p1' ? p1Score : p2Score;
          bottomScore = topPlayerIndex === 'p2' ? p1Score : p2Score;
          captures = true;
        }
        break;
      }
      case 'nackgammon':
      case 'hyper':
      case 'backgammon': {
        const startingNumberOfPieces = variantKey === 'hyper' ? 3 : 15;
        const fen = plyStep(ctrl.data, ctrl.ply).fen;
        const pieces = cgState ? cgState.pieces : fenRead(fen, boardSize, variantKey);
        const pocketPieces = pocketRead(fen, variantKey);

        const p1PiecesOffBoard: number =
          fen.split(' ').length > 5
            ? util.getBackgammonScoreFromFen(fen, 'p1')
            : startingNumberOfPieces - util.getBackgammonScoreFromPieces(pieces, pocketPieces, 'p1');
        const p2PiecesOffBoard: number =
          fen.split(' ').length > 5
            ? util.getBackgammonScoreFromFen(fen, 'p2')
            : startingNumberOfPieces - util.getBackgammonScoreFromPieces(pieces, pocketPieces, 'p2');

        const p1Score = p1PiecesOffBoard;
        const p2Score = p2PiecesOffBoard;
        topScore = topPlayerIndex === 'p1' ? p1Score : p2Score;
        bottomScore = topPlayerIndex === 'p2' ? p1Score : p2Score;
        break;
      }
      default: {
        break;
      }
    }
  }
  let material: MaterialDiff,
    score = 0;
  if (d.pref.showCaptured) {
    const pieces = cgState
      ? cgState.pieces
      : fenRead(plyStep(ctrl.data, ctrl.ply).fen, boardSize, variantKey as cg.Variant);
    material = util.getMaterialDiff(pieces);
    score = util.getScore(variantKey, pieces) * (bottomPlayerIndex === 'p1' ? 1 : -1);
  } else material = emptyMaterialDiff;

  const checks: CheckCount =
    d.player.checks || d.opponent.checks ? util.countChecks(ctrl.data.steps, ctrl.ply) : util.noChecks;

  // fix coordinates for non-chess games to display them outside due to not working well displaying on board
  if (
    [
      'xiangqi',
      'shogi',
      'minixiangqi',
      'minishogi',
      'flipello',
      'flipello10',
      'oware',
      'go9x9',
      'go13x13',
      'go19x19',
      'abalone',
    ].includes(variantKey)
  ) {
    if (!$('body').hasClass('coords-no')) {
      $('body').removeClass('coords-in').addClass('coords-out');
    }
  }
  //Togyzkumalak and backgammon board always has coodinates on the inside
  if (['togyzkumalak', 'bestemshe', 'backgammon', 'hyper', 'nackgammon'].includes(variantKey)) {
    if (!$('body').hasClass('coords-no')) {
      $('body').removeClass('coords-out').addClass('coords-in');
    }
  }

  //Add piece-letter class for games which dont want Noto Chess (font-famliy)
  const notationBasic = [
    'xiangqi',
    'shogi',
    'minixiangqi',
    'minishogi',
    'breakthroughtroyka',
    'minibreakthroughtroyka',
    'oware',
    'togyzkumalak',
    'bestemshe',
    'go9x9',
    'go13x13',
    'go19x19',
    'backgammon',
    'hyper',
    'nackgammon',
    'abalone',
  ].includes(variantKey)
    ? '.piece-letter'
    : '';

  return ctrl.nvui
    ? ctrl.nvui.render(ctrl)
    : h(
        `div.round__app.variant-${variantKey}${notationBasic}.${d.game.gameFamily}`,
        {
          class: {
            'move-confirm': !!(ctrl.moveToSubmit || ctrl.dropToSubmit),
            'turn-indicator-off': !ctrl.data.pref.playerTurnIndicator,
          },
        },
        [
          h(
            'div.round__app__board.main-board' + (ctrl.data.pref.blindfold ? '.blindfold' : ''),
            {
              hook:
                'ontouchstart' in window
                  ? undefined
                  : util.bind('wheel', (e: WheelEvent) => wheel(ctrl, e), undefined, false),
            },
            [renderGround(ctrl), promotion.view(ctrl)],
          ),
          ctrl.data.hasGameScore ? renderPlayerScore(topScore, 'top', topPlayerIndex, variantKey, captures) : null,
          ctrl.data.hasGameScore ? renderPlayerScoreNames(ctrl.data.player, ctrl.data.opponent) : null,
          crazyView(ctrl, topPlayerIndex, 'top') ||
            renderMaterial(material[topPlayerIndex], -score, 'top', d.hasGameScore, checks[topPlayerIndex]),
          ...renderTable(ctrl),
          crazyView(ctrl, bottomPlayerIndex, 'bottom') ||
            renderMaterial(material[bottomPlayerIndex], score, 'bottom', d.hasGameScore, checks[bottomPlayerIndex]),
          ctrl.data.hasGameScore
            ? renderPlayerScore(bottomScore, 'bottom', bottomPlayerIndex, variantKey, captures)
            : null,
          ctrl.keyboardMove ? keyboardMove(ctrl.keyboardMove) : null,
        ],
      );
}
