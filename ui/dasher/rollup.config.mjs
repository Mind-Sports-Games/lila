import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlaystrategyDasher',
    input: 'src/main.ts',
    output: 'dasher',
  },
});
