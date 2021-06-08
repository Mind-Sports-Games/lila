var util = require('./util');

const make = (file, volume) => {
  playstrategy.sound.loadOggOrMp3(file, `${playstrategy.sound.baseUrl}/${file}`);
  return () => playstrategy.sound.play(file, volume);
};

module.exports = {
  move: () => playstrategy.sound.play('move'),
  take: make('sfx/Tournament3rd', 0.4),
  levelStart: make('other/ping'),
  levelEnd: make('other/energy3'),
  stageStart: make('other/guitar'),
  stageEnd: make('other/gewonnen'),
  failure: make('other/no-go'),
};
