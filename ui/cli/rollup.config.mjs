import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlaystrategyCli',
    input: 'src/main.ts',
    output: 'cli',
  },
});
