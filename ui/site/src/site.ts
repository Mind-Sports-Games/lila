import { boot } from './boot';
import windowedPlaystrategy from './site.playstrategy.globals';

console.log('b4 loading...');

// window.site.{load, quantity, i18n} are initialized in layout.scala embedded script tags

const l = windowedPlaystrategy();

l.load.then(boot);
