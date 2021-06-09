import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlaystrategySimul',
    input: 'src/main.ts',
    output: 'simul',
  },
});
