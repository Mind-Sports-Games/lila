import RoundController from './ctrl';
import viewStatus from 'game/view/status';
import { Step } from './interfaces';

export const setup = (ctrl: RoundController) => {
  playstrategy.pubsub.on('speech.enabled', onSpeechChange(ctrl));
  onSpeechChange(ctrl)(playstrategy.sound.speech());
};

const onSpeechChange = (ctrl: RoundController) => (enabled: boolean) => {
  if (!window.PlayStrategySpeech && enabled) playstrategy.loadModule('speech').then(() => status(ctrl));
  else if (window.PlayStrategySpeech && !enabled) window.PlayStrategySpeech = undefined;
};

export const status = (ctrl: RoundController) => {
  const s = viewStatus(ctrl);
  if (s == 'playingRightNow') window.PlayStrategySpeech!.step(ctrl.stepAt(ctrl.ply), false);
  else {
    withSpeech(speech => speech.say(s, false));
    const w = ctrl.data.game.winner;
    if (w) withSpeech(speech => speech.say(ctrl.noarg(w + 'IsVictorious'), false));
  }
};

export const userJump = (ctrl: RoundController, ply: Ply) => withSpeech(s => s.step(ctrl.stepAt(ply), true));

export const step = (step: Step) => withSpeech(s => s.step(step, false));

const withSpeech = (f: (speech: PlayStrategySpeech) => void) =>
  window.PlayStrategySpeech && f(window.PlayStrategySpeech);
