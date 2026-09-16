const subs: Array<() => void> = [];

const pubsub: Pubsub = {
  on(name: string, cb) {
    subs[name] = subs[name] || [];
    subs[name].push(cb);
  },
  off(name: string, cb) {
    if (subs[name]) subs[name] = subs[name].filter(f => f !== cb);
  },
  emit(name: string, ...args: any[]) {
    if (subs[name]) for (const i in subs[name]) subs[name][i].apply(null, args);
  },
};

export default pubsub;
