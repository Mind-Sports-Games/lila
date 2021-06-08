import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlaystrategyNotify',
    input: 'src/main.ts',
    output: 'notify',
  },
});
