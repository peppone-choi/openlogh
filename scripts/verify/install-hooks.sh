#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GIT_DIR="$(git -C "$ROOT_DIR" rev-parse --git-dir)"

install -m 0755 "$ROOT_DIR/.githooks/pre-commit" "$ROOT_DIR/$GIT_DIR/hooks/pre-commit"
printf 'Installed pre-commit hook to %s/hooks/pre-commit\n' "$ROOT_DIR/$GIT_DIR"
