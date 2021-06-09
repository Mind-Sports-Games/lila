import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'PlaystrategyLobby',
    input: 'src/boot.ts',
    output: 'lobby',
  },
});
