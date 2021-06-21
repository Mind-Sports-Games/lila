import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlayStrategyLobby',
    input: 'src/boot.ts',
    output: 'lobby',
  },
});
