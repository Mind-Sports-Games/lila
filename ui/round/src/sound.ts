import throttle from 'common/throttle';

export const throttled = (sound: string) => throttle(100, () => playstrategy.sound.play(sound));

export const move = throttled('move');
export const capture = throttled('capture');
export const check = throttled('check');
export const explode = throttled('explosion');
export const diceroll = throttled('diceRoll');
export const dicepickup = throttled('dicePickUp');
