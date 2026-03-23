Run the project verification suite.

## Steps

1. Check which areas have changes: `git diff --name-only HEAD`
2. If backend changes exist, run: `cd backend && ./gradlew test --no-daemon`
3. If frontend changes exist, run:
    - `pnpm --dir frontend test --run`
    - `pnpm --dir frontend typecheck`
4. Report results concisely: what passed, what failed, and suggested fixes for failures.

## Notes

- Use `scripts/verify/run.sh pre-commit` for the full pre-commit gate
- Use `scripts/verify/run.sh ci` for the full CI suite
- If only one area changed, skip the other
