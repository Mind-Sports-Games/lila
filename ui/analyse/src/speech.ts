export function setup() {
  playstrategy.pubsub.on('speech.enabled', onSpeechChange);
  onSpeechChange(playstrategy.sound.speech());
}

function onSpeechChange(enabled: boolean) {
  if (!window.PlayStrategySpeech && enabled) playstrategy.loadModule('speech');
  else if (window.PlayStrategySpeech && !enabled) window.PlayStrategySpeech = undefined;
}

export function node(n: Tree.Node) {
  withSpeech(s => s.step(n, true));
}

function withSpeech(f: (speech: PlayStrategySpeech) => void) {
  if (window.PlayStrategySpeech) f(window.PlayStrategySpeech);
}
