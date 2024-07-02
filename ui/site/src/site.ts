import { boot } from './boot';
import info from './component/info';
import windowedPlaystrategy from './site.playstrategy.globals';

// window.site.{load, quantity, i18n} are initialized in layout.scala embedded script tags

const l = windowedPlaystrategy();
l.info = info;

l.load.then(boot);
