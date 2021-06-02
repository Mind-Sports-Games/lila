import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlaystrategyDgt',
    input: 'src/main.ts',
    output: 'dgt',
  },
});
