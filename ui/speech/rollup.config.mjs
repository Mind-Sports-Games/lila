import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlayStrategySpeech',
    input: 'src/main.ts',
    output: 'speech',
  },
});
