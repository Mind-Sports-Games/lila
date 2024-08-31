import * as game from 'game';
import * as xhr from './xhr';
import RoundController from './ctrl';

// TODO: I made these identical, but I'm not sure that is correct.
export default class MoveOn {
  private storage = playstrategy.storage.makeBoolean(this.key);

  constructor(
    private ctrl: RoundController,
    private key: string,
  ) {}

  toggle = () => {
    this.storage.toggle();
    this.next(true);
  };

  get = this.storage.get;

  private redirect = (href: string) => {
    this.ctrl.setRedirecting();
    window.location.href = href;
  };

  next = (force?: boolean): void => {
    const d = this.ctrl.data;
    if (d.simul) this.storage.set(true);
    if (d.player.spectator || !game.isSwitchable(d) || game.isPlayerTurn(d) || !this.get()) return;
    if (force) this.redirect('/round-next/' + d.game.id);
    else if (d.simul) {
      if (d.simul.hostId === this.ctrl.opts.userId && d.simul.nbPlaying > 1) this.redirect('/round-next/' + d.game.id);
    } else
      xhr.whatsNext(this.ctrl).then(data => {
        if (data.next) this.redirect('/' + data.next);
      });
  };
}
