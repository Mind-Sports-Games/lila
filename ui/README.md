## Client-side modules

Client builds are performed by the ui/build script.\
Stick to `ui/build -cr` (clean rebuild with watch mode) and leave it running when you can.\
NOTE - always use hard refresh (google it) or disable caching in the network tab of your browser inspector to pick up fresh changes.

NOTE - since we now use pnpm, it may be useful to know you can run `rm -rf node_modules pnpm-lock.yaml && pnpm store prune && pnpm i` if you want to ensure pnpm tries to solve completely the deps tree\
You can run `pnpm lint` and `pnpm format`, but there is also a new `pnpm lint-staged`.

Usage examples:

ui/build # builds all client assets in dev mode\
ui/build -w # builds all client assets and watches for changes\
ui/build -p # builds minified client assets (prod builds)\
ui/build --no-install # no pnpm install (to preserve local links you have set up)\
ui/build analyse site msg # specify modules (don't build everything)\
ui/build -w dasher chart # watch mode but only for given modules\
ui/build --tsc -w # watch mode but type checking only\
ui/build --sass msg notify # build css only for msg and notify modules\
ui/build --no-color # don't use color in logs\
ui/build --no-time # don't log the time\
ui/build --no-context # don't log the context ([sass], [esbuild], etc)

## Testing

Lichess use the Vitest testing framework.
We do not (yet?)

## CSS

The structure of a CSS module is as follows:

- css/
  - forum/
    - \_forum.scss # imports the files below
    - \_post.scss
    - \_search.scss
    - ...
  - build/
    - \_forum.scss # imports dependencies and `../forum/forum`.
    - forum.light.scss # generated
    - forum.dark.scss # generated
    - forum.transp.scss # generated
