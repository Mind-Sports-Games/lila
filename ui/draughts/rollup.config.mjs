import typescript from 'rollup-plugin-typescript2';

export default {
  input: 'src/main.ts',
  output: {
    file: 'main.js',
  },
  plugins: [
    typescript({
      tsconfig: './tsconfig.json',
    }),
  ],
};
