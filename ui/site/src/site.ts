import * as miniBoard from 'common/mini-board';
import * as miniGame from './component/mini-game';
import { format as timeago } from './component/timeago';
import announce from './component/announce';
import powertip from './component/powertip';
import pubsub from './component/pubsub';
import StrongSocket from './component/socket';
import watchers from './component/watchers';
import { escapeHtml, requestIdleCallback } from './component/functions';
import makeChat from './component/chat';
import once from './component/once';
import spinnerHtml from './component/spinner';
import sri from './component/sri';
import { storage, tempStorage } from './component/storage';
import {
  assetUrl,
  loadCss,
  loadCssPath,
  jsModule,
  loadScript,
  loadScriptNotAsModule,
  hopscotch,
  loadModule,
  userComplete,
} from './component/assets';
import widget from './component/widget';
import idleTimer from './component/idle-timer';
import { unload, reload, redirect } from './component/reload';
import trans from './component/trans';
import sound from './component/sound';

import { boot } from './boot';

console.log('b4 loading...');

// window.site.{load, quantity, i18n} are initialized in layout.scala embedded script tags

window.$as = <T>(cashOrHtml: Cash | string) => (typeof cashOrHtml === 'string' ? $(cashOrHtml) : cashOrHtml)[0] as T;
console.log("i'm here...");
const l = window.playstrategy;
l.StrongSocket = StrongSocket;
l.requestIdleCallback = requestIdleCallback;
l.sri = sri;
l.storage = storage;
l.tempStorage = tempStorage;
l.once = once;
l.powertip = powertip;
l.widget = widget;
l.spinnerHtml = spinnerHtml;
l.assetUrl = assetUrl;
l.loadCss = loadCss;
l.loadCssPath = loadCssPath;
l.jsModule = jsModule;
l.loadScript = loadScript;
l.loadScriptNotAsModule = loadScriptNotAsModule;
l.loadModule = loadModule;
l.hopscotch = hopscotch;
l.userComplete = userComplete;
l.makeChat = makeChat;
l.idleTimer = idleTimer;
l.pubsub = pubsub;
l.unload = unload;
l.redirect = redirect;
l.reload = reload;
l.watchers = watchers;
l.escapeHtml = escapeHtml;
l.announce = announce;
l.trans = trans;
l.sound = sound;
l.miniBoard = miniBoard;
l.miniGame = miniGame;
l.timeago = timeago;
l.contentLoaded = (parent?: HTMLElement) => pubsub.emit('content-loaded', parent);
l.pageVariant = undefined;

l.load.then(boot);
