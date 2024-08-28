import { h, VNode } from 'snabbdom';
import TournamentController from '../ctrl';
import { TournamentData, MaybeVNodes } from '../interfaces';
import * as pagination from '../pagination';
import { controls, standing, podium } from './arena';
import { teamStanding } from './battle';
import header from './header';
import playerInfo from './playerInfo';
import teamInfo from './teamInfo';
import { numberRow, medleyVariantsHoriz, medleyVariantsList } from './util';

function confetti(data: TournamentData): VNode | undefined {
  if (data.me && data.isRecentlyFinished && playstrategy.once('tournament.end.canvas.' + data.id))
    return h('canvas#confetti', {
      hook: {
        insert: _ => playstrategy.loadScriptCJS('javascripts/confetti.js'),
      },
    });
  return;
}

function stats(data: TournamentData, trans: Trans): VNode {
  const noarg = trans.noarg;
  const tableData = [
    !data.medley ? numberRow(noarg('averageElo'), data.stats.averageRating, 'raw') : null,
    numberRow(noarg('gamesPlayed'), data.stats.games),
    numberRow(noarg('movesPlayed'), data.stats.moves),
    numberRow(
      trans('playerIndexWins', data.p1Name ? data.p1Name : 'P1'),
      [data.stats.p1Wins, data.stats.games],
      'percent',
    ),
    numberRow(
      trans('playerIndexWins', data.p2Name ? data.p2Name : 'P2'),
      [data.stats.p2Wins, data.stats.games],
      'percent',
    ),
    numberRow(noarg('draws'), [data.stats.draws, data.stats.games], 'percent'),
  ];

  if (data.berserkable) {
    const berserkRate = [data.stats.berserks / 2, data.stats.games];
    tableData.push(numberRow(noarg('berserkRate'), berserkRate, 'percent'));
  }

  return h('div.tour__stats', [
    h('h2', noarg('tournamentComplete')),
    h('table', tableData),
    h('div.tour__stats__links', [
      ...(data.teamBattle
        ? [
            h(
              'a',
              {
                attrs: {
                  href: `/tournament/${data.id}/teams`,
                },
              },
              trans('viewAllXTeams', Object.keys(data.teamBattle.teams).length),
            ),
            h('br'),
          ]
        : []),
      h(
        'a.text',
        {
          attrs: {
            'data-icon': 'x',
            href: `/api/tournament/${data.id}/games`,
            download: true,
          },
        },
        'Download all games',
      ),
      h(
        'a.text',
        {
          attrs: {
            'data-icon': 'x',
            href: `/api/tournament/${data.id}/results`,
            download: true,
          },
        },
        'Download results as NDJSON',
      ),
      h(
        'a.text',
        {
          attrs: {
            'data-icon': 'x',
            href: `/api/tournament/${data.id}/results?as=csv`,
            download: true,
          },
        },
        'Download results as CSV',
      ),
      h('br'),
      h(
        'a.text',
        {
          attrs: {
            'data-icon': '',
            href: 'https://playstrategy.org/api#tag/Arena-tournaments',
          },
        },
        'Arena API documentation',
      ),
    ]),
  ]);
}

export const name = 'finished';

export function main(ctrl: TournamentController): MaybeVNodes {
  const pag = pagination.players(ctrl);
  const teamS = teamStanding(ctrl, 'finished');
  return [
    ...(teamS ? [header(ctrl), teamS] : [h('div.podium-wrap', [confetti(ctrl.data), header(ctrl), podium(ctrl)])]),
    ctrl.data.medley ? medleyVariantsHoriz(ctrl) : null,
    controls(ctrl, pag),
    standing(ctrl, pag),
  ];
}

export function table(ctrl: TournamentController): VNode | undefined {
  return ctrl.showingMedleyVariants
    ? medleyVariantsList(ctrl, true)
    : ctrl.playerInfo.id
      ? playerInfo(ctrl)
      : ctrl.teamInfo.requested
        ? teamInfo(ctrl)
        : stats
          ? stats(ctrl.data, ctrl.trans)
          : undefined;
}
