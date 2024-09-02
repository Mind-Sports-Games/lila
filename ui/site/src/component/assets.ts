import * as xhr from 'common/xhr';

/*
loads assets on the fly

The build system uses a manifest file to maintain the hash of the content for our assets (in ui) :
- scss files
- ts files (entrypoints are then importing common.<hash>.js files)
- playstrategy woff(2)
- a few external js libs (cash, powertip, howler, mousetrap)

Some other assets are not bundled by our build system, therefore an asset_version in the URL is needed to invalidate the cache.
*/

export const assetUrl = (path: string, opts: AssetUrlOpts = {}) => {
  opts = opts || {};
  const baseUrl = opts.sameDomain ? '' : document.body.getAttribute('data-asset-url'),
    version = opts.version || document.body.getAttribute('data-asset-version');
  return baseUrl + '/assets' + (opts.noVersion ? '' : '/_' + version) + '/' + path;
};
export const hashedAssetUrl = (path: string, opts: AssetUrlOpts = {}) => {
  opts = opts || {};
  const baseUrl = opts.sameDomain ? '' : document.body.getAttribute('data-asset-url');
  return baseUrl + '/assets' + '/' + path;
};

const loadedCss = new Map<string, true>();

// still used to load external libs css (hopscotch, shepherd)
export const loadCss = (url: string) => {
  if (!loadedCss.has(url)) {
    loadedCss.set(url, true);
    const el = document.createElement('link');
    el.rel = 'stylesheet';
    el.href = assetUrl(url);
    document.head.append(el);
  }
};
export const loadHashedCss = (url: string) => {
  if (!loadedCss.has(url)) {
    loadedCss.set(url, true);
    const el = document.createElement('link');
    el.rel = 'stylesheet';
    el.href = hashedAssetUrl(url);
    document.head.append(el);
  }
};
export const loadHashedCssPath = (key: string) => {
  const theme = $('body').data('theme');
  const hashKey = key + '.' + theme;
  const hash = playstrategy.manifest.css[hashKey];

  loadHashedCss(`css/${key}.${theme}.${hash}.css`);
};

export const jsModule = (name: string, path: string = 'compiled/') => {
  if (name.endsWith('.js')) name = name.slice(0, -3);
  const hash = playstrategy.manifest.js[name];
  return `${path}${name}${hash ? `.${hash}` : ''}.js`;
};

const loadedScript = new Map<string, Promise<void>>();

export const loadScript = (url: string, opts: AssetUrlOpts = {}): Promise<void> => {
  if (!loadedScript.has(url)) loadedScript.set(url, xhr.script(assetUrl(url, opts)));
  return loadedScript.get(url)!;
};
export const loadScriptCJS = (url: string, opts: AssetUrlOpts = {}): Promise<void> => {
  if (!loadedScript.has(url)) loadedScript.set(url, xhr.scriptCJS(assetUrl(url, opts)));
  return loadedScript.get(url)!;
};
const loadHashedScript = (url: string, opts: AssetUrlOpts = {}): Promise<void> => {
  if (!loadedScript.has(url)) loadedScript.set(url, xhr.script(hashedAssetUrl(url, opts)));
  return loadedScript.get(url)!;
};

export const loadModule = (name: string): Promise<void> => loadHashedScript(jsModule(name));

export const userComplete = (): Promise<UserComplete> => {
  loadHashedCssPath('complete');
  return loadModule('userComplete').then(_ => window.UserComplete);
};

export const hopscotch = () => {
  loadCss('vendor/hopscotch/dist/css/hopscotch.min.css');
  return loadScriptCJS('vendor/hopscotch/dist/js/hopscotch.min.js', {
    noVersion: true,
  });
};
