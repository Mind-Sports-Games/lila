import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlayStrategyRacer',
    input: 'src/main.ts',
    output: 'racer',
  },
});
