import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlaystrategyPuzzle',
    input: 'src/main.ts',
    output: 'puzzle',
  },
  dashboard: {
    name: 'PlaystrategyPuzzleDashboard',
    input: 'src/dashboard.ts',
    output: 'puzzle.dashboard',
  },
});
