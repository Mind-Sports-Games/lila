import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlaystrategyRacer',
    input: 'src/main.ts',
    output: 'racer',
  },
});
