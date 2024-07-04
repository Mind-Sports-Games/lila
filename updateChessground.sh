#!/bin/bash
printf 'enter path for chessground
Examples of values :
- empty space to quit
- repo : git+ssh://git@github.com:Mind-Sports-Games/chessground.git#semver:v7.11.1-pstrat2.48
- local : file:/home/vincent/Desktop/code/chessground
'
read path
echo "path chosen: $path"
if [ -z "$path" ]; then
    exit 2
fi

workspaces=("analyse" "draughtsround" "editor" "learn" "lobby" "puz" "puzzle" "racer" "round" "storm" "swiss" "tournament")

for workspace in "${workspaces[@]}"
do
    pnpm add $path --filter $workspace
done
