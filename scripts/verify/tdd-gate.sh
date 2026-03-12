#!/usr/bin/env bash

set -euo pipefail

staged_files="$(git diff --cached --name-only --diff-filter=ACMRD)"

if [[ -z "$staged_files" ]]; then
    exit 0
fi

backend_source_changes="$(printf '%s\n' "$staged_files" | grep -E '^backend/.*/src/main/.*\.kt$' || true)"
backend_test_changes="$(printf '%s\n' "$staged_files" | grep -E '^backend/.*/src/test/.*\.kt$' || true)"

frontend_source_changes="$(printf '%s\n' "$staged_files" | grep -E '^frontend/src/(app|components|lib|stores|hooks)/.*\.(ts|tsx)$' | grep -Ev '\.(test|spec)\.(ts|tsx)$' || true)"
frontend_test_changes="$(printf '%s\n' "$staged_files" | grep -E '^frontend/src/.*\.(test|spec)\.(ts|tsx)$' || true)"
deleted_test_changes="$(git diff --cached --name-only --diff-filter=D | grep -E '^(backend/.*/src/test/.*\.kt|frontend/src/.*\.(test|spec)\.(ts|tsx))$' || true)"

if [[ -n "$deleted_test_changes" ]]; then
    printf 'TDD gate failed: deleting committed tests requires manual review and replacement coverage.\n' >&2
    printf '%s\n' "$deleted_test_changes" >&2
    exit 1
fi

if [[ -n "$backend_source_changes" && -z "$backend_test_changes" ]]; then
    printf 'TDD gate failed: staged backend source changes require staged backend tests.\n' >&2
    printf '%s\n' "$backend_source_changes" >&2
    exit 1
fi

if [[ -n "$frontend_source_changes" && -z "$frontend_test_changes" ]]; then
    printf 'TDD gate failed: staged frontend source changes require staged frontend tests.\n' >&2
    printf '%s\n' "$frontend_source_changes" >&2
    exit 1
fi
