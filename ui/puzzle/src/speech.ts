export function setup(): void {
  playstrategy.pubsub.on('speech.enabled', onSpeechChange);
  onSpeechChange(playstrategy.sound.speech());
}

function onSpeechChange(enabled: boolean): void {
  if (!window.PlaystrategySpeech && enabled) playstrategy.loadModule('speech');
  else if (window.PlaystrategySpeech && !enabled) window.PlaystrategySpeech = undefined;
}

export function node(n: Tree.Node, cut: boolean): void {
  withSpeech(s => s.step(n, cut));
}

export function success(): void {
  withSpeech(s => s.say('Success!', false));
}

function withSpeech(f: (speech: PlaystrategySpeech) => void): void {
  if (window.PlaystrategySpeech) f(window.PlaystrategySpeech);
}
