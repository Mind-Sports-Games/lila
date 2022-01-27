import sanWriter, { SanToUci } from './sanWriter';
import { DecodedDests } from '../interfaces';
import { KeyboardMove } from '../keyboardMove';
import { Key } from 'draughtsground/types';

const keyRegex = /^\d{1,2}$/;

type Sans = {
  [key: string]: Uci;
};

interface Opts {
  input: HTMLInputElement;
  ctrl: KeyboardMove;
}
interface SubmitOpts {
  isTrusted: boolean;
  force?: boolean;
  yourMove?: boolean;
}
type Submit = (v: string, submitOpts: SubmitOpts) => void;

playstrategy.keyboardMove = function (opts: Opts) {
  if (opts.input.classList.contains('ready')) return;
  opts.input.classList.add('ready');
  let legalSans: SanToUci | null = null;

  const isKey = (v: string): v is Key => !!v.match(keyRegex);

  const submit: Submit = function (v: string, submitOpts: SubmitOpts) {
    if (!submitOpts.isTrusted) return;
    const foundUci = v.length >= 3 && legalSans && sanToUci(v, legalSans);
    if (foundUci) {
      opts.ctrl.san(foundUci.slice(0, 2) as Key, foundUci.slice(2) as Key);
      clear();
    } else if (legalSans && isKey(v)) {
      if (submitOpts.force) {
        opts.ctrl.select((v.length === 1 ? '0' + v : v) as Key);
        clear();
      } else opts.input.classList.remove('wrong');
    } else if (v.length > 0 && 'clock'.startsWith(v.toLowerCase())) {
      if ('clock' === v.toLowerCase()) {
        readClocks(opts.ctrl.clock());
        clear();
      }
    } else if (submitOpts.yourMove && v.length > 1) {
      setTimeout(() => playstrategy.sound.play('error'), 500);
      opts.input.value = '';
    } else {
      const wrong = v.length && legalSans && !sanCandidates(v, legalSans).length;
      if (wrong && !opts.input.classList.contains('wrong')) playstrategy.sound.play('error');
      opts.input.classList.toggle('wrong', !!wrong);
    }
  };
  const clear = () => {
    opts.input.value = '';
    opts.input.classList.remove('wrong');
  };
  makeBindings(opts, submit, clear);
  return function (fen: string, dests: DecodedDests, captLen?: number) {
    legalSans = dests && Object.keys(dests).length ? sanWriter(fen, destsToUcis(dests), captLen) : null;
    submit(opts.input.value, {
      isTrusted: true,
      // TODO: unsure if yourMove is needed here or not.s
    });
  };
};

function makeBindings(opts: any, submit: Submit, clear: () => void) {
  window.Mousetrap.bind('enter', () => opts.input.focus());
  /* keypress doesn't cut it here;
   * at the time it fires, the last typed char
   * is not available yet. Reported by:
   * https://playstrategy.org/forum/playstrategy-feedback/keyboard-input-changed-today-maybe-a-bug
   */
  opts.input.addEventListener('keyup', (e: KeyboardEvent) => {
    if (!e.isTrusted) return;
    const v = (e.target as HTMLInputElement).value;
    if (v.includes('/')) {
      focusChat();
      clear();
    } else if (v === '' && e.which == 13) opts.ctrl.confirmMove();
    else
      submit(v, {
        force: e.which === 13,
        isTrusted: e.isTrusted,
      });
  });
  opts.input.addEventListener('focus', () => opts.ctrl.setFocus(true));
  opts.input.addEventListener('blur', () => opts.ctrl.setFocus(false));
  // prevent default on arrow keys: they only replay moves
  opts.input.addEventListener('keydown', (e: KeyboardEvent) => {
    if (e.which > 36 && e.which < 41) {
      if (e.which == 37) opts.ctrl.jump(-1);
      else if (e.which == 38) opts.ctrl.jump(-999);
      else if (e.which == 39) opts.ctrl.jump(1);
      else opts.ctrl.jump(999);
      e.preventDefault();
    }
  });
}

function sanToUci(san: string, sans: Sans): string | undefined {
  if (san in sans) return sans[san];
  if (san.length === 4 && Object.keys(sans).find(key => sans[key] === san)) return san;
  let lowered = san.toLowerCase().replace('x0', 'x').replace('-0', '-');
  if (lowered.startsWith('0')) lowered = lowered.slice(1);
  if (lowered in sans) return sans[lowered];
  return undefined;
}

function sanCandidates(san: string, sans: Sans) {
  const lowered = san.toLowerCase();
  let cleanLowered = lowered.replace('x0', 'x').replace('-0', '-');
  if (cleanLowered.startsWith('0')) cleanLowered = cleanLowered.slice(1);
  const filterKeys = Object.keys(sans).filter(function (s) {
    const sLowered = s.toLowerCase();
    return sLowered.startsWith(lowered) || sLowered.startsWith(cleanLowered);
  });
  return filterKeys.length
    ? filterKeys
    : Object.keys(sans)
        .map(key => sans[key])
        .filter(function (s) {
          return s.startsWith(lowered);
        });
}

function destsToUcis(dests: DecodedDests): Uci[] {
  const ucis: string[] = [];
  for (const [orig, d] of dests) {
    d.forEach(function (dest) {
      ucis.push(orig + dest);
    });
  }
  return ucis;
}

function focusChat() {
  const chatInput = document.querySelector('.mchat .mchat__say') as HTMLInputElement;
  if (chatInput) chatInput.focus();
}

function readClocks(clockCtrl: any | undefined) {
  if (!clockCtrl) return;
  const msgs = ['p1', 'p2'].map(playerIndex => {
    const time = clockCtrl.millisOf(playerIndex);
    const date = new Date(time);
    const msg =
      (time >= 3600000 ? simplePlural(Math.floor(time / 3600000), 'hour') : '') +
      ' ' +
      simplePlural(date.getUTCMinutes(), 'minute') +
      ' ' +
      simplePlural(date.getUTCSeconds(), 'second');
    return `${playerIndex}: ${msg}`;
  });
  playstrategy.sound.say(msgs.join('. '));
}

function simplePlural(nb: number, word: string) {
  return `${nb} ${word}${nb != 1 ? 's' : ''}`;
}
