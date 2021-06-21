import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlayStrategyDgt',
    input: 'src/main.ts',
    output: 'dgt',
  },
});
