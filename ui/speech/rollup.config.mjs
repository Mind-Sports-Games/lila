import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlaystrategySpeech',
    input: 'src/main.ts',
    output: 'speech',
  },
});
