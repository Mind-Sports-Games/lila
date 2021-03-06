import { loadCssPath } from './assets';

const chat = data =>
  new Promise(resolve =>
    requestAnimationFrame(() => {
      data.loadCss = loadCssPath;
      resolve(window.PlayStrategyChat(document.querySelector('.mchat'), data));
    })
  );

export default chat;
