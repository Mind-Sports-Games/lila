import pubsub from './pubsub';
import { assetUrl } from './assets';
import { storage } from './storage';

type Name = string;
type Path = string;

const sound = new (class {
  sounds = new Map<Name, any>(); // The loaded sounds and their instances
  soundSet = $('body').data('sound-set');
  speechStorage = storage.makeBoolean('speech.enabled');
  volumeStorage = storage.make('sound-volume');
  baseUrl = assetUrl('sound', {
    version: '_____1', // 6 random letters to update
  });

  constructor() {
    if (this.soundSet == 'music') setTimeout(this.publish, 500);
  }

  getNameAndSet = (name: Name) => (['music', 'standard'].includes(this.soundSet) ? name : `${name}-${this.soundSet}`);

  getSound = (name: Name) => this.sounds.get(this.getNameAndSet(name));

  loadOggOrMp3 = (name: Name, path: Path): Promise<void> =>
    new Promise(resolve => {
      const sound = new window.Howl({
        src: ['ogg', 'mp3'].map(ext => `${path}.${ext}`),
      });
      if (sound._duration != 0) {
        // sound already loaded
        resolve(sound);
      }
      sound.on('loaderror', () => {
        // 1st load error : file not found, use standard sound
        const sound2 = new window.Howl({
          src: ['ogg', 'mp3'].map(
            ext => `${`${this.baseUrl}/standard/${name[0].toUpperCase() + name.slice(1)}`}.${ext}`,
          ),
        });
        resolve(sound2);
      });
      sound.on('load', () => {
        // 1st load success
        resolve(sound);
      });
    });

  loadStandard(name: Name, soundSet?: string): Promise<void> {
    if (!this.enabled()) return Promise.resolve();
    return new Promise(resolve => {
      const path = name[0].toUpperCase() + name.slice(1);
      this.loadOggOrMp3(name, `${this.baseUrl}/${soundSet || this.soundSet}/${path}`).then(sound => {
        resolve(sound);
      });
    });
  }

  preloadBoardSounds() {
    if (this.soundSet !== 'music') ['move', 'capture', 'check'].forEach(s => this.loadStandard(s));
  }

  play(name: string, volume?: number) {
    if (!this.enabled()) return;
    let set = this.soundSet;
    if (set === 'music' || this.speechStorage.get()) {
      if (['move', 'capture', 'check'].includes(name)) return;
      set = 'standard';
    }
    // try to "load" the sound in case it was already fetched
    let s = this.getSound(name);
    const doPlay = () => s && s.volume(this.getVolume() * (volume || 1)).play();
    if (!s) {
      // fetch the sound...
      this.loadStandard(name, set).then(sound => {
        this.sounds.set(this.getNameAndSet(name), sound);
        s = this.getSound(name);
        if (window.Howler.ctx?.state === 'suspended') window.Howler.ctx.resume().then(doPlay);
        else doPlay();
      });
    } else {
      if (window.Howler.ctx?.state === 'suspended') window.Howler.ctx.resume().then(doPlay);
      else doPlay();
    }
  }

  setVolume = this.volumeStorage.set;

  getVolume = () => {
    // garbage has been stored here by accident (e972d5612d)
    const v = parseFloat(this.volumeStorage.get() || '');
    return v >= 0 ? v : 0.7;
  };

  enabled = () => this.soundSet !== 'silent';

  speech = (v?: boolean): boolean => {
    if (typeof v != 'undefined') this.speechStorage.set(v);
    return this.speechStorage.get();
  };

  say = (text: any, cut = false, force = false) => {
    if (!this.speechStorage.get() && !force) return false;
    const msg = text.text ? (text as SpeechSynthesisUtterance) : new SpeechSynthesisUtterance(text);
    msg.volume = this.getVolume();
    msg.lang = 'en-US';
    if (cut) speechSynthesis.cancel();
    speechSynthesis.speak(msg);
    return true;
  };

  sayOrPlay = (name: string, text: string) => this.say(text) || this.play(name);

  publish = () => pubsub.emit('sound_set', this.soundSet);

  changeSet = (s: string) => {
    this.soundSet = s;
    this.sounds.clear();
    this.publish();
  };

  set = () => this.soundSet;
})();

export default sound;
