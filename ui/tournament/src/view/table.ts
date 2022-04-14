import { h, VNode } from 'snabbdom';
import { opposite } from 'chessground/util';
import { player as renderPlayer, bind, onInsert } from './util';
import { Duel, DuelPlayer, DuelTeams, TeamBattle, FeaturedGame } from '../interfaces';
import { teamName } from './battle';
import TournamentController from '../ctrl';

const renderGameClasses = (game: FeaturedGame): string =>
  game.gameLogic === 'draughts' && !!game.boardSize
    ? `.tour__featured.mini-game.mini-game-${game.id}.mini-game--init.is2d.${game.gameLogic}.is${game.boardSize.key}.${game.variantKey}.variant-${game.variantKey}`
    : `.tour__featured.mini-game.mini-game-${game.id}.mini-game--init.is2d.${game.gameFamily}.${game.variantKey}.variant-${game.variantKey}`;

const renderGameState = (game: FeaturedGame): string =>
  game.gameLogic === 'draughts' && !!game.boardSize
    ? `${game.fen}|${game.boardSize.size[0]}x${game.boardSize.size[1]}|${game.orientation}|${game.lastMove}`
    : `${game.fen},${game.orientation},${game.lastMove}`;

function featuredPlayer(game: FeaturedGame, playerIndex: PlayerIndex) {
  const player = game[playerIndex];
  const clock = game.c || game.clock; // temporary BC, remove me
  return h('span.mini-game__player', [
    h('span.mini-game__user', [
      h('strong', '#' + player.rank),
      renderPlayer(player, true, true, false),
      player.berserk
        ? h('i', {
            attrs: {
              'data-icon': '`',
              title: 'Berserk',
            },
          })
        : null,
    ]),
    clock
      ? h(`span.mini-game__clock.mini-game__clock--${playerIndex}`, {
          attrs: {
            'data-time': clock[playerIndex],
            'data-managed': 1,
          },
        } )
      : h('span.mini-game__result', game.winner ? (game.winner == playerIndex ? 1 : 0) : '½'),
    h('span', calculateScore(game, playerIndex))
  ]);
}

function calculateScore(game: FeaturedGame, playerIndex: PlayerIndex): String {  
  console.log("game ", game);
  var score = ""
  switch(game.variantKey) {
      case "flipello": {
        const boardFen = game.fen.split(" ")[0].split("[")[0];
        if (playerIndex == 'p1'){
          score = "(" + (boardFen.split('p').length - 1).toString() + ")"
        }else{
          score = "(" + (boardFen.split('P').length - 1).toString() + ")"
        }
        break;
      }
      case "threeCheck":
      case "fiveCheck": {
        var numChecks: number = 0
        if (playerIndex == 'p1'){
          numChecks = game.p1Checks ? game.p1Checks : 0
        }else{
          numChecks = game.p2Checks ? game.p2Checks : 0
        }      
        score = "(" + numChecks.toString() + ")"
        break;
      }
      default: {
       score = ""
       break;
      }
  }
  return score;
}

function featured(game: FeaturedGame): VNode {
  return h(
    `div${renderGameClasses(game)}`,
    {
      attrs: {
        'data-state': renderGameState(game),
        'data-live': game.id,
      },
      hook: onInsert(playstrategy.powertip.manualUserIn),
    },
    [
      featuredPlayer(game, opposite(game.orientation)),
      h('a.cg-wrap', {
        attrs: {
          href: `/${game.id}/${game.orientation}`,
        },
      }),
      featuredPlayer(game, game.orientation),
    ]
  );
}

function duelPlayerMeta(p: DuelPlayer) {
  return [h('em.rank', '#' + p.k), p.t ? h('em.utitle', p.t) : null, h('em.rating', '' + p.r)];
}

function renderDuel(battle?: TeamBattle, duelTeams?: DuelTeams) {
  return (d: Duel) =>
    h(
      'a.glpt',
      {
        key: d.id,
        attrs: { href: '/' + d.id },
      },
      [
        battle && duelTeams
          ? h(
              'line.t',
              [0, 1].map(i => teamName(battle, duelTeams[d.p[i].n.toLowerCase()]))
            )
          : undefined,
        h('line.a', [h('strong', d.p[0].n), h('span', duelPlayerMeta(d.p[1]).reverse())]),
        h('line.b', [h('span', duelPlayerMeta(d.p[0])), h('strong', d.p[1].n)]),
      ]
    );
}

const initMiniGame = (node: VNode) => playstrategy.miniGame.initAll(node.elm as HTMLElement);

export default function (ctrl: TournamentController): VNode {
  return h(
    'div.tour__table',
    {
      hook: {
        insert: initMiniGame,
        postpatch: initMiniGame,
      },
    },
    [
      ctrl.data.featured ? featured(ctrl.data.featured) : null,
      ctrl.data.duels.length
        ? h(
            'section.tour__duels',
            {
              hook: bind('click', _ => !ctrl.disableClicks),
            },
            [h('h2', 'Top games')].concat(ctrl.data.duels.map(renderDuel(ctrl.data.teamBattle, ctrl.data.duelTeams)))
          )
        : null,
    ]
  );
}
