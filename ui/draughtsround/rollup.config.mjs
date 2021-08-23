import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlayStrategyDraughtsRound',
    input: 'src/main.ts',
    output: 'draughtsround',
  },
  keyboardMove: {
    name: 'KeyboardMove',
    input: 'src/plugins/keyboardMove.ts',
    output: 'draughtsround.keyboardMove',
  },
  nvui: {
    name: 'NVUI',
    input: 'src/plugins/nvui.ts',
    output: 'draughtsround.nvui',
  },
});
