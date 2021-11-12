import { parseUCISquareToUSI } from '../src/notation';

test('testing e4 maps to 56', ()  => {
    expect(parseUCISquareToUSI('e4')).toBe('56');
});

test('testing a6 maps to 94', ()  => {
    expect(parseUCISquareToUSI('a6')).toBe('94');
});

test('testing i6 maps to 14', ()  => {
    expect(parseUCISquareToUSI('i6')).toBe('14');
});
