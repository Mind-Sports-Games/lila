import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlaystrategyTournament',
    input: 'src/main.ts',
    output: 'tournament',
  },
});
