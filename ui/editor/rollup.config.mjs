import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlayStrategyEditor',
    input: 'src/main.ts',
    output: 'editor',
  },
});
