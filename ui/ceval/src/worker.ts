import { sync, Sync } from 'common/sync';
import { ProtocolOpts, Work } from './types';
import Protocol from './stockfishProtocol';
import { Cache } from './cache';

export abstract class AbstractWorker<T> {
  protected protocol: Sync<Protocol>;
  readonly enginePath = 'vendor/fairy-stockfish-nnue.wasm/';

  constructor(
    protected protocolOpts: ProtocolOpts,
    protected opts: T,
  ) {
    this.protocol = sync(this.boot());
  }

  stop(): Promise<void> {
    return this.protocol.promise.then(protocol => protocol.stop());
  }

  start(work: Work): Promise<void> {
    return this.protocol.promise.then(protocol => protocol.start(work));
  }

  isComputing: () => boolean = () => !!this.protocol.sync && this.protocol.sync.isComputing();

  engineName: () => string | undefined = () => this.protocol.sync?.engineName.sync;

  protected abstract boot(): Promise<Protocol>;
  protected abstract send(cmd: string): void;
  abstract destroy(): void;
}

export interface WebWorkerOpts {
  wasm: boolean;
}

export class WebWorker extends AbstractWorker<WebWorkerOpts> {
  private worker: Worker;

  boot(): Promise<Protocol> {
    this.worker = new Worker(
      playstrategy.assetUrl(`${this.enginePath}/stockfish.${this.opts.wasm ? 'wasm' : 'js'}`, { sameDomain: true }),
    );
    const protocol = new Protocol(this.send.bind(this), this.protocolOpts);
    this.worker.addEventListener(
      'message',
      e => {
        protocol.received(e.data);
      },
      true,
    );
    protocol.init();
    return Promise.resolve(protocol);
  }

  destroy() {
    this.worker.terminate();
  }

  protected send(cmd: string) {
    this.worker.postMessage(cmd);
  }
}

export interface ThreadedWasmWorkerOpts {
  version: string;
  downloadProgress?: (mb: number) => void;
  wasmMemory: WebAssembly.Memory;
  cache?: Cache;
}

export class ThreadedWasmWorker extends AbstractWorker<ThreadedWasmWorkerOpts> {
  private static protocols: { Stockfish?: Protocol; StockfishMv?: Protocol } = {};
  private static sf: { Stockfish?: any; StockfishMv?: any } = {};
  async boot(): Promise<Protocol> {
    let protocol = ThreadedWasmWorker.protocols['Stockfish'];
    if (!protocol) {
      const version = this.opts.version;
      const cache = this.opts.cache;

      // Fetch WASM file ourselves, for caching and progress indication.
      let wasmBinary;
      if (cache) {
        const wasmPath = this.enginePath + 'stockfish.wasm';
        if (cache) {
          const [found, data] = await cache.get(wasmPath, version!);
          if (found) wasmBinary = data;
        }

        if (!wasmBinary) {
          wasmBinary = await new Promise((resolve, reject) => {
            const req = new XMLHttpRequest();
            req.open('GET', playstrategy.assetUrl(wasmPath), true);
            req.responseType = 'arraybuffer';
            req.onerror = event => reject(event);
            req.onprogress = event => this.opts.downloadProgress?.(event.loaded);
            req.onload = _ => {
              this.opts.downloadProgress?.(0);
              resolve(req.response);
            };
            req.send();
          });
        }

        await cache.set(wasmPath, version!, wasmBinary);
      }

      await playstrategy.loadScriptCJS(this.enginePath + 'stockfish.js');

      const sf = await window['Stockfish']({
        wasmBinary,
        locateFile: (path: string) =>
          playstrategy.assetUrl(this.enginePath + path, { sameDomain: path.endsWith('.worker.js') }),
        wasmMemory: this.opts.wasmMemory,
      });
      ThreadedWasmWorker.sf['Stockfish'] = sf;

      // Initialize protocol.
      protocol = new Protocol(this.send.bind(this), this.protocolOpts);
      sf.addMessageListener(protocol.received.bind(protocol));
      protocol.init();
      ThreadedWasmWorker.protocols['Stockfish'] = protocol;
    }
    protocol.setVariant(this.protocolOpts.variant);
    return protocol;
  }

  destroy() {
    // Terminated instances to not get freed reliably
    // (https://github.com/ornicar/lila/issues/7334). So instead of
    // destroying, just stop instances and keep them around for reuse.
    this.protocol.sync?.stop();
  }

  send(cmd: string) {
    ThreadedWasmWorker.sf['Stockfish']?.postMessage(cmd);
  }
}
