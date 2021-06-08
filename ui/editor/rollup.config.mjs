import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlaystrategyEditor',
    input: 'src/main.ts',
    output: 'editor',
  },
});
