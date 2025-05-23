import { h, VNode } from 'snabbdom';
import sanWriter, { SanToUci } from './sanWriter';
import RoundController from '../ctrl';
import { renderClock } from '../clock/clockView';
import { renderTableWatch, renderTablePlay, renderTableEnd } from '../view/table';
import { makeConfig as makeCgConfig } from '../ground';
import Draughtsground from 'draughtsground';
import renderCorresClock from '../corresClock/corresClockView';
import { renderResult } from '../view/replay';
import { plyStep } from '../round';
import { onInsert } from '../util';
import { Step, DecodedDests, Position, Redraw } from '../interfaces';
import * as game from 'game';
import {
  renderSan,
  //renderPieces,
  renderBoard,
  styleSetting,
  pieceSetting,
  prefixSetting,
  positionSetting,
  boardSetting,
  boardCommandsHandler,
  possibleMovesHandler,
  lastCapturedCommandHandler,
  selectionHandler,
  arrowKeyHandler,
  positionJumpHandler,
  pieceJumpingHandler,
  supportedVariant,
  Style,
} from 'nvui/chess';
// TODO: probably the entirety of nvui/chess
//       needs a full port to nvui/draughts. :(
import { renderSetting } from 'nvui/setting';
import { Notify } from 'nvui/notify';
import { commands } from 'nvui/command';
import { throttled } from '../sound';

const selectSound = throttled('select');
const wrapSound = throttled('wrapAround');
const borderSound = throttled('outOfBound');
const errorSound = throttled('error');

type Sans = {
  [key: string]: Uci;
};

// TODO: these are placeholder functions which will compile but are not semantically correctly
/*function userHtml(ctrl: RoundController, player: game.Player) {
  const d = ctrl.data,
    user = player.user,
    perf = user ? user.perfs[d.game.perf] : null,
    rating = player.rating ? player.rating : perf && perf.rating,
    rd = player.ratingDiff,
    ratingDiff = rd ? (rd > 0 ? '+' + rd : rd < 0 ? '−' + -rd : '') : '';
  return user
    ? h('span', [
        h(
          'a',
          {
            attrs: { href: '/@/' + user.username },
          },
          user.title
            ? `${user.title.endsWith('-64') ? user.title.slice(0, user.title.length - 3) : user.title} ${user.username}`
            : user.username
        ),
        rating ? ` ${rating}` : ``,
        ' ' + ratingDiff,
      ])
    : 'Anonymous';
}*/
//const _renderPlayer = (ctrl: RoundController, player: game.Player) => {
//return player.ai ? ctrl.trans('aiNameLevelAiLevel', 'Scan', player.ai) : userHtml(ctrl, player);
//};
const gameText = (_ctrl: RoundController) => '';
const playerHtml = (_ctrl: RoundController, _player: game.Player) => '';
const playerText = (_ctrl: RoundController, _player: game.Player | undefined) => '';

playstrategy.RoundNVUI = function (redraw: Redraw) {
  const notify = new Notify(redraw),
    moveStyle = styleSetting(),
    prefixStyle = prefixSetting(),
    pieceStyle = pieceSetting(),
    positionStyle = positionSetting(),
    boardStyle = boardSetting();

  playstrategy.pubsub.on('socket.in.message', line => {
    if (line.u === 'playstrategy') notify.set(line.t);
  });
  playstrategy.pubsub.on('round.suggestion', notify.set);

  return {
    render(ctrl: RoundController): VNode {
      const d = ctrl.data,
        step = plyStep(d, ctrl.ply),
        style = moveStyle.get(),
        variantNope = !supportedVariant(d.game.variant.key) && 'Sorry, this variant is not supported in blind mode.';
      if (!ctrl.draughtsground) {
        ctrl.setDraughtsground(
          Draughtsground(document.createElement('div'), {
            ...makeCgConfig(ctrl),
            animation: { enabled: false },
            drawable: { enabled: false },
            coordinates: undefined,
          }),
        );
        if (variantNope) setTimeout(() => notify.set(variantNope), 3000);
      }
      return h(
        'div.nvui',
        {
          hook: onInsert(_ => setTimeout(() => notify.set(gameText(ctrl)), 2000)),
        },
        [
          h('h1', gameText(ctrl)),
          h('h2', 'Game info'),
          ...['p1', 'p2'].map((playerIndex: PlayerIndex) =>
            h('p', [playerIndex + ' player: ', playerHtml(ctrl, ctrl.playerByPlayerIndex(playerIndex))]),
          ),
          h('p', `${d.game.rated ? 'Rated' : 'Casual'} ${d.game.perf}`),
          d.clock ? h('p', `Clock: ${d.clock.initial / 60} + ${d.clock.increment}`) : null,
          h('h2', 'Moves'),
          h(
            'p.moves',
            {
              attrs: {
                role: 'log',
                'aria-live': 'off',
              },
            },
            renderMoves(d.steps.slice(1), style),
          ),
          h('h2', 'Pieces'),
          // TODO: removed to get this to compile
          //h('div.pieces', renderPieces(ctrl.draughtsground.state.pieces, style)),
          h('h2', 'Game status'),
          h(
            'div.status',
            {
              attrs: {
                role: 'status',
                'aria-live': 'assertive',
                'aria-atomic': true,
              },
            },
            [ctrl.data.game.status.name === 'started' ? 'Playing' : renderResult(ctrl)],
          ),
          h('h2', 'Last move'),
          h(
            'p.lastMove',
            {
              attrs: {
                'aria-live': 'assertive',
                'aria-atomic': true,
              },
            },
            renderSan(step.san, step.lidraughtsUci, style),
          ),
          ...(ctrl.isPlaying()
            ? [
                h('h2', 'Move form'),
                h(
                  'form',
                  {
                    hook: onInsert(el => {
                      const $form = $(el as HTMLFormElement),
                        $input = $form.find('.move').val('');
                      $input[0]!.focus();
                      $form.on('submit', onSubmit(ctrl, notify.set, moveStyle.get, $input));
                    }),
                  },
                  [
                    h('label', [
                      d.player.playerIndex === d.game.player ? 'Your move' : 'Waiting',
                      h('input.move.mousetrap', {
                        attrs: {
                          name: 'move',
                          type: 'text',
                          autocomplete: 'off',
                          autofocus: true,
                          disabled: !!variantNope,
                          title: variantNope,
                        },
                      }),
                    ]),
                  ],
                ),
              ]
            : []),
          h('h2', 'Your clock'),
          h('div.botc', anyClock(ctrl, 'bottom')),
          h('h2', 'Opponent clock'),
          h('div.topc', anyClock(ctrl, 'top')),
          notify.render(),
          h('h2', 'Actions'),
          ...(ctrl.data.player.spectator
            ? renderTableWatch(ctrl)
            : game.playable(ctrl.data)
              ? renderTablePlay(ctrl)
              : renderTableEnd(ctrl)),
          h('h2', 'Board'),
          h(
            'div.board',
            {
              hook: onInsert(el => {
                const $board = $(el as HTMLElement);
                $board.on('keypress', boardCommandsHandler());
                $board.on('keypress', () => console.log(ctrl));
                // NOTE: This is the only line different from analysis board listener setup
                $board.on(
                  'keypress',
                  lastCapturedCommandHandler(
                    () => ctrl.data.steps.map(step => step.fen),
                    pieceStyle.get(),
                    prefixStyle.get(),
                  ),
                );
                const $buttons = $board.find('button');
                $buttons.on('click', selectionHandler(ctrl.data.opponent.playerIndex, selectSound));
                $buttons.on('keydown', arrowKeyHandler(ctrl.data.player.playerIndex, borderSound));
                $buttons.on(
                  'keypress',
                  possibleMovesHandler(
                    ctrl.data.player.playerIndex,
                    ctrl.draughtsground.getFen,
                    () => new Map(), //ctrl.draughtsground.state.pieces // TODO: this isn't working
                  ),
                );
                $buttons.on('keypress', positionJumpHandler());
                $buttons.on('keypress', pieceJumpingHandler(wrapSound, errorSound));
              }),
            },
            renderBoard(
              new Map(), //ctrl.draughtsground.state.pieces, // TODO: this isn't working
              ctrl.data.player.playerIndex,
              pieceStyle.get(),
              prefixStyle.get(),
              positionStyle.get(),
              boardStyle.get(),
            ),
          ),
          h(
            'div.boardstatus',
            {
              attrs: {
                'aria-live': 'polite',
                'aria-atomic': true,
              },
            },
            '',
          ),
          // h('p', takes(ctrl.data.steps.map(data => data.fen))),
          h('h2', 'Settings'),
          h('label', ['Move notation', renderSetting(moveStyle, ctrl.redraw)]),
          h('h3', 'Board Settings'),
          h('label', ['Piece style', renderSetting(pieceStyle, ctrl.redraw)]),
          h('label', ['Piece prefix style', renderSetting(prefixStyle, ctrl.redraw)]),
          h('label', ['Show position', renderSetting(positionStyle, ctrl.redraw)]),
          h('label', ['Board layout', renderSetting(boardStyle, ctrl.redraw)]),
          h('h2', 'Commands'),
          h('p', [
            'Type these commands in the move input.',
            h('br'),
            'c: Read clocks.',
            h('br'),
            'l: Read last move.',
            h('br'),
            'o: Read name and rating of the opponent.',
            h('br'),
            commands.piece.help,
            h('br'),
            commands.scan.help,
            h('br'),
            'abort: Abort game.',
            h('br'),
            'resign: Resign game.',
            h('br'),
            'draw: Offer or accept draw.',
            h('br'),
            'takeback: Offer or accept take back.',
            h('br'),
          ]),
          h('h2', 'Board Mode commands'),
          h('p', [
            'Use these commands when focused on the board itself.',
            h('br'),
            'o: announce current position.',
            h('br'),
            "c: announce last move's captured piece.",
            h('br'),
            'l: announce last move.',
            h('br'),
            't: announce clocks.',
            h('br'),
            'm: announce possible moves for the selected piece.',
            h('br'),
            'shift+m: announce possible moves for the selected pieces which capture..',
            h('br'),
            'arrow keys: move left, right, up or down.',
            h('br'),
            'kqrbnp/KQRBNP: move forward/backward to a piece.',
            h('br'),
            '1-8: move to rank 1-8.',
            h('br'),
            'Shift+1-8: move to file a-h.',
            h('br'),
          ]),
          h('h2', 'Promotion'),
          h('p', [
            'Standard PGN notation selects the piece to promote to. Example: a8=n promotes to a knight.',
            h('br'),
            'Omission results in promotion to queen',
          ]),
        ],
      );
    },
  };
};

function onSubmit(ctrl: RoundController, notify: (txt: string) => void, style: () => Style, $input: Cash) {
  return () => {
    let input = ($input.val() as string).trim();
    if (isShortCommand(input)) input = '/' + input;
    if (input[0] === '/') onCommand(ctrl, notify, input.slice(1), style());
    else {
      const d = ctrl.data,
        legalUcis = destsToUcis(ctrl.draughtsground.state.movable.dests!),
        legalSans: SanToUci = sanWriter(
          plyStep(d, ctrl.ply).fen,
          legalUcis,
          ctrl.draughtsground.state.movable.captLen,
        ) as SanToUci;
      const uci = sanToUci(input, legalSans) || input;
      if (legalUcis.includes(uci.toLowerCase()))
        ctrl.socket.send(
          'move',
          {
            from: uci.substr(0, 2),
            to: uci.substr(2, 2),
          },
          { ackable: true },
        );
      else notify(d.player.playerIndex === d.game.player ? `Invalid move: ${input}` : 'Not your turn');
    }
    $input.val('');
    return false;
  };
}

const shortCommands = ['c', 'clock', 'l', 'last', 'abort', 'resign', 'draw', 'takeback', 'p', 's', 'o', 'opponent'];

function isShortCommand(input: string): boolean {
  return shortCommands.includes(input.split(' ')[0].toLowerCase());
}

function onCommand(ctrl: RoundController, notify: (txt: string) => void, c: string, _style: Style) {
  const lowered = c.toLowerCase();
  if (lowered == 'c' || lowered == 'clock') notify($('.nvui .botc').text() + ', ' + $('.nvui .topc').text());
  else if (lowered == 'l' || lowered == 'last') notify($('.lastMove').text());
  else if (lowered == 'abort') $('.nvui button.abort').trigger('click');
  else if (lowered == 'resign') $('.nvui button.resign-confirm').trigger('click');
  else if (lowered == 'draw') $('.nvui button.draw-yes').trigger('click');
  else if (lowered == 'takeback') $('.nvui button.takeback-yes').trigger('click');
  else if (lowered == 'o' || lowered == 'opponent') notify(playerText(ctrl, ctrl.data.opponent));
  else {
    //const pieces = ctrl.draughtsground.state.pieces,
    //boardSize = ctrl.draughtsground.state.boardSize;
    // TODO: This was just commented out for compiling. :(
    //notify(
    //commands.piece.apply(c, pieces, style) ||
    //commands.scan.apply(c, pieces, boardSize, ctrl.data.player.playerIndex !== 'p1') ||
    //`Invalid command: ${c}`
    //);
  }
}

function anyClock(ctrl: RoundController, position: Position) {
  const d = ctrl.data,
    player = ctrl.playerAt(position);
  return (
    (ctrl.clock && renderClock(ctrl, player, position)) ||
    (d.correspondence &&
      renderCorresClock(ctrl.corresClock!, ctrl.trans, player.playerIndex, position, d.game.player)) ||
    undefined
  );
}

function destsToUcis(dests: DecodedDests) {
  const ucis: string[] = [];
  for (const [orig, d] of dests) {
    if (d)
      d.forEach(function (dest) {
        ucis.push(orig + dest);
      });
  }
  return ucis;
}

function sanToUci(san: string, sans: Sans): string | undefined {
  if (san in sans) return sans[san];
  if (san.length === 4 && Object.keys(sans).find(key => sans[key] === san)) return san;
  let lowered = san.toLowerCase().replace('x0', 'x').replace('-0', '-');
  if (lowered.slice(0, 1) === '0') lowered = lowered.slice(1);
  if (lowered in sans) return sans[lowered];
  return undefined;
}

function renderMoves(steps: Step[], style: Style) {
  const res: Array<string | VNode> = [];
  steps.forEach(s => {
    if (s.ply & 1) res.push(Math.ceil(s.ply / 2) + ' ');
    res.push(renderSan(s.san, s.lidraughtsUci, style) + ', ');
    if (s.ply % 2 === 0) res.push(h('br'));
  });
  return res;
}
