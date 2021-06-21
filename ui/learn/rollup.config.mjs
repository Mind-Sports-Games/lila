import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlayStrategyLearn',
    input: 'src/main.js',
    output: 'learn',
    js: true,
  },
});
