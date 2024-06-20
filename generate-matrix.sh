#!/bin/bash

set -euo pipefail

IMGS=''

while read dir; do
    vsn=$(cat "${dir}/vsn")
    img="[\"$dir\", \"$vsn\"]"
    if [ -n "$IMGS" ]; then
        IMGS="${IMGS},${img}"
    else
        IMGS="${img}"
    fi
done < <(find . -type f -name 'Dockerfile' -printf '%h\n' | sed 's|./||')

echo "{\"image\":[${IMGS}]}"
