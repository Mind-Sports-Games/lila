import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlayStrategyChallenge',
    input: 'src/main.ts',
    output: 'challenge',
  },
});
