Check TypeScript and Kotlin type alignment between frontend and backend.

## Steps

1. Run frontend typecheck: `pnpm --dir frontend typecheck`
2. If there are errors, read the failing files and fix type issues
3. Cross-check: compare frontend API types (`frontend/src/lib/api/types/`) with backend DTOs
4. Report any mismatches between FE TypeScript types and BE Kotlin DTOs

## Focus Areas

- Request/Response DTO field names and types
- Enum/union type alignment
- Nullable vs required field consistency
- New backend fields missing from frontend types
