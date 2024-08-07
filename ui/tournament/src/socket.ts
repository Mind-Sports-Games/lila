import TournamentController from './ctrl';

export interface TournamentSocket {
  send: SocketSend;
  receive(type: string, data: any): void;
}

export default function (send: SocketSend, ctrl: TournamentController) {
  const handlers: any = {
    reload() {
      setTimeout(ctrl.askReload, Math.floor(Math.random() * 4000));
    },
    redirect(fullId: string) {
      ctrl.redirectFirst(fullId.slice(0, 8), true);
      return true; // prevent default redirect
    },
    newVariant(variant: Variant) {
      ctrl.newVariant(variant);
      return true;
    },
  };

  return {
    send,
    receive(type: string, data: any) {
      if (handlers[type]) return handlers[type](data);
      return false;
    },
  };
}
