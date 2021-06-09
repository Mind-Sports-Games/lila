import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlaystrategyMsg',
    input: 'src/main.ts',
    output: 'msg',
  },
});
