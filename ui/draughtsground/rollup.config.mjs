import commonjs from '@rollup/plugin-commonjs';
import typescript from 'rollup-plugin-typescript2';
import { terser } from 'rollup-plugin-terser';

export default [
  {
    input: 'src/index.js',
    output: [
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
      typescript({ declaration: false }),
      commonjs({
        extensions: ['.js', '.ts'],
      }),
    ],
  },
  {
    input: 'src/draughtsground.ts',
    output: [
      {
        dir: '.',
        format: 'cjs'
      },
    ],
    plugins: [
      typescript({
        tsconfig: './tsconfig.json',
      }),
    ],
  },
];

