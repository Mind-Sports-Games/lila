import { FormStore, toFormLines, makeStore } from './form';
import modal from 'common/modal';
import debounce from 'common/debounce';
import * as xhr from 'common/xhr';
import LobbyController from './ctrl';
import { ClockConfig } from './interfaces';

export default class Setup {
  stores: {
    game: FormStore;
  };

  constructor(
    readonly makeStorage: (name: string) => PlayStrategyStorage,
    readonly root: LobbyController,
  ) {
    this.stores = {
      game: makeStore(makeStorage('lobby.setup.game')),
    };
  }

  private save = (form: HTMLFormElement) => this.stores[form.getAttribute('data-type')!].set(toFormLines(form));

  private sliderTimes = [
    0,
    1 / 4,
    1 / 2,
    3 / 4,
    1,
    3 / 2,
    2,
    3,
    4,
    5,
    6,
    7,
    8,
    9,
    10,
    11,
    12,
    13,
    14,
    15,
    16,
    17,
    18,
    19,
    20,
    25,
    30,
    35,
    40,
    45,
    60,
    75,
    90,
    105,
    120,
    135,
    150,
    165,
    180,
  ];

  private sliderTime = (v: number) => (v < this.sliderTimes.length ? this.sliderTimes[v] : 180);

  private sliderIncrement = (v: number) => {
    if (v <= 20) return v;
    switch (v) {
      case 21:
        return 25;
      case 22:
        return 30;
      case 23:
        return 35;
      case 24:
        return 40;
      case 25:
        return 45;
      case 26:
        return 60;
      case 27:
        return 90;
      case 28:
        return 120;
      case 29:
        return 150;
      default:
        return 180;
    }
  };

  private sliderHandicap = (v: number) => (v < 26 ? v : 0);

  private sliderKomisCache: { [bs: number]: number[] } = {};

  private sliderKomis = (bs: number) => {
    if (!this.sliderKomisCache[bs]) {
      this.sliderKomisCache[bs] = [...Array(bs * bs * 4 + 1).keys()].map(i => -(bs * bs * 10) + i * 5);
    }
    return this.sliderKomisCache[bs];
  };

  private sliderKomi = (bs: number) => (v: number) => (v < this.sliderKomis(bs).length ? this.sliderKomis(bs)[v] : 75);

  private sliderPoints = (v: number) => {
    if (v < 0 || v > 16) {
      return 1;
    } else {
      return v * 2 + 1;
    }
  };

  private sliderDays = (v: number) => {
    if (v <= 3) return v;
    switch (v) {
      case 4:
        return 5;
      case 5:
        return 7;
      case 6:
        return 10;
      default:
        return 14;
    }
  };

  private sliderInitVal = (v: number, f: (x: number) => number, max: number) => {
    for (let i = 0; i < max; i++) {
      if (f(i) === v) return i;
    }
    return undefined;
  };

  private clockDefaults = (variant: string) => {
    const defaultClockConfig = {
      bullet: { timemode: '1', initial: '1', increment: '0' },
      blitz: { timemode: '1', initial: '3', increment: '2' },
      rapid: { timemode: '1', initial: '10', increment: '5' },
      classical: { timemode: '1', initial: '20', increment: '10' },
      correspondence: { timemode: '2', days: '2' },
      custom: { timemode: '6' },
    };
    switch (variant) {
      case '0_8': //horde
      case '4_2': //xiangqi
        return Object.assign({}, defaultClockConfig, {
          blitz: { timemode: '1', initial: '5', increment: '3' },
        });
      case '1_1': //international draughts
      case '1_6': //antidraughts
      case '1_9': //breakthrough
      case '1_10': //frisian
        return Object.assign({}, defaultClockConfig, {
          bullet: { timemode: '1', initial: '2', increment: '1' },
          blitz: { timemode: '1', initial: '5', increment: '3' },
        });
      case '1_8': //frysk
      case '11_10': //minibreakthrough
        return Object.assign({}, defaultClockConfig, {
          bullet: { timemode: '1', initial: '0.5', increment: '0' },
          blitz: { timemode: '1', initial: '2', increment: '1' },
          rapid: { timemode: '1', initial: '5', increment: '3' },
        });
      case '3_5': //mini-shogi
        return Object.assign({}, defaultClockConfig, {
          bullet: { timemode: '3', byoyomi: '5', periods: '1', increment: '0', initial: '0' },
          blitz: { timemode: '3', byoyomi: '5', periods: '1', increment: '0', initial: '3' },
          rapid: { timemode: '3', byoyomi: '10', periods: '1', increment: '0', initial: '5' },
          classical: { timemode: '3', byoyomi: '20', periods: '1', increment: '0', initial: '10' },
        });
      case '3_1': //shogi
        return Object.assign({}, defaultClockConfig, {
          bullet: { timemode: '3', byoyomi: '10', periods: '1', increment: '0', initial: '0' },
          blitz: { timemode: '3', byoyomi: '10', periods: '1', increment: '0', initial: '5' },
          rapid: { timemode: '3', byoyomi: '15', periods: '1', increment: '0', initial: '10' },
          classical: { timemode: '3', byoyomi: '30', periods: '1', increment: '0', initial: '15' },
        });
      case '5_6': // othello
        return Object.assign({}, defaultClockConfig, {
          bullet: { timemode: '1', initial: '1', increment: '0' },
          blitz: { timemode: '1', initial: '5', increment: '0' },
          rapid: { timemode: '1', initial: '15', increment: '0' },
          classical: { timemode: '1', initial: '20', increment: '10' },
        });
      case '5_7': // grand othello
        return Object.assign({}, defaultClockConfig, {
          bullet: { timemode: '1', initial: '2', increment: '0' },
          blitz: { timemode: '1', initial: '8', increment: '0' },
          rapid: { timemode: '1', initial: '20', increment: '0' },
          classical: { timemode: '1', initial: '20', increment: '10' },
        });
      case '6_1': // oware
      case '7_2': // bestemshe
        return Object.assign({}, defaultClockConfig, {
          bullet: { timemode: '1', initial: '1', increment: '1' },
          blitz: { timemode: '1', initial: '3', increment: '2' },
          rapid: { timemode: '1', initial: '7', increment: '3' },
          classical: { timemode: '1', initial: '20', increment: '10' },
        });
      case '7_1': // togyzkumalak
        return Object.assign({}, defaultClockConfig, {
          bullet: { timemode: '1', initial: '1', increment: '1' },
          blitz: { timemode: '1', initial: '5', increment: '2' },
          rapid: { timemode: '1', initial: '10', increment: '3' },
          classical: { timemode: '1', initial: '20', increment: '10' },
        });
      case '8_8': //amazons
        return Object.assign({}, defaultClockConfig, {
          bullet: { timemode: '1', initial: '1', increment: '1' },
          blitz: { timemode: '1', initial: '3', increment: '5' },
          rapid: { timemode: '1', initial: '10', increment: '10' },
          classical: { timemode: '1', initial: '20', increment: '10' },
        });
      case '9_1': //go9x9
        return Object.assign({}, defaultClockConfig, {
          bullet: { timemode: '3', byoyomi: '2', periods: '1', increment: '0', initial: '1' },
          blitz: { timemode: '3', byoyomi: '10', periods: '5', increment: '0', initial: '2' },
          rapid: { timemode: '3', byoyomi: '30', periods: '3', increment: '0', initial: '10' },
          classical: { timemode: '3', byoyomi: '30', periods: '1', increment: '0', initial: '20' },
        });
      case '9_2': //go13x13
        return Object.assign({}, defaultClockConfig, {
          bullet: { timemode: '3', byoyomi: '3', periods: '1', increment: '0', initial: '1' },
          blitz: { timemode: '3', byoyomi: '10', periods: '5', increment: '0', initial: '3' },
          rapid: { timemode: '3', byoyomi: '30', periods: '3', increment: '0', initial: '15' },
          classical: { timemode: '3', byoyomi: '40', periods: '1', increment: '0', initial: '30' },
        });
      case '9_4': //go19x19
        return Object.assign({}, defaultClockConfig, {
          bullet: { timemode: '3', byoyomi: '5', periods: '1', increment: '0', initial: '1' },
          blitz: { timemode: '3', byoyomi: '10', periods: '5', increment: '0', initial: '5' },
          rapid: { timemode: '3', byoyomi: '30', periods: '3', increment: '0', initial: '20' },
          classical: { timemode: '3', byoyomi: '60', periods: '1', increment: '0', initial: '40' },
        });
      case '10_1': //backgammon
        return Object.assign({}, defaultClockConfig, {
          bullet: { timemode: '5', increment: '5', initial: '1' },
          blitz: { timemode: '5', increment: '10', initial: '1.5' },
          rapid: { timemode: '5', increment: '12', initial: '2' },
          classical: { timemode: '5', increment: '15', initial: '3' },
        });
      case '10_2': //nackgammon
        return Object.assign({}, defaultClockConfig, {
          bullet: { timemode: '5', increment: '5', initial: '1' },
          blitz: { timemode: '5', increment: '10', initial: '2' },
          rapid: { timemode: '5', increment: '12', initial: '2' },
          classical: { timemode: '5', increment: '15', initial: '3' },
        });
      case '10_4': //hyper
        return Object.assign({}, defaultClockConfig, {
          bullet: { timemode: '5', increment: '3', initial: '0.5' },
          blitz: { timemode: '5', increment: '10', initial: '1' },
          rapid: { timemode: '5', increment: '12', initial: '1.5' },
          classical: { timemode: '5', increment: '15', initial: '3' },
        });
      case '12_1': // abalone
        return Object.assign({}, defaultClockConfig, {
          bullet: { timemode: '1', initial: '2', increment: '1' },
          blitz: { timemode: '1', initial: '5', increment: '3' },
          rapid: { timemode: '1', initial: '10', increment: '5' },
          classical: { timemode: '1', initial: '20', increment: '10' },
        });
      default:
        return defaultClockConfig;
    }
  };

  private clockDisplayText = (clockConfig: ClockConfig) => {
    const capitalize = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);
    const showTime = (v: string) => {
      if (v == '0.25') return '¼';
      if (v == '0.5') return '½';
      if (v == '0.75') return '¾';
      return '' + v;
    };
    switch (clockConfig.timemode) {
      case '2': //correspondence
        return clockConfig.days + ' day' + (clockConfig.days !== '1' ? 's' : '');
      case '3': //byoyomi
        return (
          showTime(clockConfig.initial) +
          (clockConfig.increment !== '0' ? ' + ' + clockConfig.increment : '') +
          `|${clockConfig.byoyomi}${clockConfig.periods !== '1' ? `(${clockConfig.periods}x)` : ''}`
        );
      case '4': //bronstein
        return showTime(clockConfig.initial) + ' d+' + clockConfig.increment;
      case '5': //simple delay
        return showTime(clockConfig.initial) + ' d/' + clockConfig.increment;
      case '0': //unlimited
        return capitalize(this.root.trans('unlimited'));
      case '6': //custom
        return capitalize(this.root.trans('custom'));
      default:
        return showTime(clockConfig.initial) + ' + ' + clockConfig.increment;
    }
  };

  private hookToPoolMember = (playerIndex: string, form: HTMLFormElement) => {
    const data = Array.from(new FormData(form).entries());
    const hash: any = {};
    for (const i in data) hash[data[i][0]] = data[i][1];
    const valid = playerIndex == 'random' && hash.mode == 1 && hash.timeMode == 1,
      id = parseFloat(hash.time) + '+' + parseInt(hash.increment);
    return valid && this.root.pools.find(p => p.id === id)
      ? {
          id,
          range: hash.ratingRange,
        }
      : undefined;
  };

  private psBots = ['ps-random-mover', 'ps-greedy-one-move', 'ps-greedy-two-move', 'ps-greedy-four-move'];
  private stockfishBots = [
    'stockfish-level1',
    'stockfish-level2',
    'stockfish-level3',
    'stockfish-level4',
    'stockfish-level5',
    'stockfish-level6',
    'stockfish-level7',
    'stockfish-level8',
  ];
  private allBots = this.psBots.concat(this.stockfishBots);
  private ratedTimeModes = ['1', '3', '4', '5'];

  prepareForm = ($modal: Cash) => {
    const self = this,
      $form = $modal.find('form'),
      $timeModeSelect = $form.find('#sf_timeMode'),
      $timeModeDefaults = $form.find('.time_mode_defaults'),
      $modeChoicesWrap = $form.find('.mode_choice'),
      $modeChoices = $modeChoicesWrap.find('input'),
      $casual = $modeChoices.eq(0),
      $rated = $modeChoices.eq(1),
      $gameGroups = $form.find('.gameGroup_choice'),
      $gameGroupInput = $gameGroups.find('.gameGroup_choice [name=gameGroup]'),
      $variants = $form.find('.variant_choice'),
      $variantInput = $variants.find('.variant_choice [name=variant]'),
      inputVariant = $form.data('variant') as string,
      forceVariant = inputVariant !== '',
      $fenPosition = $form.find('.fen_position'),
      $fenInput = $fenPosition.find('input'),
      forceFromPosition = !!$fenInput.val(),
      $multiMatch = $form.find('.multi_match'),
      $timeInput = $form.find('.time_choice [name=time]'),
      $incrementInput = $form.find('.increment_choice [name=increment]'),
      $byoyomiInput = $form.find('.byoyomi_choice [name=byoyomi]'),
      $periodsInput = $form.find('.byoyomi_periods [name=periods]'),
      $goConfig = $form.find('.go_config'),
      $goHandicapInput = $form.find('.go_handicap_choice [name=goHandicap]'),
      $goKomiInput = $form.find('.go_komi_choice [name=goKomi]'),
      $backgammonConfig = $form.find('.backgammon_config'),
      $backgammonPointsInput = $form.find('.backgammon_points_choice [name=backgammonPoints]'),
      $advancedTimeSetup = $form.find('.advanced_setup'),
      $advancedTimeToggle = $form.find('.advanced_toggle'),
      $daysInput = $form.find('.days_choice [name=days]'),
      userDetails = $form.attr('action')?.split('user='),
      user = userDetails && userDetails[1] ? userDetails[1].toLowerCase() : '',
      vsPSBot = this.psBots.includes(user),
      vsStockfishBot = this.stockfishBots.includes(user),
      $botChoices = $form.find('.bot_choice'),
      $botInput = $form.find('.bot_choice [name=bot]'),
      $opponentInput = $form.find('.opponent_choices [name=opponent]'),
      typ = $form.data('type'),
      $ratings = $modal.find('.ratings > div'),
      $collapsibleSections = $modal.find('.collapsible'),
      randomPlayerIndexVariants = $form.data('random-playerindex-variants').split(','),
      $playerIndex = $form.find('.playerIndex_choices'),
      $playerIndexInput = $form.find('.playerIndex_choices [name=playerIndex]'),
      $submits = $form.find('.submit_button'),
      toggleButtons = () => {
        randomPlayerIndexVariants;
        const variantFull = $variantInput.filter(':checked').val() as string,
          variantId = variantFull.split('_'),
          timeMode = <string>$timeModeSelect.val(),
          rated = $rated.prop('checked'),
          limit = parseFloat($timeInput.val() as string),
          inc = parseFloat($incrementInput.val() as string),
          byo = parseFloat($byoyomiInput.val() as string),
          per = parseFloat($periodsInput.filter(':checked').val() as string),
          opponentType = $opponentInput.filter(':checked').val() as string,
          botUser = user === '' ? ($botInput.filter(':checked').val() as string) : user,
          playerIndex = $playerIndexInput.filter(':checked').val() as string,
          // no rated variants with less than 30s on the clock and no rated unlimited in the lobby
          cantBeRated =
            (opponentType === 'lobby' && timeMode === '0') ||
            this.ratedTimeModes.indexOf(timeMode) === -1 ||
            (limit < 0.5 && inc == 0) ||
            (limit == 0 && inc < 2) ||
            (playerIndex !== 'random' && randomPlayerIndexVariants.includes(variantFull)) ||
            variantFull === '0_3' ||
            (vsPSBot && botUser === 'ps-random-mover') ||
            (variantId[0] == '9' &&
              $goConfig.val() !== undefined &&
              (($goHandicapInput.val() as string) != '0' ||
                (variantId[1] !== '1' && ($goKomiInput.val() as string) != '75') ||
                (variantId[1] == '1' && ($goKomiInput.val() as string) != '55'))) ||
            //remove this if we ever want Backgammon cube games to be rated
            (variantId[0] == '10' &&
              $backgammonConfig.val() !== undefined &&
              ($backgammonPointsInput.val() as string) != '1'),
          cantBeLobby =
            (variantId[0] == '9' &&
              $goConfig.val() !== undefined &&
              (($goHandicapInput.val() as string) != '0' ||
                (variantId[1] !== '1' && ($goKomiInput.val() as string) != '75') ||
                (variantId[1] == '1' && ($goKomiInput.val() as string) != '55'))) ||
            variantFull === '0_3',
          cantBeBot = !isRealTime();
        if (cantBeRated && rated) {
          $casual.trigger('click');
          $form.find('.mode_0.choice').show();
          $form.find('.mode_1.choice').hide();
          return toggleButtons();
        }
        if ((cantBeLobby && opponentType === 'lobby') || (cantBeBot && opponentType === 'bot')) {
          const $friend = $opponentInput.eq(1);
          $friend.trigger('click');
          $form.find('.opponent_lobby.choice').hide();
          $form.find('.opponent_bot.choice').hide();
          $form.find('.opponent_friend.choice').show();
          return toggleButtons();
        }
        $rated.prop('disabled', !!cantBeRated).siblings('label').toggleClass('disabled', cantBeRated);
        $opponentInput.eq(0).prop('disabled', !!cantBeLobby).siblings('label').toggleClass('disabled', cantBeLobby);
        $opponentInput.eq(2).prop('disabled', !!cantBeBot).siblings('label').toggleClass('disabled', cantBeBot);
        $botInput.each(function (i, input) {
          const isDisabled = !botCanPlay(self.allBots[i], limit, inc, byo, variantId);
          const $input = $(input);
          $input.prop('disabled', isDisabled);
          $input.siblings('label').toggleClass('disabled', isDisabled);
        });
        const byoOk = timeMode !== '3' || ((limit > 0 || inc > 0 || byo > 0) && (byo || per === 1));
        const delayOk = (timeMode !== '4' && timeMode !== '5') || inc > 0;
        const timeOk = timeMode !== '1' || limit > 0 || inc > 0,
          ratedOk = opponentType !== 'lobby' || !rated || timeMode !== '0',
          fenOk = variantId[0] !== '0' || variantId[1] !== '3' || $fenInput.hasClass('success'),
          botOK = opponentType !== 'bot' || botCanPlay(botUser, limit, inc, byo, variantId);
        if (byoOk && delayOk && timeOk && ratedOk && fenOk && botOK) {
          $submits.toggleClass('nope', false);
        } else {
          $submits.toggleClass('nope', true);
          if (!botOK && !vsPSBot && !vsStockfishBot) {
            const defaultBot = 'ps-greedy-two-move';
            const backupBot = 'ps-random-mover';
            if (
              $botInput.filter(':checked').val() !== defaultBot &&
              botCanPlay(defaultBot, limit, inc, byo, variantId)
            ) {
              $botInput.val(defaultBot).trigger('change');
            } else if (
              $botInput.filter(':checked').val() !== backupBot &&
              botCanPlay(backupBot, limit, inc, byo, variantId)
            ) {
              $botInput.val(backupBot).trigger('change');
            }
            if (opponentType === 'bot') {
              const $bot = $opponentInput.eq(2);
              $bot.trigger('click');
            }
          }
        }
      },
      save = function () {
        self.save($form[0] as HTMLFormElement);
      };

    const botCanPlay = (user: string, limit: number, inc: number, byo: number, variantId: string[]) => {
      let variantCompatible = true;
      if (/^stockfish-level[1-8]$/.test(user)) {
        variantCompatible =
          (variantId[0] === '0' && variantId[1] !== '15') || ['3', '4', '5', '11'].includes(variantId[0]);
      } else {
        switch (user) {
          case 'ps-greedy-four-move': {
            //in (draughts, oware, togy)
            variantCompatible = ['1', '6', '7'].includes(variantId[0]);
            break;
          }
          default:
            variantCompatible = true;
        }
      }

      let clockCompatible = true;
      if (isRealTime()) {
        if (/^stockfish-level[1-8]$/.test(user)) {
          clockCompatible = limit >= 0.5;
        } else {
          switch (user) {
            case 'ps-random-mover': {
              clockCompatible = limit >= 0.5 || byo > 2;
              break;
            }
            case 'ps-greedy-one-move': {
              clockCompatible = limit >= 1 && inc >= 1;
              break;
            }
            default: {
              clockCompatible = limit >= 3 && inc >= 2;
            }
          }
        }
      } else {
        clockCompatible = false;
      }
      return variantCompatible && clockCompatible;
    };
    const setBaseDefaultOptions = () => {
      $gameGroupInput.val('0'); //default to chess
      $variantInput.val('0_1'); //default to standard chess
      const clockConfig = self.clockDefaults('chess'); //default of chess
      $timeModeSelect.val(clockConfig['blitz'].timemode);
      $timeInput.val(clockConfig['blitz'].initial);
      $incrementInput.val(clockConfig['blitz'].increment);
      $timeModeDefaults.find('input').val('blitz'); //default to real time blitz
    };
    const clearFenInput = () => $fenInput.val('');
    setBaseDefaultOptions();
    const c = this.stores[typ].get();
    if (c) {
      Object.keys(c).forEach(k => {
        $form.find(`[name="${k}"]`).each(function (this: HTMLInputElement) {
          if (k === 'timeMode' && this.value !== '1') return;
          if (this.type == 'checkbox') this.checked = true;
          else if (this.type == 'radio') this.checked = this.value == c[k];
          else if (k != 'fen' || !this.value) this.value = c[k];
        });
      });
    }
    const isRealTime = () => this.ratedTimeModes.indexOf(<string>$timeModeSelect.val()) !== -1;

    //default options for challenge against bots
    if (vsPSBot || vsStockfishBot) {
      setBaseDefaultOptions();
      $casual.trigger('click');
      if (user !== '') $botInput.val(user);

      //disable non realtime modes
      $('#sf_timeModeDefaults_correspondence, #sf_timeModeDefaults_custom')
        .prop('disabled', true)
        .siblings('label')
        .toggleClass('disabled', true);

      const limit = parseFloat($timeInput.val() as string),
        inc = parseFloat($incrementInput.val() as string),
        byo = parseFloat($byoyomiInput.val() as string);

      // Disable variant options the bot cannot play
      $variantInput.each(function (_, el) {
        const $el = $(el);
        const variantId = ($el.val() as string).split('_');
        const canPlay = botCanPlay(user, limit, inc, byo, variantId);
        $el.prop('disabled', !canPlay).siblings('label').toggleClass('disabled', !canPlay);
      });

      // Disable game group options the bot cannot play
      $gameGroupInput.each(function (_, el) {
        const $el = $(el);
        // Find the first variant for this game group
        const groupValue = $el.val() as string;
        // Find a variantId for this group (e.g., the first matching variant)
        const variantForGroup = $variantInput
          .filter(function () {
            return ($(this).val() as string).split('_')[0] === groupValue;
          })
          .first();
        let canPlay = true;
        if (variantForGroup.length) {
          const variantId = (variantForGroup.val() as string).split('_');
          canPlay = botCanPlay(user, limit, inc, byo, variantId);
        }
        $el.prop('disabled', !canPlay).siblings('label').toggleClass('disabled', !canPlay);
      });
    }

    const showRating = () => {
      const variantId = ($variantInput.filter(':checked').val() as string).split('_'),
        timeMode = $timeModeSelect.val();
      let key = 'correspondence';
      switch (variantId[0]) {
        case '0':
          switch (variantId[1]) {
            case '1':
            case '3':
              if (timeMode == '1') {
                const time =
                  parseFloat($timeInput.val() as string) * 60 + parseFloat($incrementInput.val() as string) * 40;
                if (time < 30) key = 'ultraBullet';
                else if (time < 180) key = 'bullet';
                else if (time < 480) key = 'blitz';
                else if (time < 1500) key = 'rapid';
                else key = 'classical';
              }
              break;
            case '10':
              key = 'crazyhouse';
              break;
            case '2':
              key = 'chess960';
              break;
            case '4':
              key = 'kingOfTheHill';
              break;
            case '5':
              key = 'threeCheck';
              break;
            case '6':
              key = 'antichess';
              break;
            case '7':
              key = 'atomic';
              break;
            case '8':
              key = 'horde';
              break;
            case '9':
              key = 'racingKings';
              break;
            case '12':
              key = 'fiveCheck';
              break;
            case '13':
              key = 'noCastling';
              break;
            case '15':
              key = 'monster';
              break;
            default:
              key = 'standard';
              break;
          }
          break;
        case '1':
          switch (variantId[1]) {
            case '1':
              key = 'international';
              break;
            case '10':
              key = 'frisian';
              break;
            case '8':
              key = 'frysk';
              break;
            case '6':
              key = 'antidraughts';
              break;
            case '9':
              key = 'breakthrough';
              break;
            case '11':
              key = 'russian';
              break;
            case '12':
              key = 'brazilian';
              break;
            case '13':
              key = 'pool';
              break;
            case '14':
              key = 'portuguese';
              break;
            case '15':
              key = 'english';
              break;
          }
          break;
        case '2':
          switch (variantId[1]) {
            case '11':
              key = 'linesOfAction';
              break;
            case '14':
              key = 'scrambledEggs';
              break;
          }
          break;
        case '3':
          switch (variantId[1]) {
            case '1':
              key = 'shogi';
              break;
            case '5':
              key = 'minishogi';
              break;
          }
          break;
        case '4':
          switch (variantId[1]) {
            case '2':
              key = 'xiangqi';
              break;
            case '4':
              key = 'minixiangqi';
              break;
          }
          break;
        case '5':
          switch (variantId[1]) {
            case '6':
              key = 'flipello';
              break;
            case '7':
              key = 'flipello10';
              break;
          }
          break;
        case '8':
          key = 'amazons';
          break;
        case '11':
          switch (variantId[1]) {
            case '9':
              key = 'breakthroughtroyka';
              break;
            case '10':
              key = 'minibreakthroughtroyka';
              break;
          }
          break;
        case '6':
          key = 'oware';
          break;
        case '7':
          switch (variantId[1]) {
            case '1':
              key = 'togyzkumalak';
              break;
            case '2':
              key = 'bestemshe';
              break;
          }
          break;
        case '9':
          switch (variantId[1]) {
            case '1':
              key = 'go9x9';
              break;
            case '2':
              key = 'go13x13';
              break;
            case '4':
              key = 'go19x19';
              break;
          }
          break;
        case '10':
          switch (variantId[1]) {
            case '1':
              key = 'backgammon';
              break;
            case '2':
              key = 'nackgammon';
              break;
            case '4':
              key = 'hyper';
              break;
          }
          break;
        case '12':
          key = 'abalone';
          break;
      }
      const $selected = $ratings
        .hide()
        .filter('.' + key)
        .show();
      $modal.find('.ratings input').val($selected.find('strong').text());
      save();
    };
    const showStartingImages = () => {
      const variantId = ($variantInput.filter(':checked').val() as string).split('_');
      const class_list =
        'chess draughts loa shogi xiangqi flipello oware togyzkumalak amazons go backgammon breakthroughtroyka abalone';
      let key = 'chess';
      switch (variantId[0]) {
        case '0':
          key = 'chess';
          break;
        case '1':
          key = 'draughts';
          break;
        case '2':
          key = 'loa';
          break;
        case '3':
          key = 'shogi';
          break;
        case '4':
          key = 'xiangqi';
          break;
        case '5':
          key = 'flipello';
          break;
        case '8':
          key = 'amazons';
          break;
        case '6':
          key = 'oware';
          break;
        case '7':
          key = 'togyzkumalak';
          break;
        case '9':
          key = 'go';
          break;
        case '10':
          key = 'backgammon';
          break;
        case '11':
          key = 'breakthroughtroyka';
          break;
        case '12':
          key = 'abalone';
          break;
      }
      $playerIndex.removeClass(class_list);
      $playerIndex.addClass(key);
      save();
    };

    const resetIncSlider = (): void => {
      $incrementInput.val('0');
      $('.increment_choice .ui-slider').val('0');
      $('.increment_choice input').siblings('span').text('0');
    };

    const resetPeriods = (): void => {
      $periodsInput.eq(0).trigger('click');
    };

    const customClockConfig = () => {
      return {
        timemode: $timeModeSelect.val(),
        initial: $timeInput.val(),
        increment: $incrementInput.val(),
        byoyomi: $byoyomiInput.val(),
        periods: $periodsInput.filter(':checked').val() as string,
        days: $daysInput.val(),
      } as ClockConfig;
    };

    const updateClockOptionsText = () => {
      const variantId = $variantInput.filter(':checked').val() as string;
      const clockConfig = self.clockDefaults(variantId);
      $timeModeDefaults.find('label').each(function (this: HTMLElement) {
        const $this = $(this);
        const clockType = $this.attr('for').split('_')[2];
        if (clockType !== 'custom') {
          $this.text(self.clockDisplayText(clockConfig[clockType]));
        }
        const $selectedChoice = $timeModeDefaults.find(`div.choice.${$this.attr('for').replace('sf_', '')}`);
        if (clockType !== 'custom') {
          $selectedChoice.text(self.clockDisplayText(clockConfig[clockType]));
        } else {
          $selectedChoice.text(self.clockDisplayText(customClockConfig()));
        }
      });
    };
    const setAnonOptions = () => {
      const isAnon = $form.data('anon');
      if (isAnon) {
        setBaseDefaultOptions(); //defult to chess, real time, blitz clock
        $casual.trigger('click');
        $opponentInput.val('lobby'); //default to lobby
        const opponent = $opponentInput.filter(':checked').val() as string;
        if (opponent === 'lobby') {
          $timeModeSelect
            .val('1')
            .children('.timeMode_2, .timeMode_0')
            .prop('disabled', true)
            .attr('title', this.root.trans('youNeedAnAccountToDoThat'));
        }
        //disable non realtime modes
        $('#sf_timeModeDefaults_correspondence, #sf_timeModeDefaults_custom')
          .prop('disabled', true)
          .siblings('label')
          .toggleClass('disabled', true);
      }
    };
    setAnonOptions();
    const updateLobbySubmit = () => {
      if (($opponentInput.filter(':checked').val() as string) === 'lobby') {
        const ajaxSubmit = (playerIndex: string) => {
          const form = $form[0] as HTMLFormElement;
          const rating = parseInt($modal.find('.ratings input').val() as string) || 1500;
          if (form.ratingRange)
            form.ratingRange.value = [
              rating + parseInt(form.ratingRange_range_min.value),
              rating + parseInt(form.ratingRange_range_max.value),
            ].join('-');
          save();
          const poolMember = this.hookToPoolMember(playerIndex, form);
          modal.close();
          if (poolMember) {
            this.root.enterPool(poolMember);
          } else {
            this.root.setTab(isRealTime() ? 'real_time' : 'seeks');
            xhr.text($form.attr('action')!.replace(/sri-placeholder/, playstrategy.sri), {
              method: 'post',
              body: (() => {
                const data = new FormData($form[0] as HTMLFormElement);
                return data;
              })(),
            });
          }
          this.root.redraw();
          return false;
        };
        $submits
          .off('click') // Always remove previous handlers first!
          .on('click', function (this: HTMLElement) {
            return ajaxSubmit($playerIndexInput.filter(':checked').val() as string);
          })
          .prop('disabled', false);
        $form.off('submit').on('submit', () => ajaxSubmit('random'));
      } else {
        $submits.off('click').prop('disabled', false);
        $form.off('submit'); // Remove any custom submit handler
        $form.one('submit', () => {
          $submits.hide();
          $form.find('.submit').append(playstrategy.spinnerHtml);
        });
      }
    };
    updateLobbySubmit();

    const getIncrementMin = () => {
      const timeMode = $timeModeSelect.val() as string;
      // For Bronstein (4) and Simple Delay (5): min 1, others: min 0
      return timeMode === '4' || timeMode === '5' ? 1 : 0;
    };

    // Update increment input min value and slider
    const updateIncrementMin = () => {
      const min = getIncrementMin();
      $incrementInput.attr('min', String(min));
      $incrementInput.parent().find('.range').attr('min', String(min));
      // If current value is less than min, set to min
      if (parseFloat($incrementInput.val() as string) < min) {
        $incrementInput.val(String(min));
        $incrementInput
          .parent()
          .find('span')
          .text($incrementInput.val() as string);
        $incrementInput
          .parent()
          .find('.range')
          .val('' + self.sliderInitVal(min, self.sliderIncrement, 100));
      }
    };

    $timeModeSelect.on('change', updateIncrementMin);
    $timeModeDefaults.on('change', updateIncrementMin);
    updateIncrementMin();

    if (this.root.opts.blindMode) {
      $variantInput.filter(':checked')[0]!.focus();
      $timeInput.add($incrementInput).on('change', () => {
        toggleButtons();
        showRating();
      });
    } else {
      $timeInput.add($incrementInput).each(function (this: HTMLInputElement) {
        const $input = $(this),
          $value = $input.siblings('span'),
          $range = $input.siblings('.range'),
          isTimeSlider = $input.parent().hasClass('time_choice'),
          showTime = (v: number) => {
            if (v == 1 / 4) return '¼';
            if (v == 1 / 2) return '½';
            if (v == 3 / 4) return '¾';
            return '' + v;
          },
          valueToTime = (v: number) => (isTimeSlider ? self.sliderTime : self.sliderIncrement)(v),
          show = (time: number) => $value.text(isTimeSlider ? showTime(time) : '' + time);
        show(parseFloat($input.val() as string));
        $range.attr({
          min: '0',
          max: '' + (isTimeSlider ? 38 : 30),
          value:
            '' +
            self.sliderInitVal(
              parseFloat($input.val() as string),
              isTimeSlider ? self.sliderTime : self.sliderIncrement,
              100,
            ),
        });
        $range.on('input', () => {
          const time = valueToTime(parseInt($range.val() as string));
          show(time);
          $input.val('' + time);
          showRating();
          toggleButtons();
        });
      });
      $daysInput.each(function (this: HTMLInputElement) {
        const $input = $(this),
          $value = $input.siblings('span'),
          $range = $input.siblings('.range');
        $value.text($input.val() as string);
        $range.attr({
          min: '1',
          max: '7',
          value: '' + self.sliderInitVal(parseInt($input.val() as string), self.sliderDays, 20),
        });
        $range.on('input', () => {
          const days = self.sliderDays(parseInt($range.val() as string));
          $value.text('' + days);
          $input.val('' + days);
          save();
        });
      });
      $byoyomiInput.each(function (this: HTMLInputElement) {
        const $input = $(this),
          $value = $input.siblings('span'),
          $range = $input.siblings('.range');
        $value.text($input.val() as string);
        // 1-20 1 increment
        // 20-45 5 increment
        // 45-60 15 increment
        // 60-180 30 increment
        $range.attr({
          min: '1',
          max: '38',
          value: '' + self.sliderInitVal(parseInt($input.val() as string), self.sliderIncrement, 20),
        });
        $range.on('input', () => {
          const byoyomi = self.sliderIncrement(parseInt($range.val() as string));
          $value.text('' + byoyomi);
          $input.val('' + byoyomi);
          save();
        });
      });
    }
    $goHandicapInput.each(function (this: HTMLInputElement) {
      const $input = $(this),
        $value = $input.siblings('span'),
        $range = $input.siblings('.range');
      $value.text($input.val() as string);
      $range.attr({
        min: '0',
        max: '25',
        value: '' + self.sliderInitVal(parseInt($input.val() as string), self.sliderHandicap, 26),
      });
      $range.on('input', () => {
        const goHandicap = self.sliderHandicap(parseInt($range.val() as string));
        $value.text('' + goHandicap);
        $input.val('' + goHandicap);
        save();
        clearFenInput();
        toggleButtons();
      });
    });
    const setupGoKomiInput = () => {
      $goKomiInput.each(function (this: HTMLInputElement) {
        const $input = $(this),
          $value = $input.siblings('span'),
          $range = $input.siblings('.range'),
          showKomi = (v: number) => {
            return ('' + v / 10.0).replace('.0', '');
          },
          show = (komi: number) => $value.text(showKomi(komi)),
          variantId = ($variantInput.filter(':checked').val() as string).split('_'),
          boardsize = variantId[1] == '1' ? 9 : variantId[1] == '2' ? 13 : 19,
          defaultValue = boardsize === 9 ? 55 : 75,
          initialValue =
            Math.abs(parseInt($input.val() as string)) > boardsize * boardsize * 10
              ? defaultValue
              : parseInt($input.val() as string);
        if (initialValue === defaultValue) $input.val(defaultValue.toString());
        show(initialValue);
        $range.attr({
          min: '0',
          max: (boardsize * boardsize * 2 * 2).toString(),
          value: '' + self.sliderInitVal(initialValue, self.sliderKomi(boardsize), boardsize * boardsize * 2 * 2),
        });
        $range.val('' + self.sliderInitVal(initialValue, self.sliderKomi(boardsize), boardsize * boardsize * 2 * 2));
        save();
        $range.on('input', () => {
          const goKomi = self.sliderKomi(boardsize)(parseInt($range.val() as string));
          show(goKomi);
          $input.val('' + goKomi);
          save();
          clearFenInput();
          toggleButtons();
        });
      });
    };
    setupGoKomiInput();
    $backgammonPointsInput.each(function (this: HTMLInputElement) {
      const $input = $(this),
        $value = $input.siblings('span'),
        $range = $input.siblings('.range');
      $value.text($input.val() as string);
      $range.attr({
        min: '0',
        max: '15',
        value: '' + self.sliderInitVal(parseInt($input.val() as string), self.sliderPoints, 16),
      });
      $range.on('input', () => {
        const backgammonPoints = self.sliderPoints(parseInt($range.val() as string));
        $value.text('' + backgammonPoints);
        $input.val('' + backgammonPoints);
        save();
        clearFenInput();
        toggleButtons();
      });
    });
    const updateBotDetails = () => {
      let bot = $botInput.filter(':checked').val() as string;
      if (!bot) {
        $botInput.val('ps-greedy-two-move'); //default bot
        bot = 'ps-greedy-two-move';
      }
      const botText = $form.find('.opponent_bot.choice');
      const botName = bot
        .replace('stockfish-l', 'Stockfish-L')
        .replace('ps-', 'PS-')
        .replace('greedy-', 'Greedy-')
        .replace('-move', '-Move')
        .replace('random', 'Random')
        .replace('one', 'One')
        .replace('four', 'Four')
        .replace('two', 'Two');
      botText.empty();
      botText.append(
        `<a class="user-link ulpt" href="/@/${bot}"><span class="utitle" data-bot="data-bot" title="Robot">BOT</span>&nbsp${botName}</a>`,
      );
      const botSelected = $opponentInput.filter(':checked').val() === 'bot';
      if (!botSelected) return;
      if (user || $form.attr('action').includes('user'))
        $form.attr('action', $form.attr('action')?.replace(/user=[^&]*/, 'user=' + bot));
      else $form.attr('action', $form.attr('action') + `&user=${bot}`);
    };
    const setupOpponentChoices = () => {
      $botChoices.hide();
      $form.find('.bot_title').hide();
      $form.find('.rating-range-config').hide();
      if (user) {
        if (vsPSBot || vsStockfishBot) {
          $opponentInput.val('bot');
        } else {
          $opponentInput.val('friend');
        }
      }
      updateBotDetails();
    };
    setupOpponentChoices();
    $form.find('.rating-range').each(function (this: HTMLDivElement) {
      const $this = $(this),
        $minInput = $this.find('.rating-range__min'),
        $maxInput = $this.find('.rating-range__max'),
        minStorage = self.makeStorage('lobby.ratingRange.min'),
        maxStorage = self.makeStorage('lobby.ratingRange.max'),
        update = (e?: Event) => {
          const min = $minInput.val() as string,
            max = $maxInput.val() as string;
          minStorage.set(min);
          maxStorage.set(max);
          $this.find('.rating-min').text(`-${min.replace('-', '')}`);
          $this.find('.rating-max').text(`+${max}`);
          if (e) save();
        };

      $minInput
        .attr({
          min: '-1000',
          max: '0',
          step: '100',
          value: minStorage.get() || '-1000',
        })
        .on('input', update);

      $maxInput
        .attr({
          min: '0',
          max: '1000',
          step: '100',
          value: maxStorage.get() || '1000',
        })
        .on('input', update);

      update();
    });
    $timeModeSelect
      .on('change', function (this: HTMLElement) {
        const timeMode = $(this).val();
        const isFischer = timeMode === '1';
        const isByoyomi = timeMode === '3';
        const isBronstein = timeMode === '4';
        const isSimple = timeMode === '5';
        $form.find('.time_choice, .increment_choice').toggle(isFischer || isByoyomi || isBronstein || isSimple);
        $form.find('.days_choice').toggle(timeMode === '2');
        $form.find('.byoyomi_choice, .byoyomi_periods').toggle(isByoyomi);
        toggleButtons();
        showRating();
      })
      .trigger('change');
    $timeModeDefaults
      .on('change', function (this: HTMLElement) {
        const choice = $(this).find('input').filter(':checked').val() as string;
        const clockConfig = self.clockDefaults($variantInput.filter(':checked').val() as string);
        switch (choice) {
          case 'correspondence': //correspondence
            $timeModeSelect.val(clockConfig['correspondence'].timemode);
            $daysInput.val(clockConfig['correspondence'].days);
            $form.find('.time_mode_config').hide();
            break;
          case 'custom': //custom - i.e. used old time setup options
            if ($(this).hasClass('active')) {
              $form.find('.time_mode_config').show();
              // Update text and range to show current form settings
              $timeInput.siblings('span').text($timeInput.val() as string);
              $incrementInput.siblings('span').text($incrementInput.val() as string);
              $byoyomiInput.siblings('span').text($byoyomiInput.val() as string);
              $periodsInput.siblings('span').text($periodsInput.filter(':checked').val() as string);
              $daysInput.siblings('span').text($daysInput.val() as string);
              // After updating the input and span values:
              [
                { input: $timeInput, slider: self.sliderTime, max: 38 },
                { input: $incrementInput, slider: self.sliderIncrement, max: 100 },
                { input: $byoyomiInput, slider: self.sliderIncrement, max: 20 },
                { input: $daysInput, slider: self.sliderDays, max: 20 },
              ].forEach(({ input, slider, max }) => {
                const val = parseFloat(input.val() as string);
                const $range = input.parent().find('.range');
                $range.val('' + self.sliderInitVal(val, slider, max));
              });
            } else {
              $form.find('.time_mode_config').hide();
            }
            $timeModeSelect.trigger('change');
            break;
          default:
            $timeModeSelect.val(clockConfig[choice].timemode);
            if (clockConfig[choice]['initial']) $timeInput.val(clockConfig[choice]['initial']);
            if (clockConfig[choice]['increment']) $incrementInput.val(clockConfig[choice]['increment']);
            if (clockConfig[choice]['byoyomi']) $byoyomiInput.val(clockConfig[choice]['byoyomi']);
            if (clockConfig[choice]['periods']) $periodsInput.val(clockConfig[choice]['periods']);
            $form.find('.time_mode_config').hide();
        }
        toggleButtons();
        showRating();
      })
      .trigger('change');
    const validateFen = debounce(() => {
      $fenInput.removeClass('success failure');
      const fen = $fenInput.val() as string;
      const variantId = ($variantInput.filter(':checked').val() as string).split('_');
      const lib = variantId[0];
      if (fen) {
        const [path, params] = $fenInput.parent().data('validate-url').split('?'); // Separate "strict=1" for AI match
        xhr.text(xhr.url(path, { lib, fen }) + (params ? `&${params}` : '')).then(
          data => {
            $fenInput.addClass('success');
            $fenPosition.find('.preview').html(data);
            $fenPosition.find('a.board_editor_link').each(function (this: HTMLAnchorElement) {
              this.href = this.href.replace(/editor(\/.+)?$/, 'editor/' + fen);
            });
            toggleButtons();
            playstrategy.contentLoaded();
            save();
          },
          _ => {
            $fenInput.addClass('failure');
            $fenPosition.find('.preview').html('');
            toggleButtons();
          },
        );
      }
    }, 200);
    $fenInput.on('keyup', validateFen);
    if (forceFromPosition) {
      //force chess until support for other variants
      $variantInput.val('0_3');
      $gameGroupInput.val('0');
      $opponentInput.val('friend');
    }
    if (forceVariant && inputVariant) {
      $variantInput.val(inputVariant);
      $gameGroupInput.val(inputVariant.split('_')[0]);
    }
    $form.find('optgroup').each((_, optgroup: HTMLElement) => {
      optgroup.setAttribute('label', optgroup.getAttribute('name') || '');
    });
    $opponentInput.on('change', function (this: HTMLElement) {
      const opponent = $opponentInput.filter(':checked').val() as string;
      if (opponent === 'bot') {
        $botChoices.show();
        $form.find('.bot_title').show();
        $form.find('.rating-range-config').hide();
        updateBotDetails();
      } else if (opponent === 'lobby') {
        if (!user) $form.attr('action', $form.attr('action')?.replace(/&user=[^&]*/, ''));
        $botChoices.hide();
        $form.find('.bot_title').hide();
        $form.find('.rating-range-config').show();
      } else if (opponent === 'friend') {
        if (!user) $form.attr('action', $form.attr('action')?.replace(/&user=[^&]*/, ''));
        $botChoices.hide();
        $form.find('.bot_title').hide();
        $form.find('.rating-range-config').hide();
      }
      updateLobbySubmit();
      toggleButtons();
    });
    $botInput.on('change', function (this: HTMLElement) {
      updateBotDetails();
      toggleButtons();
    });

    $variantInput
      .on('change', function (this: HTMLElement) {
        const variantId = ($variantInput.filter(':checked').val() as string).split('_'),
          isFen = variantId[1] == '3';
        let ground = 'chessground';
        if (variantId[0] == '1') ground = 'draughtsground';
        ground += '.resize';
        if (variantId[0] == '9' || variantId[0] == '10') clearFenInput();
        $multiMatch.toggle(isFen && variantId[0] == '1');
        $fenPosition.toggle(isFen);
        $goConfig.toggle(variantId[0] == '9');
        // Set default Go komi and handicap for lobby-legal games
        if (variantId[0] == '9') {
          $goHandicapInput.val('0');
          $goHandicapInput.siblings('span').text('0');
          $goHandicapInput.siblings('.range').val('' + self.sliderInitVal(0, self.sliderHandicap, 26));
          if (variantId[1] == '1') {
            $goKomiInput.val('55'); // 5.5 for go9x9
          } else {
            $goKomiInput.val('75'); // 7.5 for go13x13 and go19x19
          }
        }
        //TODO change back when playing with friend is allowed for Backgammon multipoint
        $backgammonConfig.toggle(false);
        $backgammonPointsInput.val('1'); //remove along with above
        //$backgammonConfig.toggle(variantId[0] == '10');
        if (isFen) {
          $casual.trigger('click');
          $form.find('.mode_0.choice').show();
          $form.find('.mode_1.choice').hide();
          validateFen();
          requestAnimationFrame(() => document.body.dispatchEvent(new Event(ground)));
        }
        $timeModeDefaults.trigger('change');
        updateClockOptionsText();
        showStartingImages();
        if (variantId[0] == '9') setupGoKomiInput();
        toggleButtons();
      })
      .trigger('change');

    $playerIndexInput.on('change', function (this: HTMLElement) {
      toggleButtons();
    });

    $gameGroupInput
      .on('click', function (this: HTMLElement) {
        const variantId = ($variantInput.filter(':checked').val() as string).split('_'),
          gameFamily = $gameGroupInput.filter(':checked').val() as string;

        let numInGroup = 0;
        const toShow: HTMLElement[] = [];
        const toHide: HTMLElement[] = [];
        $variantInput.each(function (this: HTMLElement) {
          const gfOfVariant = ($(this).val() as string).split('_')[0];
          const additionMatches = gfOfVariant === '6' && gameFamily === '7'; //add oware to mancala group
          if (gfOfVariant === gameFamily || additionMatches) {
            toShow.push($(this).parent()[0]);
            numInGroup++;
          } else {
            toHide.push($(this).parent()[0]);
          }
        });
        $(toShow).show();
        $(toHide).hide();

        $variants
          .find('group.radio')
          .removeClass('child-count-1 child-count-2 child-count-3')
          .addClass('child-count-' + numInGroup);

        //select the default variant for each gameGroup
        if (variantId[0] !== gameFamily) {
          const variantValue = function () {
            switch (gameFamily) {
              case '2':
                return '2_11'; // Lines of Action
              case '4':
                return '4_2'; // Xiangqi
              case '5':
                return '5_6'; // Flipello
              case '7':
                return '6_1'; // Oware
              case '8':
                return '8_8'; // Amazons
              case '9':
                return '9_4'; // Go 19x19
              case '11':
                return '11_9'; // Breakthrough Troyka
              default:
                return `${gameFamily}_1`;
            }
          };
          $variantInput.filter(`[value="${variantValue()}"]`).trigger('click');
        }

        //Always close sections and open variants after game group selection
        $collapsibleSections
          .not($variants)
          .filter('.active')
          .each(function (this: HTMLDivElement) {
            squashSection(this);
          });
        $variants.addClass('active');
        $variants.find('group').removeClass('hide');
        $variants.find('div.choice').hide();

        toggleButtons();
      })
      .trigger('change');

    $modeChoices.on('change', () => {
      toggleButtons();
      save();
    });

    $advancedTimeToggle.on('click', function (this: HTMLElement) {
      if ($advancedTimeToggle.hasClass('active')) {
        $advancedTimeToggle.removeClass('active');
        $advancedTimeSetup.hide();
        resetIncSlider();
        resetPeriods();
        toggleButtons();
      } else {
        $advancedTimeToggle.addClass('active');
        $advancedTimeSetup.show();
      }
    });
    const squashSection = (collapsibleSection: HTMLDivElement) => {
      const sName = $(collapsibleSection).find('input').attr('name') || '';
      $(collapsibleSection).removeClass('active').find('group').addClass('hide');
      $(collapsibleSection)
        .find('div.choice')
        .hide()
        .filter(`.${sName}_` + $(collapsibleSection).find('input').filter(':checked').val())
        .show()
        .removeAttr('style'); //remove unwated display: block added by show()
      //hide/update additional sections
      $form.find('.time_mode_config').hide();
      updateClockOptionsText();
      $form.find('.bot_title').hide();
      $form.find('.rating-range-config').hide();
    };
    //do this last so that the form is ready for the user
    $collapsibleSections.each(function (this: HTMLDivElement) {
      const $this = $(this);
      const sName = $this.find('input').attr('name') || '';
      //initial display of form
      $this.removeClass('active');
      $this.find('group').addClass('hide');
      $this
        .find('div.choice')
        .hide()
        .filter(`.${sName}_` + $this.find('input').filter(':checked').val())
        .show();
      $form.find('.time_mode_config').hide();
      //Always start the form with gameGroup active unless forced setup
      if (forceVariant || forceFromPosition) {
        if (sName == 'timeModeDefaults') {
          $this.addClass('active');
          $this.find('group').removeClass('hide');
          $this.find('div.choice').hide();
        }
      } else if (sName == 'gameGroup') {
        $gameGroupInput.filter(':checked').trigger('click'); // to initalise variant list
        $this.addClass('active');
        $this.find('group').removeClass('hide');
        $this.find('div.choice').hide();
      }
      const fixedSection = (user && sName === 'opponent') || ($form.data('anon') && sName === 'mode');
      if (!fixedSection) {
        $this.on('click', function (this: HTMLElement) {
          const $this = $(this),
            sName = $this.find('input').attr('name') || '';
          $collapsibleSections
            .not(this)
            .filter('.active')
            .each(function (this: HTMLDivElement) {
              squashSection(this);
            });
          this.classList.toggle('active');
          $(this).find('group').toggleClass('hide');
          const $displayChoices = $this.find('div.choice');
          if (this.classList.contains('active')) {
            $displayChoices.hide();
            if (
              sName === 'timeModeDefaults' &&
              ($timeModeDefaults.find('input').filter(':checked').val() as string) === 'custom'
            ) {
              $form.find('.time_mode_config').show();
              $form.find('.time_mode_config').trigger('click');
            }
            if (sName === 'opponent') {
              if (($opponentInput.filter(':checked').val() as string) === 'bot') {
                $form.find('.bot_title').show();
                $botChoices.show();
              }
              if (($opponentInput.filter(':checked').val() as string) === 'lobby') {
                $form.find('.rating-range-config').show();
              }
            }
          } else {
            squashSection(this as HTMLDivElement);
          }
        });
      }
    });

    $collapsibleSections.find('input, label').on('click', function (e) {
      e.stopPropagation();
    });

    toggleButtons();
  };
}
