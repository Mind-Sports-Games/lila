import { loadCssPath } from './assets';

const chat = data =>
  new Promise(resolve =>
    requestAnimationFrame(() => {
      data.loadCss = loadCssPath;
      resolve(window.PlaystrategyChat(document.querySelector('.mchat'), data));
    })
  );

export default chat;
