#!/bin/bash

set -euo pipefail

IMGS=''

while read dir; do
    vsn=$(cat "${dir}/vsn")
    if [ -f "${dir}/platforms" ]; then
        platforms=$(cat "${dir}/platforms")
    else
        platforms='linux/amd64,linux/arm64'
    fi
    tags="ghcr.io/emqx/${dir}:${vsn}"
    if [ "$dir" = "certgen" ]; then
        tags="${tags},ghcr.io/emqx/${dir}:latest"
    fi
    img="[\"$dir\", \"$vsn\", \"$platforms\", \"$tags\"]"
    if [ -n "$IMGS" ]; then
        IMGS="${IMGS},${img}"
    else
        IMGS="${img}"
    fi
done < <(find . -type f -name 'Dockerfile' -printf '%h\n' | sed 's|./||')

echo "{\"image\":[${IMGS}]}"
