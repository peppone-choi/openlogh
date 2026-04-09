---
status: partial
phase: 14-frontend-integration
source: [14-VERIFICATION.md]
started: 2026-04-09T12:45:00Z
updated: 2026-04-09T12:45:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. 진영색 palette fidelity
expected: Empire #4466ff, Alliance #ff4444, Fezzan #888888 — CRC rings, unit icon borders, and ghost icons render in correct faction colors with correct lightened variants for friendly/enemy differentiation on a live tactical battle canvas.
result: [pending]

### 2. CRC real-time shrink animation
expected: CommandRangeCircle radius visually shrinks smoothly frame-by-frame as tick events arrive; no flicker or stutter; 8 simultaneous CRCs maintain 60fps in Chrome DevTools.
result: [pending]

### 3. Sub-fleet drag-and-drop feel
expected: 60-unit load produces no lag; drop zones highlight on hover; disabled (aria-disabled) chips reject drag attempts; bucket labels readable.
result: [pending]

### 4. Fog of war ghost UX tension
expected: Dashed ghost icons with Korean tick stamps appear at last-seen enemy positions; ghosts fade over ~60 ticks; creates "they were there" spatial memory tension.
result: [pending]

### 5. Flagship flash visual impact
expected: 0.5s Konva ring flash plays on flagship destruction; ring expands and fades cleanly without canvas artifacts; visible against busy battle background.
result: [pending]

### 6. Sonner toast Korean particle accuracy
expected: Succession toasts use grammatically correct Korean particles (-에게/-께/-로) depending on the final-consonant rule of the officer name; no particle errors.
result: [pending]

### 7. F1 overlay toggle — cross-browser safety
expected: Pressing F1 on the galaxy map opens OperationsOverlay in Chrome, Safari, and Firefox without triggering the browser's built-in help dialog. Esc dismisses correctly.
result: [pending]

### 8. Korean text consistency across new UI
expected: All newly added Korean strings across BattleEndModal, OperationsOverlay, SubFleetAssignmentDrawer, InfoPanel NPC rows, succession toasts, ghost tick stamps are grammatically consistent, use correct honorifics level, and match gin7 manual terminology.
result: [pending]

### 9. Performance under load
expected: Tactical battle page maintains ≥ 50 FPS with 60 units, 8 active CRC rings, 15+ fog ghosts, and 10Hz STOMP ticks; no memory leak over 10 minutes.
result: [pending]

## Summary

total: 9
passed: 0
issues: 0
pending: 9
skipped: 0
blocked: 0

## Gaps
