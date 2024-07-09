import * as fs from 'node:fs';
import * as path from 'node:path';
import * as fg from 'fast-glob';
import { PlaystrategyModule, env, colors as c } from './main';

export const parseModules = async (): Promise<[Map<string, PlaystrategyModule>, Map<string, string[]>]> => {
  const modules = new Map<string, PlaystrategyModule>();
  const moduleDeps = new Map<string, string[]>();

  for (const dir of (await globArray('[^@.]*/package.json')).map(pkg => path.dirname(pkg))) {
    const mod = await parseModule(dir);
    modules.set(mod.name, mod);
  }

  for (const mod of modules.values()) {
    const deplist: string[] = [];
    for (const dep in mod.pkg.dependencies) {
      if (modules.has(dep)) deplist.push(dep);
    }
    moduleDeps.set(mod.name, deplist);
  }
  return [modules, moduleDeps];
};

export async function globArray(glob: string, { cwd = env.uiDir, abs = true, dirs = false } = {}): Promise<string[]> {
  const files: string[] = [];
  for await (const f of fg.stream(glob, { cwd, absolute: abs, onlyFiles: !dirs })) files.push(f.toString('utf8'));
  return files;
}

async function parseModule(moduleDir: string): Promise<PlaystrategyModule> {
  const pkg = JSON.parse(await fs.promises.readFile(path.join(moduleDir, 'package.json'), 'utf8'));
  const mod: PlaystrategyModule = {
    pkg: pkg,
    name: path.basename(moduleDir),
    root: moduleDir,
    pre: [],
    post: [],
    hasTsconfig: fs.existsSync(path.join(moduleDir, 'tsconfig.json')),
  };

  if ('playstrategy' in pkg && 'bundles' in pkg.playstrategy) {
    if (typeof pkg.playstrategy.bundles === 'string') mod.bundles = [pkg.playstrategy.bundles];
    else mod.bundles = pkg.playstrategy.bundles as string[];
  }
  if ('playstrategy' in pkg && 'sync' in pkg.playstrategy) {
    mod.sync = Object.entries(pkg.playstrategy.sync).map(x => ({
      src: x[0],
      dest: x[1] as string,
      mod,
    }));
  }
  return mod;
}
