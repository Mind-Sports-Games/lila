import StrongSocket from './component/socket';
import { requestIdleCallback, escapeHtml } from './component/functions';
import makeChat from './component/chat';
import once from './component/once';
import spinnerHtml from './component/spinner';
import sri from './component/sri';
import { storage, tempStorage } from './component/storage';
import powertip from './component/powertip';
import clockWidget from './component/clock-widget';
import {
  assetUrl,
  loadCss,
  loadHashedCss,
  loadHashedCssPath,
  jsModule,
  loadScript,
  loadScriptCJS,
  hopscotch,
  userComplete,
  loadModule,
} from './component/assets';
import widget from './component/widget';
import idleTimer from './component/idle-timer';
import pubsub from './component/pubsub';
import { unload, redirect, reload } from './component/reload';
import announce from './component/announce';
import trans from './component/trans';
import sound from './component/sound';
import * as miniBoard from 'common/mini-board';
import * as miniGame from './component/mini-game';
import { format as timeago } from './component/timeago';
import watchers from './component/watchers';

window.$as = <T>(cashOrHtml: Cash | string) => (typeof cashOrHtml === 'string' ? $(cashOrHtml) : cashOrHtml)[0] as T;

export default function windowedPlaystrategy(): PlayStrategy {
  const l = window.playstrategy;
  l.StrongSocket = StrongSocket;
  l.requestIdleCallback = requestIdleCallback;
  l.sri = sri;
  l.storage = storage;
  l.tempStorage = tempStorage;
  l.once = once;
  l.powertip = powertip;
  l.clockWidget = clockWidget;
  l.widget = widget;
  l.spinnerHtml = spinnerHtml;
  l.assetUrl = assetUrl;
  l.loadCss = loadCss;
  l.loadHashedCss = loadHashedCss;
  l.loadHashedCssPath = loadHashedCssPath;
  l.jsModule = jsModule;
  l.loadScript = loadScript;
  l.loadScriptCJS = loadScriptCJS;
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

  return l;
}
