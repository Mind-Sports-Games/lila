import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlayStrategyChat',
    input: 'src/main.ts',
    output: 'chat',
  },
});
