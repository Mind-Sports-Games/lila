import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlayStrategyStorm',
    input: 'src/main.ts',
    output: 'storm',
  },
});
