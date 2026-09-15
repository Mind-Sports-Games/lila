import { json, text as xhrText, form } from 'common/xhr';

export const userModInfo = (username: string) => json('/mod/chat-user/' + username);

export const flag = (resource: string, username: string, text: string) =>
  xhrText('/report/flag', {
    method: 'post',
    body: form({ username, resource, text }),
  });

export const getNote = (id: string) => xhrText(noteUrl(id));

export const setNote = (id: string, text: string) =>
  xhrText(noteUrl(id), {
    method: 'post',
    body: form({ text }),
  });

const noteUrl = (id: string) => `/${id}/note`;
