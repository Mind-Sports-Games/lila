import { boot } from './boot';
import info from './component/info';
import windowedPlaystrategy from './site.playstrategy.globals';

// window.playstrategy.load is initialized in layout.scala embedded script tags.

const l = windowedPlaystrategy();
l.info = info;

l.load.then(boot);
