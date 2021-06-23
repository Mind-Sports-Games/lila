import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlayStrategyMsg',
    input: 'src/main.ts',
    output: 'msg',
  },
});
