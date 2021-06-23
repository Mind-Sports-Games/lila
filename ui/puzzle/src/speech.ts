export function setup(): void {
  playstrategy.pubsub.on('speech.enabled', onSpeechChange);
  onSpeechChange(playstrategy.sound.speech());
}

function onSpeechChange(enabled: boolean): void {
  if (!window.PlayStrategySpeech && enabled) playstrategy.loadModule('speech');
  else if (window.PlayStrategySpeech && !enabled) window.PlayStrategySpeech = undefined;
}

export function node(n: Tree.Node, cut: boolean): void {
  withSpeech(s => s.step(n, cut));
}

export function success(): void {
  withSpeech(s => s.say('Success!', false));
}

function withSpeech(f: (speech: PlayStrategySpeech) => void): void {
  if (window.PlayStrategySpeech) f(window.PlayStrategySpeech);
}
