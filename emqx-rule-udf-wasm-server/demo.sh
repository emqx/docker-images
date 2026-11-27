#!/usr/bin/env bash
# Walk through the emqx-rule-udf/1 protocol (plugins/emqx_rule_udf/PROTOCOL.md in emqx/emqx) with curl.
# Needs curl and jq.
#
#   docker run -d --name emqx-rule-udf-demo -p 127.0.0.1:18480:8080 -e UDF_TOKEN=demo-token \
#     --read-only --cap-drop ALL --security-opt no-new-privileges emqx-rule-udf-wasm-server:dev
#   ./demo.sh

set -euo pipefail

URL="${UDF_URL:-http://127.0.0.1:18480}"
TOKEN="${UDF_TOKEN:-demo-token}"
AUTH=(-H "Authorization: Bearer ${TOKEN}")

step() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
show() { curl -sS -w '\nHTTP %{http_code}\n' "$@"; }

# fn NAME < source.py: keep the source as {"runtime", "source"} in ${WORK}/NAME.json.
fn() {
  jq -n --rawfile src /dev/stdin '{runtime: "python3", source: $src}' >"${WORK}/$1.json"
}

# validate NAME: check the function, as EMQX does when it saves a program.
validate() {
  show "${AUTH[@]}" -H 'Content-Type: application/json' --data-binary @"${WORK}/$1.json" \
    "${URL}/v1/validate"
}

sha() {
  jq -j '.runtime + "\n" + .source' "${WORK}/$1.json" | sha256sum | cut -d' ' -f1
}

# call NAME ARGS_JSON [with-function]: call by version; add the function to load it.
call() {
  jq -n --arg name "$1" --arg sha "$(sha "$1")" --argjson args "$2" \
    --slurpfile f "${WORK}/$1.json" --arg with "${3:-}" \
    '{name: $name, sha256: $sha, args: $args} + (if $with == "" then {} else {function: $f[0]} end)' |
    show "${AUTH[@]}" -H 'Content-Type: application/json' --data-binary @- "${URL}/v1/call"
}

WORK=$(mktemp -d)
trap 'rm -rf "${WORK}"' EXIT

step "1. Health check (no token needed)"
show "${URL}/healthz"

step "2. Every other endpoint needs the token"
show "${URL}/v1/info"

step "3. Server info: protocol, runtimes and limits"
show "${AUTH[@]}" "${URL}/v1/info"

step "4. Validate a function: str_upper(s). The server keeps nothing"
fn str_upper <<'EOF'
def handle(s):
    return s.upper()
EOF
validate str_upper

step "5. A call by version alone fails while the server does not have it loaded"
call str_upper '["hello emqx"]'

step "6. The call that carries the function loads it and runs it"
call str_upper '["hello emqx"]' with-function

step "7. Now a call by version alone works"
call str_upper '["hello emqx"]'

step "8. A function that uses allowed imports: decode a base64 JSON payload"
fn temp_f <<'EOF'
import base64
import json

def handle(payload_b64):
    data = json.loads(base64.b64decode(payload_b64))
    return {"device": data["id"], "temp_f": round(data["temp_c"] * 9 / 5 + 32, 1)}
EOF
PAYLOAD=$(printf '{"id":"dev-1","temp_c":21.5}' | base64 -w0)
call temp_f "[\"${PAYLOAD}\"]" with-function

step "9. The check rejects a disallowed import: INVALID_SOURCE (feedback only, not the sandbox)"
fn bad_import <<'EOF'
import os
def handle():
    return os.listdir("/")
EOF
validate bad_import

step "10. An exception in the function returns 422; the worker keeps running"
call str_upper '[42]'

step "11. Get past the lint check, then try to reach the host from inside the sandbox"
fn escape <<'EOF'
import json

# 'os' is not in the allowlist, but it is reachable through json -> re -> enum -> sys.
os = json.decoder.re.enum.sys.modules["os"]

def attempt(f):
    try:
        return repr(f())[:100]
    except BaseException as e:
        return "BLOCKED: %s: %s" % (type(e).__name__, e)

def handle():
    return {
        "environ": attempt(lambda: dict(os.environ)),
        "list /": attempt(lambda: os.listdir("/")),
        "read /etc/passwd": attempt(lambda: open("/etc/passwd").read()),
        "write /lib": attempt(lambda: open("/lib/pwned.py", "w").write("x")),
        "spawn process": attempt(lambda: os.posix_spawn("/bin/sh", ["sh"], {})),
        "network": attempt(
            lambda: os.sys.modules["socket"].create_connection(("1.1.1.1", 80), 1)
            if "socket" in os.sys.modules
            else __import__("socket").create_connection(("1.1.1.1", 80), 1)
        ),
    }
EOF
call escape '[]' with-function

step "12. An infinite loop is killed at the call timeout (504); the next call works"
fn spin <<'EOF'
def handle(n):
    if n < 0:
        while True:
            pass
    return n * 2
EOF
call spin '[-1]' with-function
call spin '[21]'

step "13. Memory is capped per worker"
fn hog <<'EOF'
def handle():
    return len(bytearray(512 * 1024 * 1024))
EOF
call hog '[]' with-function
