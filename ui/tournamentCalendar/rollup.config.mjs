import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlayStrategyTournamentCalendar',
    input: 'src/main.ts',
    output: 'tournament.calendar',
  },
});
