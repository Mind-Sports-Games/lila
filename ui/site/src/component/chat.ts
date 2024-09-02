import { loadHashedCssPath } from './assets';

const chat = (data: any) =>
  new Promise(resolve =>
    requestAnimationFrame(() => {
      data.loadCss = loadHashedCssPath;
      resolve(window.PlayStrategyChat(document.querySelector('.mchat'), data));
    }),
  );

export default chat;
