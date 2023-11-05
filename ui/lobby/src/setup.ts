import { FormStore, toFormLines, makeStore } from './form';
import modal from 'common/modal';
import debounce from 'common/debounce';
import * as xhr from 'common/xhr';
import LobbyController from './ctrl';

export default class Setup {
  stores: {
    hook: FormStore;
    friend: FormStore;
    ai: FormStore;
  };

  constructor(readonly makeStorage: (name: string) => PlayStrategyStorage, readonly root: LobbyController) {
    this.stores = {
      hook: makeStore(makeStorage('lobby.setup.hook')),
      friend: makeStore(makeStorage('lobby.setup.friend')),
      ai: makeStore(makeStorage('lobby.setup.ai')),
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

  private sliderKomis = [...Array(41).keys()].map(i => -100 + i * 5);

  private sliderKomi = (v: number) => (v < this.sliderKomis.length ? this.sliderKomis[v] : 75);

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

  private ratedTimeModes = ['1', '3', '4', '5'];

  prepareForm = ($modal: Cash) => {
    let fenOk = false;
    const self = this,
      $form = $modal.find('form'),
      $timeModeSelect = $form.find('#sf_timeMode'),
      $modeChoicesWrap = $form.find('.mode_choice'),
      $modeChoices = $modeChoicesWrap.find('input'),
      $casual = $modeChoices.eq(0),
      $rated = $modeChoices.eq(1),
      $variantSelect = $form.find('#sf_variant'),
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
      $advancedTimeSetup = $form.find('.advanced_setup'),
      $advancedTimeToggle = $form.find('.advanced_toggle'),
      $daysInput = $form.find('.days_choice [name=days]'),
      typ = $form.data('type'),
      $ratings = $modal.find('.ratings > div'),
      randomPlayerIndexVariants = $form.data('random-playerindex-variants').split(','),
      $submits = $form.find('.playerIndex-submits__button'),
      toggleButtons = () => {
        randomPlayerIndexVariants;
        const variantId = ($variantSelect.val() as string).split('_'),
          timeMode = <string>$timeModeSelect.val(),
          rated = $rated.prop('checked'),
          limit = parseFloat($timeInput.val() as string),
          inc = parseFloat($incrementInput.val() as string),
          byo = parseFloat($byoyomiInput.val() as string),
          per = parseFloat($periodsInput.filter(':checked').val() as string),
          // no rated variants with less than 30s on the clock and no rated unlimited in the lobby
          cantBeRated =
            (typ === 'hook' && timeMode === '0') ||
            (this.ratedTimeModes.indexOf(timeMode) != -1) ||
            (limit < 0.5 && inc == 0) ||
            (limit == 0 && inc < 2) ||
            (variantId[0] == '9' &&
              $goConfig.val() !== undefined &&
              (($goHandicapInput.val() as string) != '0' || ($goKomiInput.val() as string) != '75'));
        if (cantBeRated && rated) {
          $casual.trigger('click');
          return toggleButtons();
        }
        $rated.prop('disabled', !!cantBeRated).siblings('label').toggleClass('disabled', cantBeRated);
        const byoOk = timeMode !== '3' || ((limit > 0 || inc > 0 || byo > 0) && (byo || per === 1));
        const timeOk = timeMode !== '1' || limit > 0 || inc > 0,
          ratedOk = typ !== 'hook' || !rated || timeMode !== '0',
          aiOk = typ !== 'ai' || variantId[1] !== '3' || limit >= 1,
          posOk = variantId[0] !== '0' || variantId[1] !== '3' || fenOk;
        if (byoOk && timeOk && ratedOk && aiOk && posOk) {
          $submits.toggleClass('nope', false);
          $submits.filter(':not(.random)').toggle(!rated || !randomPlayerIndexVariants.includes(variantId[1]));
        } else $submits.toggleClass('nope', true);
      },
      save = function() {
        self.save($form[0] as HTMLFormElement);
      };

    const clearFenInput = () => $fenInput.val('');
    const c = this.stores[typ].get();
    if (c) {
      Object.keys(c).forEach(k => {
        $form.find(`[name="${k}"]`).each(function(this: HTMLInputElement) {
          if (k === 'timeMode' && this.value !== '1') return;
          if (this.type == 'checkbox') this.checked = true;
          else if (this.type == 'radio') this.checked = this.value == c[k];
          else if (k != 'fen' || !this.value) this.value = c[k];
        });
      });
    }

    const showRating = () => {
      const variantId = ($variantSelect.val() as string).split('_'),
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
        case '6':
          key = 'oware';
          break;
        case '7':
          key = 'togyzkumalak';
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
      }
      const $selected = $ratings
        .hide()
        .filter('.' + key)
        .show();
      $modal.find('.ratings input').val($selected.find('strong').text());
      save();
    };
    const showStartingImages = () => {
      const variantId = ($variantSelect.val() as string).split('_');
      const class_list = 'chess draughts loa shogi xiangqi flipello oware togyzkumalak amazons go';
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

    const isRealTime = () => this.ratedTimeModes.indexOf(<string>$timeModeSelect.val()) !== -1

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
        .on('click', function(this: HTMLElement) {
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
      $variantSelect[0]!.focus();
      $timeInput.add($incrementInput).on('change', () => {
        toggleButtons();
        showRating();
      });
    } else {
      $timeInput.add($incrementInput).each(function(this: HTMLInputElement) {
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
              100
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
      $daysInput.each(function(this: HTMLInputElement) {
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
      $byoyomiInput.each(function(this: HTMLInputElement) {
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
      $goHandicapInput.each(function(this: HTMLInputElement) {
        const $input = $(this),
          $value = $input.siblings('span'),
          $range = $input.siblings('.range');
        $value.text($input.val() as string);
        $range.attr({
          min: '0',
          max: '9',
          value: '' + self.sliderInitVal(parseInt($input.val() as string), self.sliderIncrement, 10),
        });
        $range.on('input', () => {
          const goHandicap = self.sliderIncrement(parseInt($range.val() as string));
          $value.text('' + goHandicap);
          $input.val('' + goHandicap);
          save();
          clearFenInput();
          toggleButtons();
        });
      });
      $goKomiInput.each(function(this: HTMLInputElement) {
        const $input = $(this),
          $value = $input.siblings('span'),
          $range = $input.siblings('.range'),
          showKomi = (v: number) => {
            return ('' + v / 10.0).replace('.0', '');
          },
          show = (komi: number) => $value.text(showKomi(komi));
        show(parseInt($input.val() as string));
        $range.attr({
          min: '0',
          max: '40',
          value: '' + self.sliderInitVal(parseInt($input.val() as string), self.sliderKomi, 40),
        });
        $range.on('input', () => {
          const goKomi = self.sliderKomi(parseInt($range.val() as string));
          show(goKomi);
          $input.val('' + goKomi);
          save();
          clearFenInput();
          toggleButtons();
        });
      });
      $form.find('.rating-range').each(function(this: HTMLDivElement) {
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
    }
    $timeModeSelect
      .on('change', function(this: HTMLElement) {
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
      const variantId = ($variantSelect.val() as string).split('_');
      const lib = variantId[0];
      if (fen) {
        const [path, params] = $fenInput.parent().data('validate-url').split('?'); // Separate "strict=1" for AI match
        xhr.text(xhr.url(path, { lib, fen }) + (params ? `&${params}` : '')).then(
          data => {
            $fenInput.addClass('success');
            $fenPosition.find('.preview').html(data);
            $fenPosition.find('a.board_editor').each(function(this: HTMLAnchorElement) {
              this.href = this.href.replace(/editor\/.+$/, 'editor/' + fen);
            });
            $submits.removeClass('nope');
            fenOk = true;
            playstrategy.contentLoaded();
          },
          _ => {
            $fenInput.addClass('failure');
            $fenPosition.find('.preview').html('');
            $submits.addClass('nope');
            fenOk = false;
          }
        );
      }
    }, 200);
    $fenInput.on('keyup', validateFen);
    validateFen();

    if (forceFromPosition) {
      switch (($variantSelect.val() as string).split('_')[0]) {
        case '0':
          $variantSelect.val('0_3');
          break;
        case '1':
          $variantSelect.val('1_3');
          break;
        //TODO: Add all variants from position?
      }
    }

    $form.find('optgroup').each((_, optgroup: HTMLElement) => {
      optgroup.setAttribute('label', optgroup.getAttribute('name') || '');
    });

    $variantSelect
      .on('change', function(this: HTMLElement) {
        const variantId = ($variantSelect.val() as string).split('_'),
          isFen = variantId[1] == '3';
        let ground = 'chessground';
        if (variantId[0] == '1') ground = 'draughtsground';
        ground += '.resize';
        if (variantId[0] == '9') clearFenInput();
        $multiMatch.toggle(isFen && variantId[0] == '1');
        $fenPosition.toggle(isFen);
        $modeChoicesWrap.toggle(!isFen);
        $goConfig.toggle(variantId[0] == '9');
        if (isFen) {
          $casual.trigger('click');
          requestAnimationFrame(() => document.body.dispatchEvent(new Event(ground)));
        }
        showRating();
        showStartingImages();
        toggleButtons();
      })
      .trigger('change');

    $modeChoices.on('change', save);

    $advancedTimeToggle.on('click', function(this: HTMLElement) {
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

    $form.find('div.level').each(function(this: HTMLElement) {
      const $infos = $(this).find('.ai_info > div');
      $(this)
        .find('label')
        .on('mouseenter', function(this: HTMLElement) {
          $infos
            .hide()
            .filter('.' + $(this).attr('for'))
            .show();
        });
      $(this)
        .find('#config_level')
        .on('mouseleave', function(this: HTMLElement) {
          const level = $(this).find('input:checked').val();
          $infos
            .hide()
            .filter('.sf_level_' + level)
            .show();
        })
        .trigger('mouseout');
      $(this).find('input').on('change', save);
    });
  };
}
