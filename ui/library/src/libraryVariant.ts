import { canUseBoardEditor } from 'common/editor';

playstrategy.load.then(() => {
  $('#library-section').each(function (this: HTMLElement) {
    const $editorLink = $('.library-links .library-editor');
    const $rulesLink = $('.library-links .library-rules');
    const variant = extractVariantKey($rulesLink);
    if (variant && !canUseBoardEditor(variant)) {
      $editorLink.remove();
    }
  });

  function extractVariantKey($rulesLink: Cash): VariantKey | undefined {
    if ($rulesLink.length) {
      const href = $rulesLink.attr('href') || '';
      const parts = href.split('/');
      return parts[parts.length - 1] as VariantKey;
    }
    return undefined;
  }
});
