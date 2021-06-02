import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlaystrategySwiss',
    input: 'src/main.ts',
    output: 'swiss',
  },
});
