import trans from './trans';
import pubsub from './pubsub';
import { notNull } from 'common/common';

type TitleName = string;

interface Friend {
  id: string;
  name: string;
  title?: string;
  playing: boolean;
  patron: boolean;
}

export default class OnlineFriends {
  titleEl: HTMLElement;
  countEl: HTMLElement;
  badgeEl: HTMLElement;
  receivedOnlineFriends = false;
  trans: Trans;
  users: Map<string, Friend>;

  constructor(readonly el: HTMLElement) {
    this.titleEl = this.el.querySelector('.friend_box_button') as HTMLElement;
    this.countEl = this.el.querySelector('.friend_box_count') as HTMLElement;
    this.badgeEl = this.titleEl.querySelector('.friend_box_badge') as HTMLElement;
    this.trans = trans(JSON.parse(this.el.getAttribute('data-i18n')!));
    this.users = new Map();
    pubsub.on('socket.in.following_onlines', this.receive);
    pubsub.on('socket.in.following_enters', this.enters);
    pubsub.on('socket.in.following_leaves', this.leaves);
    pubsub.on('socket.in.following_playing', this.playing);
    pubsub.on('socket.in.following_stopped_playing', this.stoppedPlaying);
    // the badge needs the count up front; ask again on each (re)connection
    pubsub.on('socket.open', this.request);
    this.request();
  }
  request = () => pubsub.emit('socket.send', 'following_onlines');
  receive = (friends: TitleName[], msg: { playing: string[]; patrons: string[] }) => {
    this.users.clear();
    this.receivedOnlineFriends = true;
    friends.forEach(this.insert);
    msg.playing
      .map(p => this.users.get(p))
      .filter(notNull)
      .forEach(u => (u.playing = true));
    msg.patrons
      .map(p => this.users.get(p))
      .filter(notNull)
      .forEach(u => (u.patron = true));
    this.repaint();
  };
  repaint = () => {
    if (this.receivedOnlineFriends)
      requestAnimationFrame(() => {
        const ids = Array.from(this.users.keys()).sort();
        const count = this.trans.plural('nbFriendsOnline', ids.length);
        this.titleEl.title = count;
        this.titleEl.setAttribute('aria-label', count);
        this.countEl.textContent = count;
        this.badgeEl.innerHTML = ids.length > 99 ? '99<sup>+</sup>' : ids.length ? `${ids.length}` : '';
        this.el.querySelector('.list')!.innerHTML = ids.map(id => this.renderFriend(this.users.get(id)!)).join('');
      });
  };
  renderFriend = (friend: Friend) => {
    const icon = `<i class="line${friend.patron ? ' patron' : ''}"></i>`,
      titleTag = friend.title
        ? `<span class="utitle"${friend.title === 'BOT' ? ' data-bot' : ''}>${friend.title}</span>&nbsp;`
        : '',
      url = '/@/' + friend.name,
      tvButton = friend.playing
        ? `<a data-icon="1" class="tv ulpt" data-pt-pos="nw" href="${url}/tv" data-href="${url}"></a>`
        : '';
    return `<div><a class="user-link ulpt" data-pt-pos="nw" href="${url}">${icon}${titleTag}${friend.name}</a>${tvButton}</div>`;
  };

  enters = (titleName: TitleName, msg: { playing: boolean; patron: boolean }) => {
    const friend = this.insert(titleName);
    friend.playing = msg.playing;
    friend.patron = msg.patron;
    this.repaint();
  };
  leaves = (titleName: TitleName) => {
    this.users.delete(this.getId(titleName));
    this.repaint();
  };
  playing = (titleName: TitleName) => {
    this.insert(titleName).playing = true;
    this.repaint();
  };
  stoppedPlaying = (titleName: TitleName) => {
    this.insert(titleName).playing = false;
    this.repaint();
  };

  insert = (titleName: TitleName): Partial<Friend> => {
    const id = this.getId(titleName);
    if (!this.users.has(id)) this.users.set(id, this.toFriend(titleName));
    return this.users.get(id)!;
  };

  getId = (titleName: TitleName) => titleName.toLowerCase().replace(/^\w+\s/, '');

  toFriend = (titleName: TitleName): Friend => {
    const split = titleName.split(' ');
    return {
      id: split[split.length - 1].toLowerCase(),
      name: split[split.length - 1],
      title: split.length > 1 ? split[0] : undefined,
      playing: false,
      patron: false,
    };
  };
}
