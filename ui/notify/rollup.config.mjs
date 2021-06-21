import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlayStrategyNotify',
    input: 'src/main.ts',
    output: 'notify',
  },
});
