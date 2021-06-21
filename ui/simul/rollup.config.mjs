import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlayStrategySimul',
    input: 'src/main.ts',
    output: 'simul',
  },
});
