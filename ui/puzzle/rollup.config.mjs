import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlayStrategyPuzzle',
    input: 'src/main.ts',
    output: 'puzzle',
  },
  dashboard: {
    name: 'PlayStrategyPuzzleDashboard',
    input: 'src/dashboard.ts',
    output: 'puzzle.dashboard',
  },
});
