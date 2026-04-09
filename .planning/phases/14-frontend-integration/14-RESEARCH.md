# Phase 14: 프론트엔드 통합 — Technical Research

**Researched:** 2026-04-09
**Scope:** How to implement FE-01..FE-05 while honoring the 37 locked decisions in `14-CONTEXT.md` and the approved `14-UI-SPEC.md`.
**Consumer:** `gsd-planner` (will produce waved PLAN.md files).

> **Binding inputs.** `14-CONTEXT.md` decisions D-01..D-37 and `14-UI-SPEC.md` are authoritative. This research does not propose alternatives; it enumerates the facts the planner needs to turn those decisions into concrete tasks.

---

## 1. Dependency baseline (from `frontend/package.json`)

Versions (exact, from lockfile/package.json):

| Package | Version | Role | Notes |
|---|---|---|---|
| `react` / `react-dom` | 19.2.3 | Runtime | React 19 — constrains dnd library choice (D-05) |
| `next` | 16.1.6 | Framework | App Router in use |
| `konva` | ^10.2.0 | 2D canvas | BattleMap renderer |
| `react-konva` | ^19.2.2 | React bindings | React 19 compatible |
| `zustand` | ^5.0.11 | Stores | `tacticalStore`, `galaxyStore`, `commandStore`, `worldStore`, `officerStore` |
| `sonner` | ^2.0.7 | Toast | Already installed; used for turn/battle/message toasts |
| `@stomp/stompjs` | ^7.3.0 | STOMP | Wrapped by `frontend/src/lib/websocket.ts` |
| `sockjs-client` | ^1.6.1 | Transport fallback | Paired with STOMP |
| `lucide-react` | ^0.564.0 | Icons | Use for drawer/modal glyphs |
| `tailwindcss` | ^4 | Styling | Tailwind 4 config via `@tailwindcss/postcss` |
| `vitest` | ^3.2.4 | Unit test | `pnpm test` |
| `@playwright/test` | ^1.58.2 | E2E | `pnpm e2e` |
| `@react-three/fiber` | ^9.5.0 | **REMOVAL TARGET** | Only used in 2 files (see §2) |
| `@react-three/drei` | ^10.7.7 | **REMOVAL TARGET** | Same 2 files |
| `three` | ^0.183.2 | **REMOVAL TARGET** | Check for non-R3F users before removal |
| `@types/three` | ^0.183.1 | devDep — drop with three | — |

**Missing packages (must be added for D-05 / FE-02 drag & drop):**

- `@dnd-kit/core` (DndContext, useDraggable, useDroppable, DragOverlay, PointerSensor/KeyboardSensor, collision detection)
- `@dnd-kit/sortable` *(optional — only if we want list-reorder semantics; for buckets the plain `@dnd-kit/core` is sufficient)*
- `@dnd-kit/utilities` (CSS.Translate helper for transform strings)

dnd-kit is actively maintained for React 19 and has no peer-dep conflict with React 19.2.3. Install with `pnpm add @dnd-kit/core @dnd-kit/utilities` (add `@dnd-kit/sortable` only if the planner decides buckets need sortable ordering — CONTEXT.md does not require it).

**Assumption to verify:** dnd-kit current releases work with React 19 StrictMode. If StrictMode double-invocation causes sensor init duplication, use `React.memo` on draggable items and key draggable IDs by fleetId string.

---

## 2. R3F removal (D-25, D-26, D-27)

**Exact R3F/drei import sites:**
```
frontend/src/components/tactical/TacticalMapR3F.tsx
frontend/src/components/tactical/BattleCloseViewScene.tsx
```

**Additional files to investigate for cleanup (referenced in CONTEXT.md as "possible removal"):**
- `frontend/src/components/tactical/BattleCloseView.tsx`
- `frontend/src/components/tactical/BattleCloseViewPanel.tsx`

**Planner action:** Before deleting, grep for every import of these four files and their exports. Any consumer of `TacticalMapR3F`, `BattleCloseViewScene`, `BattleCloseView`, or `BattleCloseViewPanel` must be migrated to use `BattleMap` directly or removed.

**Suggested removal sequence (planner can wave this):**
1. Grep `rg "from.*TacticalMapR3F|from.*BattleCloseView"` → list consumers
2. Migrate any remaining consumer to `BattleMap.tsx`
3. Delete the 4 files
4. Remove `@react-three/fiber`, `@react-three/drei`, `three`, `@types/three` from `frontend/package.json`
5. Run `pnpm install && pnpm typecheck && pnpm build` to confirm no orphaned imports
6. Run `pnpm test` and `pnpm e2e:oauth` to confirm no broken snapshots

**Risk:** `three` is also pulled in transitively by other libraries? Grep confirms only the 2 R3F files import it directly. Planner must also check `frontend/src` for any other `from 'three'` before declaring safe removal.

**Assumption to verify:** No Playwright snapshot depends on R3F render output. If any snapshot references `canvas` count >1 or 3D-specific attributes, those snapshots must be regenerated after removal.

---

## 3. Current `BattleMap.tsx` architecture (`frontend/src/components/tactical/BattleMap.tsx`)

- `Stage` 1000×1000 with 3 `Layer`s currently:
  1. Background (space + stars + grid, `listening=false` for perf)
  2. Command range (single `CommandRangeCircle` for `selectedUnit` only)
  3. Units (all `TacticalUnitIcon`s, listening for click)
- `selectedUnit` is computed via `useMemo`; click-to-select routes through `onSelectUnit`.
- Stars are deterministic (seeded), memoized with `useMemo`.
- Coordinate mapping: `unit.posX * scaleX`, `unit.posY * scaleY` where scale = `width/1000`.

### Required changes for Phase 14

The planner should add **new Konva layers in this order** (back-to-front):
1. Background (unchanged, `listening=false`)
2. **Command range layer — multi-CRC**: render N circles, one per `subFleetCommanderId` visible in my command chain (D-01). Drop the single `selectedUnit.commandRange > 0` gate. Listening=false.
3. **Fog/ghost layer (new, D-17)**: stale enemy positions rendered as `Circle` with dashed outline + reduced opacity. Listening=false.
4. Units layer (live units, listening=true)
5. **Succession FX layer (new, D-13/D-14/D-16)**: short-lived animated groups for flagship-destroyed ring flash, succession countdown overlay, and "command transferred" ring. Listening=false.
6. *(Optional)* Overlay layer for selection halos — already handled inside `TacticalUnitIcon` via internal `Circle`, may stay.

**Critical perf rules for Konva multi-layer scenes (confirmed pattern in existing code):**
- `listening={false}` on any non-interactive shape (already applied to stars/grid/letters).
- Avoid putting animated FX on the same Layer as static grids — each Layer is its own canvas.
- Use `Konva.Animation` only for continuous tweens. For tick-synced values (commandRange, countdown), rely on React re-renders from the Zustand reducer (one re-render per tick ≈ 1s, well below 16 ms frame budget).
- For 60 units × 4 layers, the bottleneck is the units layer re-render on every tick. Acceptable for 1 Hz tick rate.

---

## 4. `CommandRangeCircle.tsx` rewrite (D-03, D-04, FE-01)

**Current state** (`frontend/src/components/tactical/CommandRangeCircle.tsx`, 70 lines):
- Props: `x, y, radius, maxRadius, side: 'ATTACKER'|'DEFENDER'`
- Runs a **local `Konva.Animation`** that interpolates the inner circle's radius from 0 to `maxRadius` over 3000 ms in a loop.
- Only a single instance is ever mounted (gated on `selectedUnit` in `BattleMap`).
- Colors from `FACTION_COLORS` literal (empire `#4466ff`, alliance `#ff4444`, neutral `#888`).

**Required new shape** (per D-03, D-04, FE-01):
- Driven entirely by **server `commandRange` value** (already exposed in `TacticalUnitDto.commandRange: Double`).
- Remove the local animation loop.
- Render one instance **per sub-fleet commander** visible to the logged-in officer (D-01: my CRC + CRC of aligned commanders I can issue orders to).
- Props shape (proposed):
  ```ts
  interface CommandRangeCircleProps {
    cx: number; cy: number;                // scaled coords
    currentRadius: number;                  // scaled (current tick commandRange)
    maxRadius: number;                      // scaled reference (full size)
    side: 'ATTACKER' | 'DEFENDER';
    isMine: boolean;                        // gold highlight if true (D-11 layer a)
    isCommandable: boolean;                 // slightly brighter if I can command this commander
  }
  ```
- Interpolation strategy: **no JS interpolation needed**. The backend tick (~1 Hz) already writes the regenerated value. React re-renders the layer when `tacticalStore.units` changes. If the planner wants smooth transitions between ticks, use CSS-level tweening via a short `react-spring`-free lerp hook **only if UX testing demands it** — CONTEXT.md does NOT require smooth animation (D-03 says "backend tick의 실제 commandRange 값을 보간"; "보간" = server-driven interpolation, not local animation).
- Faction color rule (D-02): keep `empire #4466ff` / `alliance #ff4444`. On hover/select, apply a lighter hue variant (planner can use HSL lighten-10).

**CRC data pipeline (FE-01 integration):**
- Need `hierarchy` DTO data exposed on the battle state. The `TacticalBattleDto` currently has no hierarchy field (see §5). This must be added as part of FE-01.
- Frontend computes "which commanders should render CRC rings" by reading:
  - logged-in `myOfficerId` (from `officerStore`)
  - `attackerHierarchy` / `defenderHierarchy` from the DTO
  - For my side: render my own CRC if I am `fleetCommander` or any `subCommanders[].commanderId`. Additionally render CRCs of all commanders that sit below me in the chain (because I can issue orders down the chain).

**Assumption to verify:** CONTEXT.md D-01 says "내 지휘권 CRC만 표시 — 로그인 장교가 사령관/분함대장일 때 본인 CRC + 본인이 명령 가능한 아군 지휘관 CRC." So for a plain officer (non-commander), zero CRCs render. For a sub-commander, my own CRC + optionally my staff/vice's. For a fleet commander, mine + every sub-commander below me.

---

## 5. Backend DTO extension (D-21..D-24, `backend/game-app/.../dto/TacticalBattleDtos.kt`)

**Current `TacticalBattleDto` (lines 18–32 of `TacticalBattleDtos.kt`):**
```kotlin
data class TacticalBattleDto(
    val id: Long, val sessionId: Long, val starSystemId: Long,
    val attackerFactionId: Long, val defenderFactionId: Long,
    val phase: String, val startedAt: String, val endedAt: String? = null,
    val result: String? = null, val tickCount: Int,
    val attackerFleetIds: List<Long>, val defenderFleetIds: List<Long>,
    val units: List<TacticalUnitDto>,
)
```

**Current `TacticalUnitDto` (lines 34–55):**
```kotlin
data class TacticalUnitDto(
    val fleetId, officerId, officerName, factionId, side, posX, posY,
    hp, maxHp, ships, maxShips, training, morale, energy, formation,
    commandRange, isAlive, isRetreating, retreatProgress, unitType
)
```
Jackson uses Kotlin module defaults (see build.gradle). No custom serializer needed; Kotlin data classes serialize field-by-field.

**Current `BattleTickEventDto` (lines 67–73):**
```kotlin
data class BattleTickEventDto(
    val type: String,
    val sourceUnitId: Long = 0, val targetUnitId: Long = 0,
    val value: Int = 0, val detail: String = "",
)
```

### Additions required by CONTEXT.md

D-21 — `TacticalBattleDto` gains:
```kotlin
val attackerHierarchy: CommandHierarchyDto? = null,
val defenderHierarchy: CommandHierarchyDto? = null,
```

D-21 — new DTO:
```kotlin
data class CommandHierarchyDto(
    val fleetCommander: Long,                   // active commanding officer id
    val subFleets: List<SubFleetDto>,
    val successionQueue: List<Long>,            // ordered by rank desc
    val designatedSuccessor: Long? = null,
    val vacancyStartTick: Int = -1,             // -1 = no vacancy, else countdown basis
    val commJammed: Boolean = false,
    val jammingTicksRemaining: Int = 0,
    val activeCommander: Long? = null,
)
data class SubFleetDto(
    val commanderOfficerId: Long,
    val commanderName: String,
    val memberFleetIds: List<Long>,
    val commanderRank: Int,
)
```

D-22 — `TacticalUnitDto` gains:
```kotlin
val sensorRange: Double = 0.0,            // NEW — gin7 색적 범위; backend must compute
val subFleetCommanderId: Long? = null,    // which sub-fleet the unit belongs to (null = direct under fleet commander)
val successionState: String? = null,      // "PENDING_SUCCESSION" | null
val successionTicksRemaining: Int? = null,
val isOnline: Boolean = true,             // from TacticalBattleState.connectedPlayerOfficerIds
val isNpc: Boolean = false,               // from Officer.isNpc or similar
val missionObjective: String? = null,     // D-37 — from TacticalBattleState.missionObjectiveByFleetId
```

D-23 — `BattleTickEventDto.type` new values to document (no shape change):
```
FLAGSHIP_DESTROYED, SUCCESSION_STARTED, SUCCESSION_COMPLETED, JAMMING_ACTIVE
```
Use `sourceUnitId` = affected fleetId, `value` = ticks (for countdown events), `detail` = Korean label.

D-24 — `commandRange` keeps its existing meaning ("명령 통신 범위"); `sensorRange` is new and separate.

### Where the DTO builder pulls data from

`TacticalBattleService.toUnitDto` (lines 612–633) is the single DTO builder. It receives a `TacticalUnit` from `TacticalBattleState.units`. The builder must be extended:

- `subFleetCommanderId`: derive from `state.hierarchyByFleetId` OR iterate `state.attackerHierarchy.subCommanders` + `defenderHierarchy.subCommanders` to find which `SubFleet.unitIds` contains this unit. (Assumption: `TacticalBattleState` already carries hierarchy per side — confirm via `TacticalBattleEngine.kt:142` where `TacticalBattleState` is declared.)
- `successionState` / `successionTicksRemaining`: derive from `CommandHierarchy.vacancyStartTick` — if > -1 and this unit's commanderOfficerId matches the vacant fleetCommander, set "PENDING_SUCCESSION" and `currentTick - vacancyStartTick` → `30 - elapsed`.
- `isOnline`: `state.connectedPlayerOfficerIds.contains(unit.officerId)`
- `isNpc`: lookup via `officerRepository.findById(unit.officerId).isNpc` — BUT this is a DB hit per unit per tick. Cache it in `TacticalBattleState` on battle start (add `npcOfficerIds: Set<Long>`) to avoid hot path DB hits.
- `missionObjective`: `state.missionObjectiveByFleetId[unit.fleetId]?.name`

**sensorRange computation (D-19):** Backend needs a formula. Suggestion (planner to confirm with game designer):
```
sensorRange = baseSensorRange * (unit.energy.sensor / 20.0) * injuryModifier
```
Where `baseSensorRange` ≈ 150 units (smaller than commandRange when sensor slider is at default 10, larger when player dumps energy into sensors). Store as `val sensorRange: Double` in `TacticalUnit` (engine model) so tick loop already has it.

**Migration concern:** All new DTO fields must default to sane values so existing clients (if any during migration) do not crash. Use `= default` parameters on every new Kotlin field. Frontend `types/tactical.ts` must mark these fields optional (`?:`) until fully wired.

**Also update `BattleTickBroadcast.units` — uses the same `toUnitDto`, so it picks up the new fields automatically. The hierarchy fields are needed in both `TacticalBattleDto` (initial load) AND `BattleTickBroadcast` (per-tick updates if hierarchy changes). Add `attackerHierarchy/defenderHierarchy` to `BattleTickBroadcast` as well.**

---

## 6. `tacticalStore.onBattleTick` reducer extension

**Current** (`frontend/src/stores/tacticalStore.ts`, lines 76–96):
```ts
onBattleTick: (data: BattleTickBroadcast) => {
  const myId = get().myOfficerId;
  const myUnit = myId ? data.units.find((u) => u.officerId === myId) : undefined;
  set((state) => ({
    units: data.units,
    recentEvents: [...data.events, ...state.recentEvents].slice(0, 50),
    currentBattle: state.currentBattle
      ? { ...state.currentBattle, tickCount: data.tickCount, phase: data.phase, result: data.result ?? state.currentBattle.result, units: data.units }
      : null,
    myEnergy: myUnit?.energy ?? state.myEnergy,
    myFormation: myUnit?.formation ?? state.myFormation,
  }));
}
```

### Phase 14 additions

Add state fields (every new field is FE-only; server never reads it):

```ts
interface TacticalState {
  // ... existing ...
  attackerHierarchy: CommandHierarchyDto | null;
  defenderHierarchy: CommandHierarchyDto | null;

  /** D-20: stale enemy positions. FE-only; not sent to server. */
  lastSeenEnemyPositions: Record<number, { x: number; y: number; tick: number; ships: number; unitType: string; side: BattleSide }>;

  /** Succession FX bookkeeping — recent events we still want to render */
  activeFlagshipDestroyedFleetIds: number[]; // decay after 30 ticks
  activeSuccessionFleetIds: number[];        // fleets currently in PENDING_SUCCESSION
}
```

Rationale for `Record<number, ...>` over `Map<number, ...>`: Zustand 5 supports both, but `Record` serializes/devtools-inspects more cleanly and avoids the "Zustand + Immer + Map gotcha" (Zustand's default middleware-less set works fine with Maps, but Redux DevTools integrations treat Records better). D-20 says store it in the store; data shape is the planner's call.

**Fog (D-17/D-18) update logic each tick:**
```ts
// Compute vision cone for each enemy unit using hierarchy-shared vision
const myHierarchy = mySide === 'ATTACKER' ? data.attackerHierarchy : data.defenderHierarchy;
const alliedUnitsInMyChain = findAlliesInMyChain(data.units, myHierarchy, myOfficerId);
// D-18: shared vision among my chain
const visibleEnemies = data.units
  .filter(u => u.side !== mySide && u.isAlive)
  .filter(e => alliedUnitsInMyChain.some(a => distance(a, e) <= a.sensorRange));

// Update lastSeen for each visible enemy with current position
visibleEnemies.forEach(e => {
  next.lastSeenEnemyPositions[e.fleetId] = { x: e.posX, y: e.posY, tick: data.tickCount, ships: e.ships, unitType: e.unitType, side: e.side };
});
// Enemies NOT in visibleEnemies retain their stale entry (if any)
// Optionally prune entries older than N ticks (planner to set — CONTEXT.md "Claude's Discretion": 60 ticks suggested)
```

**Succession FX update:**
- On tick, compare `data.events` for `FLAGSHIP_DESTROYED` / `SUCCESSION_STARTED` / `SUCCESSION_COMPLETED` — push into `activeFlagshipDestroyedFleetIds` / `activeSuccessionFleetIds`, fire sonner toast, fire sound effect (D-15).
- Flagship flash auto-decays after 30 frames (~0.5 s wall clock given 1 Hz tick? No — use `setTimeout` from the reducer for the 0.5 s ring flash).

**BattleMap rendering rules from store:**
- `units`: filter to only `visibleEnemies ∪ allies` — but CONTEXT.md D-17 says fog ghosts the enemy outside sensor range; don't show their real position. Planner decision: render the raw `units[]` for allies + visible enemies; render `lastSeenEnemyPositions` for ghosts; the union of two sources. To avoid double-rendering a currently-visible enemy as both live and ghost, skip ghosts whose `fleetId` appears in `visibleEnemies`.

---

## 7. WebSocket subscription pattern (FE-01/02/04/05 + operations channel)

**Two patterns in the codebase:**

1. **High-level callback pattern** (`frontend/src/hooks/useWebSocket.ts`) — uses `connectWebSocket(worldId, { onTurnAdvance, onBattle, ... })` from `lib/websocket.ts`. Only supports hard-coded event buckets.
2. **Direct topic subscription** (`frontend/src/lib/websocket.ts:60`) — exposes `subscribeWebSocket(topic, callback)` returning an unsubscribe function. Used widely: `command-panel.tsx:295`, `battle/page.tsx:129`, `commands/page.tsx:132`, `tactical/page.tsx:62`, etc.

**For Phase 14, the planner should prefer `subscribeWebSocket(topic, cb)`** directly in components/stores, since it's the established pattern for per-topic hooks and supports multiple concurrent subscriptions.

### Topics used and to add

| Topic | Direction | Existing? | Used in |
|---|---|---|---|
| `/topic/world/{sessionId}/tactical-battle/{battleId}` | server→client | **yes** (TacticalBattleService.broadcastBattleState) | `tactical/page.tsx`, tacticalStore |
| `/topic/world/{sessionId}/events` | server→client | yes | `useWebSocket.ts`, multiple pages |
| `/topic/world/{sessionId}/battle` | server→client | yes | `battle/page.tsx` |
| `/topic/world/{sessionId}/operations` | server→client | **no — NEW (D-31)** | galaxyStore subscription (new) |
| `/app/command/{sessionId}/execute` | client→server | yes | `command-execution-panel.tsx:68` |

**New channel work:**
- **Backend:** `OperationPlanService` (command handlers + lifecycle service) must broadcast on `"/topic/world/{sessionId}/operations"` whenever status transitions happen (PENDING → ACTIVE → COMPLETED → CANCELLED). Use `messagingTemplate.convertAndSend(topic, payload)` — same pattern as `TacticalBattleService.processFlagshipDestructions()` at line 531 which broadcasts to `/topic/world/{sessionId}/events`.
- **Payload shape (proposed, planner to confirm):**
  ```ts
  interface OperationEvent {
    type: 'OPERATION_PLANNED' | 'OPERATION_STARTED' | 'OPERATION_COMPLETED' | 'OPERATION_CANCELLED';
    operationId: number;
    objective: 'CONQUEST' | 'DEFENSE' | 'SWEEP';
    targetStarSystemId: number;
    participantFleetIds: number[];
    status: 'PENDING' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
  }
  ```
- **Frontend:** `galaxyStore` gains `activeOperations: OperationEvent[]` + a subscription hook started from `(game)/layout.tsx` or `galaxy-map` component. On event, upsert/remove by `operationId`.

---

## 8. `useHotkeys` F1 binding (D-28)

`frontend/src/hooks/useHotkeys.ts` already supports arbitrary `key` strings including function keys — `e.key === 'F1'` matches. `preventDefault` is `true` by default, which will suppress the browser's built-in F1 help. Input/textarea focus is already excluded.

**Usage in a galaxy map toggle component:**
```ts
const [operationOverlayOn, setOperationOverlayOn] = useState(false);
useHotkeys([{
  key: 'F1',
  handler: () => setOperationOverlayOn(v => !v),
  description: '작전계획 오버레이',
}]);
```

**Conflict check:** `grep "key: 'F1'"` should return zero existing bindings. Planner must run this grep before merging.

---

## 9. Sound effects (D-15)

`frontend/src/hooks/useSoundEffects.ts` — Web Audio API synth with per-sound tone sequences in a `FREQUENCIES` record. Types: `turnComplete | battleStart | newMessage | notification`.

**Add 2 new sound types for D-15:**
```ts
type SoundType = '... existing ...' | 'successionStart' | 'flagshipDestroyed';

const FREQUENCIES = {
  // ... existing ...
  successionStart: { freq: [330, 277, 220], duration: [150, 150, 300], type: 'triangle' },
  flagshipDestroyed: { freq: [110, 82, 55], duration: [100, 150, 400], type: 'sawtooth' },
};
```
No new dependency. Planner is free to tune the tone sequence; the point is to not block on BGM design.

---

## 10. Sonner toast conventions (D-13, D-14, D-16)

- Already initialized. `useWebSocket.ts` shows the pattern: `toast.info`, `toast.warning`, `toast.success`. Use `{ duration, id }` to de-dup rapid events.
- For commander-fallen toast (D-13): `toast.warning('사령관 ${name} 전사, 30틱 후 승계', { duration: 6000, id: 'succ-${officerId}' })`
- For command-takeover toast (D-16): `toast.success('${name} 지휘 인수', { duration: 4000 })`
- For flagship destroyed flash: the flash is a Konva layer render, NOT a toast. The toast only fires on SUCCESSION_STARTED.
- Check `frontend/src/app/layout.tsx` for existing `<Toaster />` mount. Verify z-index is above the BattleMap stage (typically 9999 by default).

**Assumption to verify:** There is exactly one `<Toaster />` already mounted in the app shell; planner can reuse without re-mounting.

---

## 11. Proposal path (D-10): Shift+click hookup

`frontend/src/components/game/proposal-panel.tsx:45` — `ProposalPanel` component takes `generalId` and uses `useCommandStore()` (fields: `pendingProposals`, `myProposals`, `fetchPendingProposals`, `fetchMyProposals`, `resolveProposal`). It displays lists but does NOT currently expose a "create proposal" entry point from disabled buttons.

**Gap:** `commandStore` likely has a `createProposal(commandCode, target)` action. Planner must verify and, if missing, expose it. Without it, D-10 Shift+click cannot wire to any backend.

**Integration plan (FE-03 proposal path):**
- `command-execution-panel.tsx` — wrap each command button with a handler that checks FE-side gating (see §12). If gated and user Shift+clicks, call `createProposal` instead of `execute`.
- Show a `Sonner` toast "제안을 상위자에게 전송" on success.
- Do NOT open a new panel; reuse the existing `ProposalPanel` for viewing pending proposals.

**Assumption to verify:** The proposal flow has a concrete `createProposal` endpoint in `/lib/gameApi.ts` or `commandStore`. Planner must confirm. If not, FE-03 scope expands to include the store action.

---

## 12. FE gating logic (D-09, D-11, D-12)

**D-12 rule:** FE computes the gating; server double-checks on execution.

Given:
- `myOfficerId` from `officerStore`
- `attackerHierarchy` / `defenderHierarchy` from `tacticalStore.currentBattle`
- target `TacticalUnit` (from click/select)

**Function (suggested, planner refines):**
```ts
function canCommandUnit(
  myOfficerId: number,
  myHierarchy: CommandHierarchyDto | null,
  targetUnit: TacticalUnit
): { allowed: boolean; reason?: 'OUT_OF_CHAIN' | 'OUT_OF_CRC' | 'JAMMED' } {
  if (!myHierarchy) return { allowed: false, reason: 'OUT_OF_CHAIN' };
  if (myHierarchy.commJammed) return { allowed: false, reason: 'JAMMED' };

  // Am I the fleet commander?
  if (myHierarchy.fleetCommander === myOfficerId || myHierarchy.activeCommander === myOfficerId) {
    // I can command any unit in my fleet
    return { allowed: true };
  }

  // Am I a sub-commander? If so, only my sub-fleet
  const mySubFleet = myHierarchy.subFleets.find(sf => sf.commanderOfficerId === myOfficerId);
  if (mySubFleet && mySubFleet.memberFleetIds.includes(targetUnit.fleetId)) {
    return { allowed: true };
  }

  return { allowed: false, reason: 'OUT_OF_CHAIN' };
}
```

**D-11 visual layers:**
- Layer (a) **Gold border always shown**: render a 1px gold outline around every unit in my command chain. Put this in `TacticalUnitIcon.tsx` as an extra `Rect`/`RegularPolygon` behind the main shape when a new `isUnderMyCommand` prop is true.
- Layer (b) **Selection batch in `InfoPanel`**: when a unit is selected, show a badge "본인의 지휘권 하 유닛입니다" (for allies in my chain) or "국의 지휘관" (for the fleet commander, when that's me). Add a new prop `selectedUnitCommandRelation: 'self' | 'subordinate' | 'peer' | 'out-of-chain' | null` to `InfoPanel`.

**D-09 disabled button tooltip:**
- Check `command-execution-panel.tsx` — each button currently calls `handleExecute(cmd)` without any gating (line 63). Planner must wrap the button with a Radix `Tooltip` (already in deps) and set `disabled` when gating fails. Tooltip content: "지휘권 없음 / Shift+클릭으로 상위자에게 제안".

---

## 13. Sub-fleet assignment drawer (FE-02)

**Drawer host:** `responsive-sheet.tsx` is a controlled `Sheet` wrapper with `side='right'` on desktop, `side='bottom'` on mobile (`w-[400px] sm:w-[540px]`). Already supports a controlled `open`/`onOpenChange` prop — reuse without modification.

**Content layout (per 14-UI-SPEC.md and D-05, D-07, D-08):**
- Header: "분함대 배정" + phase indicator (PREPARING / ACTIVE)
- Body: DndContext with buckets for:
  - 부사령관 (Vice Commander)
  - 참모장 (Chief of Staff)
  - 참모 1–6 (Staff Officers ×6)
  - 전계 (Main Fleet / Direct Command)
  - Unassigned pool
- Each draggable tile = `TacticalUnit` (fleetId, officer name, ship count, flagship marker).
- Drop zones are `useDroppable({ id: bucketId })`.
- Draggable items are `useDraggable({ id: `unit-${fleetId}` })`.
- On drop: dispatch `AssignSubFleet` or `ReassignUnit` via existing `publishWebSocket('/app/command/{sessionId}/execute', { officerId, commandCode, args: { ... } })` (same pattern as `command-execution-panel.tsx:68`). Command codes and args come from Phase 9 D-16 / Phase 9 CMD-05; verify against `command` table or backend `CommandExecutor`.

**D-06 gating on drag start (ACTIVE phase):**
- Only `CMD-05` conditions: target unit outside CRC AND velocity ≈ 0. Compute on drag start:
  ```ts
  const sourceCommander = /* the commander the unit currently reports to */;
  const isOutsideCRC = distance(unit, sourceCommander) > sourceCommander.commandRange;
  const isStopped = Math.hypot(unit.velX, unit.velY) < 0.01; // velX/velY are NOT in TacticalUnitDto — need to add, OR show "not stopped" when derived
  ```
- **Gap:** `velX`/`velY` are on the backend `TacticalUnit` but not exported via `TacticalUnitDto`. The planner must either add them to the DTO OR define "stopped" via some other flag (e.g., add `isStopped: Boolean` derived backend-side).
- If not allowed, draggable must render in gray + cursor `not-allowed`, and drop handler should refuse.

**DragOverlay**: show the dragged unit tile at cursor with faction color. Use `DragOverlay` from `@dnd-kit/core` to avoid layout shift.

**Sensors:** `PointerSensor` (mouse/touch) + `KeyboardSensor` (accessibility). Activate pointer with `activationConstraint: { distance: 4 }` so clicks don't start drags.

---

## 14. Battle end modal (D-32..D-34)

Data source: `TacticalBattleDto.result` (string: "attacker_win" / "defender_win" / "draw"), `TacticalBattleDto.units` (for survival/destruction rows), `tacticalStore.recentEvents` (for final ship counts), `BattleTickEventDto` with type `battle_end` (if exists — else use `phase === 'ENDED'`).

**Modal trigger:** Watch `currentBattle.phase` in `tacticalStore`. When it transitions `ACTIVE → ENDED`, open a full-screen dialog. Use `@radix-ui/react-alert-dialog` (already installed) or `SheetContent side='top'` — planner decides.

**Row data (D-33):** For each participating unit, compute base merit + operation bonus:
- Base merit: `TacticalBattleService.computeBaseMerit()` (line 686) → 100 × survivalRatio. NOT exposed via DTO — need a new `FinalMeritRow` structure in `TacticalBattleHistoryDto` OR a new `/api/{sessionId}/battles/{battleId}/summary` endpoint.
- Operation bonus multiplier: 1.5× if `unit.fleetId ∈ state.operationParticipantFleetIds` (Phase 12 D-11).
- Display format: "기본 {base} + 작전 +{bonus} = 총 {total}"

**Gap:** There is no existing endpoint that returns the per-unit merit breakdown. Planner must add one (new Plan):
```kotlin
data class BattleSummaryDto(
    val battleId: Long,
    val winner: String?,
    val durationTicks: Int,
    val rows: List<BattleSummaryRow>,
)
data class BattleSummaryRow(
    val fleetId: Long, val officerId: Long, val officerName: String,
    val side: String, val survivingShips: Int, val maxShips: Int,
    val baseMerit: Int, val operationMultiplier: Double, val totalMerit: Int,
    val isOperationParticipant: Boolean,
)
```
This is persisted to DB on `endBattle()` (already runs inside `@Transactional`).

---

## 15. Status markers / NPC / offline (D-35, D-36, D-37)

**TacticalUnitIcon.tsx extension:**
- Add `isOnline: boolean`, `isNpc: boolean` props.
- In the top-right of the unit bounding box (≈ `+6, -6` offset), render a small shape:
  - `●` green (`#22c55e`) if `isOnline && !isNpc`
  - `○` gray (`#6b7280`) stroke-only if `!isOnline && !isNpc`
  - `🤖` (text or small icon) if `isNpc`
- Keep icon opacity at 1 regardless of online state — per D-35, opacity is already used for "파괴" (destruction). Don't confuse the two.

**InfoPanel NPC objective (D-36):**
- When a selected unit has `isNpc && missionObjective`, show rows:
  - 현재 목적: CONQUEST/DEFENSE/SWEEP (Korean labels: 점령/방어/소탕)
  - 목표: {starSystemName} (look up from `galaxyStore.getSystem(targetStarSystemId)`)
  - 추적: {target officer name} (optional; if AI is chasing a specific enemy, derive from `unit.targetFleetId`)
- Add a dashed line on `BattleMap` from the NPC unit toward its target when selected. Konva `Line` with `dash=[5,5]`, `listening=false`.

---

## 16. Frontend test setup (`pnpm test`, `pnpm e2e`)

**Vitest config:** `vitest` is used with `@vitejs/plugin-react`, `@testing-library/react`, `@testing-library/jest-dom`, `jsdom`. Colocated test files (pattern `*.test.ts` / `*.test.tsx`). Existing: `frontend/src/components/responsive-sheet.test.tsx`.

**Playwright:** `@playwright/test` ^1.58.2, chromium-only install via `pnpm e2e:setup`. Existing tests: `e2e/oauth-gate.spec.ts`. `pnpm e2e:oauth` runs the OAuth gate test. No existing visual snapshot baselines for BattleMap.

**Coverage expectations for Phase 14:**
- Unit tests (Vitest, colocated):
  - `CommandRangeCircle.test.tsx` — given props, assert rendered `Circle` radius = current/max; assert no `Konva.Animation` started (per D-03).
  - `tacticalStore.test.ts` — reducer tests for `onBattleTick` with hierarchy, fog update, event processing.
  - `canCommandUnit.test.ts` — gating logic table tests (fleet commander, sub-commander, plain officer, out-of-chain, jammed).
  - `subFleetAssignmentDrawer.test.tsx` — dnd-kit headless tests (use `fireEvent` for drop simulation or mock sensors).
  - `lastSeenPositions.test.ts` — fog reducer staleness prune.
- Playwright:
  - `e2e/tactical-crc.spec.ts` — navigate to tactical page, seed a battle with hierarchy, assert CRC rings render with expected class/attribute.
  - `e2e/battle-end-modal.spec.ts` — simulate ended battle, assert modal shows breakdown rows.
- **Visual snapshot baselines must be generated after removing R3F** to avoid false-positive diffs.

---

## Validation Architecture

*(Nyquist Dimension 8 — how to verify each FE requirement without relying on manual observation alone.)*

### FE-01: CRC visualization

| Layer | Method | Passing criteria |
|---|---|---|
| Unit | `CommandRangeCircle.test.tsx` | Given currentRadius=120, maxRadius=300 → Circle with radius=120 renders; no `Konva.Animation` instance constructed |
| Unit | `battleMap-crc.test.tsx` | Given hierarchy with 2 sub-commanders and myOfficerId = fleet commander → 3 CommandRangeCircle instances mount |
| Integration | `tacticalStore.test.ts` | `onBattleTick` merges new hierarchy from DTO; reducer does not drop old units |
| E2E | `e2e/tactical-crc.spec.ts` | Playwright: seed battle, open tactical page, assert `canvas` contains ring elements via Konva stage.toJSON() snapshot |
| Manual UAT | Korean checklist | "사령관 로그인 시 내 CRC + 모든 분함대장 CRC가 진영색으로 표시됨"; "명령 발령 직후 CRC 반경이 서버 값에 따라 축소되고 다시 확장됨" |

### FE-02: Sub-fleet assignment drawer

| Layer | Method | Passing criteria |
|---|---|---|
| Unit | `subFleetAssignmentDrawer.test.tsx` | `useDraggable` returns attributes for every unit tile; drop event dispatches `publishWebSocket('/app/command/.../execute', { commandCode: 'AssignSubFleet', ... })` |
| Unit | `dragGating.test.ts` | In ACTIVE phase, unit inside CRC → draggable returns `disabled=true`; unit outside CRC and stopped → draggable enabled |
| Integration | `tacticalStore+drawer.test.ts` | After successful assignment, hierarchy in store reflects the new subFleets grouping on next broadcast |
| E2E | `e2e/sub-fleet-drawer.spec.ts` | Playwright: open drawer, drag tile from pool to 부사령관 bucket, assert outgoing WebSocket frame |
| Manual UAT | — | "PREPARING 페이즈에서 60유닛을 자유롭게 드래그하여 부사령관/참모 버킷에 배정"; "ACTIVE 페이즈에서 CRC 내 유닛은 회색 차단" |

### FE-03: Command gating

| Layer | Method | Passing criteria |
|---|---|---|
| Unit | `canCommandUnit.test.ts` | Table tests: fleet commander / sub-commander / plain officer / jammed / delegated — all 5 cases pass |
| Unit | `command-execution-panel.test.tsx` | Gated command button has `disabled`, tooltip content includes "지휘권 없음" |
| Unit | `proposal-shift-click.test.tsx` | Shift+click on gated button calls `commandStore.createProposal` instead of `execute` |
| Integration | `infoPanel-relationBadge.test.tsx` | Selecting a subordinate renders "본인의 지휘권 하 유닛입니다" badge |
| E2E | `e2e/gating.spec.ts` | Playwright: login as sub-commander, click gated command, assert button is disabled, Shift+click asserts proposal created |
| Manual UAT | — | "본인 지휘권 하 유닛은 금색 테두리로 구분"; "Shift+클릭 제안 경로 동작" |

### FE-04: Succession feedback

| Layer | Method | Passing criteria |
|---|---|---|
| Unit | `successionReducer.test.ts` | `onBattleTick` with SUCCESSION_STARTED event adds fleetId to `activeSuccessionFleetIds`; COMPLETED removes it |
| Unit | `flagshipFlash.test.tsx` | Layer mounts a ring flash Group on FLAGSHIP_DESTROYED; unmounts after 500 ms |
| Unit | `toastSuccession.test.ts` | Sonner `toast.warning` called with "30틱 후 승계" message once per event (id de-dupes) |
| E2E | `e2e/succession.spec.ts` | Playwright: trigger succession from mock backend, assert countdown text visible, assert toast visible |
| Manual UAT | — | "기함 격침 순간 해당 위치에만 0.5초 링 플래시"; "승계 완료 시 '지휘 인수' 링 + 토스트" |

### FE-05: Fog of war

| Layer | Method | Passing criteria |
|---|---|---|
| Unit | `fogReducer.test.ts` | Enemy in sensor range → lastSeen updated; enemy leaves range → lastSeen preserved with stale tick; new entry never overwrites if unit dead |
| Unit | `hierarchyVision.test.ts` | D-18: ally in my chain sees enemy → visibility shared; ally NOT in chain sees enemy → not visible to me |
| Unit | `fogLayer.test.tsx` | Ghost rendered with dashed stroke and `opacity=0.35` for stale positions; live enemy not rendered as ghost |
| E2E | `e2e/fog.spec.ts` | Playwright: seed battle with enemy outside sensor range, assert ghost at stale coords; move enemy back into range, assert ghost replaced with live marker |
| Manual UAT | — | "색적 범위 밖 적 유닛이 마지막 목격 위치에 반투명으로 표시되고 실시간 추적 불가" |

### Cross-cutting verification

- **Korean UI text parity** (`verify-docs-parity` skill): all new strings in Korean per CLAUDE.md.
- **Type parity** (`verify-type-parity` skill): `TacticalUnitDto` Kotlin ↔ `TacticalUnit` TypeScript must match field-for-field after extensions.
- **API parity** (`verify-api-parity` skill): every new controller method or WebSocket channel has a matching FE consumer.
- **Frontend parity script** (`scripts/verify/frontend-parity.mjs`): re-run after UI integration; expected to remain green.
- **Performance budget** (manual + devtools):
  - BattleMap FPS ≥ 50 with 60 units + 8 CRC rings + fog ghosts + dashed NPC mission line on a MacBook Pro 2020.
  - Tick→render latency ≤ 100 ms at 1 Hz broadcast.
- **R3F removal smoke check**: `pnpm typecheck && pnpm build` must succeed, `pnpm test` must pass, no `import.*three` remains in `frontend/src`.
- **Bundle size impact**: Track `pnpm build` output size before/after. R3F removal should drop ≥150 kB gzip; dnd-kit adds ≈20 kB gzip. Net negative expected.

### Regression guardrails

- Add a Vitest test that grep-fails if anyone re-introduces `Konva.Animation` in `CommandRangeCircle.tsx` (per D-03 rule) — read the file text and assert it does not contain the substring `Konva.Animation`.
- Add a Vitest test asserting `frontend/package.json` does NOT contain `@react-three/fiber` after removal.
- Add `verify-frontend-parity` run to the pre-commit or PR workflow for this phase.

---

## Open questions for the planner

1. **sensorRange formula** — confirm with game designer: base value, energy slider coefficient, injury modifier. CONTEXT.md D-19 says "에너지 sensor 슬라이더 기반으로 백엔드에서 계산" but does not specify the exact formula.
2. **velX/velY exposure** — to implement D-06 "정지 조건" on the drawer, we need either `velX/velY` on `TacticalUnitDto` or a derived `isStopped: Boolean`. Planner should pick one and propagate.
3. **commandStore.createProposal existence** — verify the FE store already has a proposal-creation action. If not, scope expands.
4. **Radix vs sheet for battle end modal** — D-32 says "전체 화면 모달"; `@radix-ui/react-alert-dialog` is installed and a natural fit. Planner to confirm visual treatment matches UI-SPEC.md.
5. **Battle summary endpoint** — new backend endpoint needed to expose per-unit merit breakdown (see §14). Planner should scope this as its own plan.
6. **Operation WebSocket channel schema** — confirm payload with backend Phase 12 patterns; finalize the interface and bind to `galaxyStore`.
7. **R3F collateral** — double-check no visual regressions in lobby/login (earlier commits mention `galaxy` 3D preview); R3F removal may inadvertently break non-tactical pages if they import `@react-three/fiber` transitively.
8. **Ghost TTL** — CONTEXT.md "Claude's Discretion" for fog entry expiry. Suggest 60 ticks; planner confirms.

## Assumptions to verify before planning

- dnd-kit installs cleanly on React 19.2.3 (no peer-dep warning).
- `@radix-ui/react-dialog` or `@radix-ui/react-alert-dialog` is the preferred modal primitive (not `react-resizable-panels`, which is in deps but scoped to split-pane layouts).
- `TacticalBattleState` already stores per-side `CommandHierarchy` (confirm by reading `TacticalBattleEngine.kt:142`). If not, FE-01 scope expands to populate the hierarchy on battle start.
- `connectedPlayerOfficerIds` is authoritative for online state (see `TacticalBattleService.onPlayerConnected/onPlayerDisconnected` lines 549–561 — yes, confirmed).
- No existing feature uses `F1` as a hotkey (planner runs `grep "'F1'" frontend/src` before merging).
- Sonner's `<Toaster />` is mounted exactly once in the app shell.

## RESEARCH COMPLETE

All 5 requirements (FE-01..FE-05) have actionable research. DTO extensions, Konva layer layout, dnd-kit integration pattern, WebSocket channel additions, and validation strategy are specified. The planner can proceed with wave-based plan generation using this as the technical substrate.
