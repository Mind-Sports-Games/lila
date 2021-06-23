import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlayStrategyTournament',
    input: 'src/main.ts',
    output: 'tournament',
  },
});
