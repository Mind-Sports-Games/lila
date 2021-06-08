import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlaystrategyChallenge',
    input: 'src/main.ts',
    output: 'challenge',
  },
});
