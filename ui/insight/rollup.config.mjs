import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlaystrategyInsight',
    input: 'src/main.js',
    output: 'insight',
    js: true,
  },
});
