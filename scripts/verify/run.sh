#!/usr/bin/env bash

set -euo pipefail

PROFILE="${1:-pre-commit}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "$ROOT_DIR"

staged_files() {
    git diff --cached --name-only --diff-filter=ACMR
}

has_staged_match() {
    local pattern="$1"
    staged_files | grep -Eq "$pattern"
}

resolve_java_home() {
    if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
        return 0
    fi

    if command -v /usr/libexec/java_home >/dev/null 2>&1; then
        local detected
        detected="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
        if [[ -n "$detected" ]]; then
            export JAVA_HOME="$detected"
            return 0
        fi
    fi

    if command -v java >/dev/null 2>&1; then
        return 0
    fi

    printf 'Unable to locate Java 17. Set JAVA_HOME before running verification.\n' >&2
    exit 1
}

run_backend_tests() {
    resolve_java_home
    printf '\n[verify] backend tests\n'
    (
        cd backend
        ./gradlew test --no-daemon
    )
}

run_backend_parity() {
    resolve_java_home
    printf '\n[verify] backend parity suite\n'
    (
        cd backend
        ./gradlew :game-app:test \
            --tests 'com.opensam.qa.parity.*' \
            --tests 'com.opensam.qa.ApiContractTest' \
            --tests 'com.opensam.qa.GoldenValueTest' \
            --tests 'com.opensam.command.CommandParityTest' \
            --tests 'com.opensam.engine.FormulaParityTest' \
            --tests 'com.opensam.engine.DeterministicReplayParityTest' \
            --no-daemon
    )
}

run_frontend_unit_tests() {
    printf '\n[verify] frontend unit tests\n'
    pnpm --dir frontend test --run
}

run_frontend_typecheck() {
    printf '\n[verify] frontend typecheck\n'
    pnpm --dir frontend typecheck
}

run_frontend_build() {
    printf '\n[verify] frontend build\n'
    pnpm --dir frontend build
}

run_frontend_parity() {
    printf '\n[verify] frontend structural parity\n'
    node scripts/verify/frontend-parity.mjs
}

run_tdd_gate() {
    printf '\n[verify] staged test requirement gate\n'
    scripts/verify/tdd-gate.sh
}

run_pre_commit() {
    local backend_changed=false
    local frontend_changed=false

    if has_staged_match '^backend/'; then
        backend_changed=true
    fi

    if has_staged_match '^frontend/'; then
        frontend_changed=true
    fi

    if [[ "$backend_changed" == false && "$frontend_changed" == false ]]; then
        printf '[verify] no backend/frontend changes staged; skipping gate.\n'
        exit 0
    fi

    run_tdd_gate

    if [[ "$backend_changed" == true ]]; then
        run_backend_tests
        run_backend_parity
    fi

    if [[ "$frontend_changed" == true ]]; then
        run_frontend_unit_tests
        run_frontend_typecheck
        run_frontend_parity
    fi
}

run_ci() {
    run_backend_tests
    run_backend_parity
    run_frontend_unit_tests
    run_frontend_typecheck
    run_frontend_build
    run_frontend_parity
}

case "$PROFILE" in
    pre-commit)
        run_pre_commit
        ;;
    ci|nightly)
        run_ci
        ;;
    *)
        printf 'Unknown verification profile: %s\n' "$PROFILE" >&2
        exit 1
        ;;
esac
