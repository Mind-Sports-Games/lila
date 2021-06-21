import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlayStrategyInsight',
    input: 'src/main.js',
    output: 'insight',
    js: true,
  },
});
