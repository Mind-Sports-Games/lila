export function setup() {
  playstrategy.pubsub.on('speech.enabled', onSpeechChange);
  onSpeechChange(playstrategy.sound.speech());
}

function onSpeechChange(enabled: boolean) {
  if (!window.PlaystrategySpeech && enabled) playstrategy.loadModule('speech');
  else if (window.PlaystrategySpeech && !enabled) window.PlaystrategySpeech = undefined;
}

export function node(n: Tree.Node) {
  withSpeech(s => s.step(n, true));
}

function withSpeech(f: (speech: PlaystrategySpeech) => void) {
  if (window.PlaystrategySpeech) f(window.PlaystrategySpeech);
}
