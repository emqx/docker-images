#!/usr/bin/env bash

set -euo pipefail

RUNTIME_IMAGE="${RUNTIME_IMAGE:-ghcr.io/emqx/certgen:latest}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

run_case() {
    local name="$1"
    local prefix="$2"
    local dir
    dir="$(mktemp -d)"

    if [ -n "$prefix" ]; then
        docker run --rm \
            -e "CERTGEN_JKS_PREFIX=$prefix" \
            -v "$ROOT/entrypoint.sh:/bin/entrypoint.sh:ro" \
            -v "$dir:/var/lib/secret" \
            "$RUNTIME_IMAGE"
    else
        docker run --rm \
            -v "$ROOT/entrypoint.sh:/bin/entrypoint.sh:ro" \
            -v "$dir:/var/lib/secret" \
            "$RUNTIME_IMAGE"
    fi

    test -f "$dir/ca.crt"
    test -f "$dir/client.crt"
    test -f "$dir/client.key"
    test -f "$dir/${name}.keystore.jks"
    test -f "$dir/${name}.truststore.jks"

    if [ "$name" != "kafka" ]; then
        test ! -e "$dir/kafka.keystore.jks"
        test ! -e "$dir/kafka.truststore.jks"
    fi

    rm -rf "$dir"
}

run_case kafka ""
run_case cassandra cassandra

dir="$(mktemp -d)"
if docker run --rm \
    -e "CERTGEN_JKS_PREFIX=../bad" \
    -v "$ROOT/entrypoint.sh:/bin/entrypoint.sh:ro" \
    -v "$dir:/var/lib/secret" \
    "$RUNTIME_IMAGE"; then
    echo "expected invalid CERTGEN_JKS_PREFIX to fail" >&2
    exit 1
fi
rm -rf "$dir"
