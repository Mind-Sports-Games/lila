#!/bin/bash
printf 'enter path to stratops
Examples of values :
- empty space to quit
- repo : git+ssh://git@github.com:Mind-Sports-Games/stratops.git#semver:v0.8.1-pstrat4.4
- local : file:/home/vincent/Desktop/code/stratops
'
read path
echo "path chosen: $path"
if [ -z "$path" ]; then
    exit 2
fi

workspaces=("analyse" "ceval" "dgt" "editor" "puz" "puzzle" "racer" "storm")

for workspace in "${workspaces[@]}"
do
    pnpm add $path --filter $workspace
done
