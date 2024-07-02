import * as fs from 'node:fs';
import * as cps from 'node:child_process';
import * as ps from 'node:process';
import { parseModules } from './parse';
import { tsc, stopTsc } from './tsc';
import { sass, stopSass } from './sass';
import { esbuild, stopEsbuild } from './esbuild';
import { copies, stopCopies } from './copies';
import { startMonitor, stopMonitor } from './monitor';
import { initManifest, writeManifest } from './manifest';
import { clean } from './clean';
import { PlaystrategyModule, env, errorMark, colors as c } from './main';

export async function build(mods: string[]) {
  await stop();
  await clean();

  if (env.install) cps.execSync('pnpm install', { cwd: env.rootDir, stdio: 'inherit' });
  if (!mods.length) env.log(`Parsing modules in '${c.cyan(env.uiDir)}'`);

  ps.chdir(env.uiDir);
  [env.modules, env.deps] = await parseModules();

  mods.filter(x => !env.modules.has(x)).forEach(x => env.exit(`${errorMark} - unknown module '${c.magenta(x)}'`));

  env.building = mods.length === 0 ? [...env.modules.values()] : depsMany(mods);

  if (mods.length) env.log(`Building ${c.grey(env.building.map(x => x.name).join(', '))}`);

  await Promise.allSettled([
    fs.promises.mkdir(env.jsDir),
    fs.promises.mkdir(env.cssDir),
    fs.promises.mkdir(env.themeGenDir),
    fs.promises.mkdir(env.cssTempDir),
  ]);

  await initManifest();
  startMonitor(mods);
  await Promise.all([sass(), copies(), esbuild(tsc())]);
}

export async function stop() {
  stopMonitor();
  stopSass();
  stopTsc();
  stopCopies();
  await stopEsbuild();
}

export function postBuild() {
  writeManifest();
  for (const mod of env.building) {
    mod.post.forEach((args: string[]) => {
      env.log(`[${c.grey(mod.name)}] exec - ${c.cyanBold(args.join(' '))}`);
      const stdout = cps.execSync(`${args.join(' ')}`, { cwd: mod.root });
      if (stdout) env.log(stdout, { ctx: mod.name });
    });
  }
  buildPlaystrategy();
}

export function preModule(mod: PlaystrategyModule | undefined) {
  mod?.pre.forEach((args: string[]) => {
    env.log(`[${c.grey(mod.name)}] exec - ${c.cyanBold(args.join(' '))}`);
    const stdout = cps.execSync(`${args.join(' ')}`, { cwd: mod.root });
    if (stdout) env.log(stdout, { ctx: mod.name });
  });
}

function depsOne(modName: string): PlaystrategyModule[] {
  const collect = (dep: string): string[] => [...(env.deps.get(dep) || []).flatMap(d => collect(d)), dep];
  return unique(collect(modName).map(name => env.modules.get(name)));
}

const depsMany = (modNames: string[]): PlaystrategyModule[] => unique(modNames.flatMap(depsOne));

const unique = <T>(mods: (T | undefined)[]): T[] => [...new Set(mods.filter(x => x))] as T[];

async function buildPlaystrategy() {
  const depsFilename = `${env.jsDir}/deps.min.js`;
  await buildDeps(depsFilename);

  if (env.prod) {
    const playstrategyFilename = `${env.jsDir}/playstrategy.min.js`;
    try {
      if (fs.existsSync(playstrategyFilename)) return;
      env.log(`Generating ${c.cyan(playstrategyFilename)} file`);
      await append(depsFilename, playstrategyFilename);
      env.log(`Added the content of ${c.cyan(depsFilename)}`);
      await append(`${env.jsDir}/site.min.js`, playstrategyFilename);
      env.log(`Added the content of ${c.cyan(`${env.jsDir}/site.min.js`)}`);
    } catch (error) {
      env.log(`${c.error(error + '')}`);
    }
  }
}

async function buildDeps(filename: string) {
  try {
    if (fs.existsSync(filename)) return;
    env.log(`Generating ${c.cyan(filename)} file...`);
    await append(`${env.outDir}/javascripts/vendor/cash.min.js`, filename);
    env.log(`Added the content of ${c.cyan(`${env.outDir}/javascripts/vendor/cash.min.js`)}`);
    await append(`${env.uiDir}/site/dep/powertip.min.js`, filename);
    env.log(`Added the content of ${c.cyan(`${env.uiDir}/site/dep/powertip.min.js`)}`);
    await append(`${env.uiDir}/site/dep/howler.min.js`, filename);
    env.log(`Added the content of ${c.cyan(`${env.uiDir}/site/dep/howler.min.js`)}`);
    await append(`${env.uiDir}/site/dep/mousetrap.min.js`, filename);
    env.log(`Added the content of ${c.cyan(`${env.uiDir}/site/dep/mousetrap.min.js`)}`);
  } catch (error) {
    env.log(`${c.error(error + '')}`);
  }
}

async function append(src: string, dest: string): Promise<void> {
  const readSrc = async () => {
    return fs.promises.readFile(src, { encoding: 'utf8' });
  };
  const appendFile = async (content: string) => {
    return fs.promises.appendFile(dest, content);
  };
  const content = await readSrc();
  await appendFile(content);
}
