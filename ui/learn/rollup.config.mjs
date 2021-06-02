import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlaystrategyLearn',
    input: 'src/main.js',
    output: 'learn',
    js: true,
  },
});
