import { FormStore, toFormLines, makeStore } from './form';
import modal from 'common/modal';
import debounce from 'common/debounce';
import * as xhr from 'common/xhr';
import LobbyController from './ctrl';

//TODO remove other form setup options
export default class Setup {
  stores: {
    hook: FormStore;
    friend: FormStore;
    ai: FormStore;
    game: FormStore;
  };

  constructor(
    readonly makeStorage: (name: string) => PlayStrategyStorage,
    readonly root: LobbyController,
  ) {
    this.stores = {
      hook: makeStore(makeStorage('lobby.setup.hook')),
      friend: makeStore(makeStorage('lobby.setup.friend')),
      ai: makeStore(makeStorage('lobby.setup.ai')),
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

  private sliderKomis = (bs: number) => [...Array(bs * bs * 4 + 1).keys()].map(i => -(bs * bs * 10) + i * 5);

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

  private psBots = ['ps-greedy-two-move', 'ps-greedy-one-move', 'ps-greedy-four-move', 'ps-random-mover'];
  private ratedTimeModes = ['1', '3', '4', '5'];

  prepareForm = ($modal: Cash) => {
    const self = this,
      $form = $modal.find('form'),
      $timeModeSelect = $form.find('#sf_timeMode'),
      $modeChoicesWrap = $form.find('.mode_choice'),
      $modeChoices = $modeChoicesWrap.find('input'),
      $casual = $modeChoices.eq(0),
      $rated = $modeChoices.eq(1),
      //$variantSelect = $form.find('#sf_variant'),
      $gameFamilies = $form.find('.gameFamily_choice'),
      $gameFamilyInput = $gameFamilies.find('.gameFamily_choice [name=gameFamily]'),
      $variants = $form.find('.variant_choice'),
      $variantInput = $variants.find('.variant_choice [name=variant]'), //todo not a select anymore....
      $fenPosition = $form.find('.fen_position'),
      $fenInput = $fenPosition.find('input'),
      forceFromPosition = !!$fenInput.val(),
      $multiMatch = $form.find('.multi_match'),
      $timeInput = $form.find('.time_choice [name=time]'),
      $incrementInput = $form.find('.increment_choice [name=increment]'),
      $byoyomiInput = $form.find('.byoyomi_choice [name=byoyomi]'),
      $periods = $form.find('.periods'),
      $periodsInput = $periods.find('.byoyomi_periods [name=periods]'),
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
      typ = $form.data('type'),
      $ratings = $modal.find('.ratings > div'),
      randomPlayerIndexVariants = $form.data('random-playerindex-variants').split(','),
      $submits = $form.find('.playerIndex-submits__button'),
      toggleButtons = () => {
        randomPlayerIndexVariants;
        const variantId = ($variantInput.filter(':checked').val() as string).split('_'),
          timeMode = <string>$timeModeSelect.val(),
          rated = $rated.prop('checked'),
          limit = parseFloat($timeInput.val() as string),
          inc = parseFloat($incrementInput.val() as string),
          byo = parseFloat($byoyomiInput.val() as string),
          per = parseFloat($periodsInput.filter(':checked').val() as string),
          // no rated variants with less than 30s on the clock and no rated unlimited in the lobby
          cantBeRated =
            (typ === 'hook' && timeMode === '0') ||
            this.ratedTimeModes.indexOf(timeMode) === -1 ||
            (limit < 0.5 && inc == 0) ||
            (limit == 0 && inc < 2) ||
            (vsPSBot && user == 'ps-random-mover') ||
            (variantId[0] == '9' &&
              $goConfig.val() !== undefined &&
              (($goHandicapInput.val() as string) != '0' ||
                (variantId[1] !== '1' && ($goKomiInput.val() as string) != '75') ||
                (variantId[1] == '1' && ($goKomiInput.val() as string) != '55'))) ||
            //remove this if we ever want Backgammon cube games to be rated
            (variantId[0] == '10' &&
              $backgammonConfig.val() !== undefined &&
              ($backgammonPointsInput.val() as string) != '1');
        if (cantBeRated && rated) {
          $casual.trigger('click');
          return toggleButtons();
        }
        $rated.prop('disabled', !!cantBeRated).siblings('label').toggleClass('disabled', cantBeRated);
        const byoOk = timeMode !== '3' || ((limit > 0 || inc > 0 || byo > 0) && (byo || per === 1));
        const delayOk = (timeMode !== '4' && timeMode !== '5') || inc > 0;
        const timeOk = timeMode !== '1' || limit > 0 || inc > 0,
          ratedOk = typ !== 'hook' || !rated || timeMode !== '0',
          aiOk = typ !== 'ai' || variantId[1] !== '3' || limit >= 1,
          fenOk = variantId[0] !== '0' || variantId[1] !== '3' || $fenInput.hasClass('success'),
          botOK = !vsPSBot || psBotCanPlay(user, limit, inc, variantId);
        if (byoOk && delayOk && timeOk && ratedOk && aiOk && fenOk && botOK) {
          $submits.toggleClass('nope', false);
          $submits.filter(':not(.random)').toggle(!rated || !randomPlayerIndexVariants.includes(variantId[1]));
        } else $submits.toggleClass('nope', true);
      },
      save = function () {
        self.save($form[0] as HTMLFormElement);
      };

    const psBotCanPlay = (user: string, limit: number, inc: number, variantId: string[]) => {
      //TODO remove hard coded options and improve bot api flow
      let variantCompatible = true;
      switch (user) {
        case 'ps-greedy-four-move': {
          //in (draughts, oware, togy)
          variantCompatible = ['1', '6', '7'].includes(variantId[0]);
          break;
        }
        default:
          variantCompatible = true;
      }

      let clockCompatible = true;
      if (isRealTime()) {
        switch (user) {
          case 'ps-random-mover': {
            clockCompatible = limit >= 0.5;
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
      } else {
        clockCompatible = true;
      }
      return variantCompatible && clockCompatible;
    };
    const clearFenInput = () => $fenInput.val('');
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
    //default options for playing against ps-bots
    if (vsPSBot) {
      $timeModeSelect.val('1');
      $timeInput.val('3');
      $incrementInput.val('2');
      $casual.trigger('click');
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
            case '1':
              key = 'breakthroughtroyka';
              break;
            case '2':
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
      $form.find('.playerIndex-submits').removeClass(class_list);
      $form.find('.playerIndex-submits').addClass(key);
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

    const isRealTime = () => this.ratedTimeModes.indexOf(<string>$timeModeSelect.val()) !== -1;

    if (typ === 'hook') {
      if ($form.data('anon')) {
        $timeModeSelect
          .val('1')
          .children('.timeMode_2, .timeMode_0')
          .prop('disabled', true)
          .attr('title', this.root.trans('youNeedAnAccountToDoThat'));
      }
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
              data.append('playerIndex', playerIndex);
              return data;
            })(),
          });
        }
        this.root.redraw();
        return false;
      };
      $submits
        .on('click', function (this: HTMLElement) {
          return ajaxSubmit($(this).val() as string);
        })
        .prop('disabled', false);
      $form.on('submit', () => ajaxSubmit('random'));
    } else
      $form.one('submit', () => {
        $submits.hide();
        $form.find('.playerIndex-submits').append(playstrategy.spinnerHtml);
      });
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
            $fenPosition.find('a.board_editor').each(function (this: HTMLAnchorElement) {
              this.href = this.href.replace(/editor\/.+$/, 'editor/' + fen);
            });
            toggleButtons();
            playstrategy.contentLoaded();
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
      switch (($variantInput.filter(':checked').val() as string).split('_')[0]) {
        case '0':
          $variantInput.val('0_3'); //TODO check this still works...
          break;
        case '1':
          $variantInput.val('1_3');
          break;
        //TODO: Add all variants from position?
      }
    }

    $form.find('optgroup').each((_, optgroup: HTMLElement) => {
      optgroup.setAttribute('label', optgroup.getAttribute('name') || '');
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
        $modeChoicesWrap.toggle(!isFen);
        $goConfig.toggle(variantId[0] == '9');
        //TODO change back when playing with friend is allowed for Backgammon multipoint
        $backgammonConfig.toggle(false);
        $backgammonPointsInput.val('1'); //remove along with above
        //$backgammonConfig.toggle(variantId[0] == '10');
        if (isFen) {
          $casual.trigger('click');
          validateFen();
          requestAnimationFrame(() => document.body.dispatchEvent(new Event(ground)));
        }
        showRating();
        showStartingImages();
        if (variantId[0] == '9') setupGoKomiInput();
        toggleButtons();
      })
      .trigger('change');

    $gameFamilyInput
      .on('change', function (this: HTMLElement) {
        const variantId = ($variantInput.filter(':checked').val() as string).split('_'),
          gameFamily = $gameFamilyInput.filter(':checked').val() as string;

        $variantInput.each(function (this: HTMLElement) {
          const gfOfVariant = ($(this).val() as string).split('_')[0];
          if (gfOfVariant === gameFamily) {
            $(this).parent().show();
          } else {
            $(this).parent().hide();
          }
        });

        if (variantId[0] !== gameFamily) {
          const variantValue = function () {
            switch (gameFamily) {
              case '2':
                return '2_11';
              case '4':
                return '4_2';
              case '5':
                return '5_6';
              case '8':
                return '8_8';
              case '9':
                return '9_4';
              case '11':
                return '11_9';
              default:
                return `${gameFamily}_1`;
            }
          };
          $variantInput.filter(`[value="${variantValue()}"]`).trigger('click');
        }
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

    // $form.find('div.level').each(function (this: HTMLElement) {
    //   const $infos = $(this).find('.ai_info > div');
    //   $(this)
    //     .find('label')
    //     .on('mouseenter', function (this: HTMLElement) {
    //       $infos
    //         .hide()
    //         .filter('.' + $(this).attr('for'))
    //         .show();
    //     });
    //   $(this)
    //     .find('#config_level')
    //     .on('mouseleave', function (this: HTMLElement) {
    //       const level = $(this).find('input:checked').val();
    //       $infos
    //         .hide()
    //         .filter('.sf_level_' + level)
    //         .show();
    //     })
    //     .trigger('mouseout');
    //   $(this).find('input').on('change', save);
    // });
  };
}
