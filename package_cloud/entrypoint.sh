#!/usr/bin/env bash

set -euo pipefail

echo "{\"url\":\"https://packagecloud.io\", \"token\": \"${PACKAGECLOUD_TOKEN}\"}" > ~/.packagecloud
package_cloud "$@"
