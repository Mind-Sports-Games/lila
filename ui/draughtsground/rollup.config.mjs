import commonjs from '@rollup/plugin-commonjs';
import typescript from '@rollup/plugin-typescript';
import { terser } from 'rollup-plugin-terser';

export default {
  input: 'src/index.js',
  output: [
    {
      file: 'draughtsground.js',
      format: 'iife',
      name: 'Draughtsground',
    },
    {
      file: 'draughtsground.min.js',
      format: 'iife',
      name: 'Draughtsground',
      plugins: [
        terser({
          safari10: true,
        }),
      ],
    },
  ],
  plugins: [
    typescript({ tsconfig: './tsconfig.json' }),
    commonjs({
      extensions: ['.js', '.ts'],
    }),
  ],
};
