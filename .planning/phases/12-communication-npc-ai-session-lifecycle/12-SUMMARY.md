---
phase: "12"
plan: "all"
subsystem: "NPC AI, Victory, Session Lifecycle, Communication"
tags: [npc-ai, victory, session, chat, mail, personality]
dependency_graph:
  requires: [phase-10, phase-11]
  provides: [npc-personality-ai, victory-conditions, session-lifecycle, chat-system, mail-system]
  affects: [officer-entity, session-state, tick-engine, websocket]
tech_stack:
  added: []
  patterns: [personality-trait-enum, stat-weighted-ai, websocket-chat-scopes, mailbox-cap]
key_files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/ai/PersonalityTrait.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/OfflinePlayerAIService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/VictoryService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/SessionLifecycleService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/ChatService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/AddressBookService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/controller/VictoryController.kt
    - backend/game-app/src/main/kotlin/com/openlogh/controller/SessionLifecycleController.kt
    - backend/game-app/src/main/kotlin/com/openlogh/controller/ChatController.kt
    - backend/game-app/src/main/kotlin/com/openlogh/controller/MailController.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/SessionRanking.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/SessionRankingRepository.kt
    - backend/game-app/src/main/resources/db/migration/V38__add_personality_column.sql
    - backend/game-app/src/main/resources/db/migration/V39__add_victory_result_and_session_ranking.sql
    - frontend/src/types/victory.ts
    - frontend/src/types/chat.ts
    - frontend/src/lib/victoryApi.ts
    - frontend/src/lib/chatApi.ts
    - frontend/src/stores/victoryStore.ts
    - frontend/src/stores/chatStore.ts
    - frontend/src/components/game/victory-banner.tsx
    - frontend/src/components/game/chat-panel.tsx
    - frontend/src/components/game/mail-panel.tsx
    - frontend/src/app/(game)/victory/page.tsx
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Officer.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/SessionState.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/ai/OfficerAI.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/ai/AIContext.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/MessageService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/MessageRepository.kt
decisions:
  - Personality stored as VARCHAR column (not meta JSONB) for first-class query support
  - Stat inference for offline player personality uses military/defensive/political score comparison
  - Victory check runs every 60 ticks (1 real-time minute) to avoid excessive DB queries
  - Chat reuses existing Message entity with new mailboxCode prefixes (chat_planet, chat_faction, chat_global)
  - Mailbox 120-cap deletes oldest messages on overflow rather than blocking sends
  - SessionRanking persisted to dedicated table for fast post-game queries
metrics:
  duration: "14min"
  completed: "2026-04-06"
  tasks: 13
  files: 30
---

# Phase 12: Communication, NPC AI & Session Lifecycle Summary

NPC AI personality system (5 traits with stat-weighted decision biases), 3 victory conditions with 4-tier evaluation, session lifecycle management with rankings and restart, location-scoped WebSocket chat, and enhanced mail with 120-cap and address book.

## What Was Built

### Plan 01: NPC AI Personality Enhancement
- **PersonalityTrait enum**: AGGRESSIVE, DEFENSIVE, BALANCED, POLITICAL, CAUTIOUS with Korean labels
- **PersonalityWeights**: stat multipliers per trait (e.g., AGGRESSIVE: attack 1.5x, defense 0.7x)
- **classifyGeneral** updated to apply personality weights when comparing command/intelligence stats
- **personalityBias** method adjusts action selection probabilities per trait
- **OfflinePlayerAIService**: detects inactive players (30min threshold), infers personality from stats, delegates to OfficerAI
- **V38 migration**: personality VARCHAR(20) and last_access_at columns on officer table

### Plan 02: Victory Conditions Service
- **VictoryService** with 3 condition checks:
  - VIC-01: Capital capture (enemy capital owned by another faction) -> DECISIVE
  - VIC-02: System threshold (enemy at 3 or fewer systems) -> DECISIVE/LIMITED
  - VIC-03: Time limit UC801.7.27 (population comparison) -> LOCAL/DEFEAT
- **VictoryTier** 4-tier evaluation: DECISIVE/LIMITED/LOCAL/DEFEAT
- **VictoryController** REST API: GET /victory, POST /victory/check
- **V39 migration**: victory_result JSONB + status on session_state, session_ranking table

### Plan 03: Session Lifecycle Management
- **SessionLifecycleService**: endSession (freeze + rank), calculateRankings, restartSession
- **Ranking formula**: meritPoints*2 + rank*1000 + kills*50 + territory*200
- **SessionRanking entity** with JSONB stats for flexible per-officer data
- **SessionLifecycleController**: POST /end, GET /rankings, POST /restart
- Restart preserves player accounts, resets game state via ScenarioService

### Plan 04: Communication System
- **ChatService**: 3 scopes (PLANET/FACTION/GLOBAL) with WebSocket broadcast
- **Rate limiting**: 2-second per-officer cooldown using ConcurrentHashMap
- **WebSocket channels**: /topic/chat/{sessionId}/{scope}/{scopeId}
- **AddressBookService**: previous contacts + faction members + name search
- **MessageService enhanced**: 120-message cap with auto-cleanup, mailbox counts
- **MailController**: count, addressbook, search endpoints

### Plan 05: Frontend UI
- **Victory page** (/victory): tier banner, faction result cards, rankings table with score/merit/kills/territory
- **VictoryBanner**: overlay component for session end notification with link to details
- **ChatPanel**: 3-scope tabs, message list, rate-limited input, collapsible
- **MailPanel**: inbox counts (X/120), compose form with address book search
- **Zustand stores**: victoryStore (WebSocket event handler), chatStore (scope switching, history)

### Plan 06: Integration Verification
- Backend compiles successfully (Java 23, Gradle 8.12)
- Frontend builds successfully with /victory route accessible
- Fixed VictoryPage to use useWorldStore instead of non-existent useGameStore.worldId

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] VictoryPage wrong store reference**
- **Found during:** Plan 06 Task 2
- **Issue:** VictoryPage imported useGameStore which has no worldId property
- **Fix:** Changed to useWorldStore with currentWorld?.id
- **Files modified:** frontend/src/app/(game)/victory/page.tsx
- **Commit:** 48186dd2

## Commits

| # | Hash | Message |
|---|------|---------|
| 1 | f11998e0 | feat(12-01): add PersonalityTrait enum, weights, and Officer entity update |
| 2 | ac7d5b66 | feat(12-01): personality-driven AI decisions and offline player handling |
| 3 | e8208a4f | feat(12-02): victory conditions service with 3 checks and 4-tier evaluation |
| 4 | 9452eff6 | feat(12-03): session lifecycle with rankings, hall of fame, and restart |
| 5 | 636cb1c7 | feat(12-04): location-scoped chat and enhanced mail system |
| 6 | a8750ac1 | feat(12-05): frontend victory screen, chat panel, and mail UI |
| 7 | 48186dd2 | fix(12-06): fix VictoryPage to use useWorldStore instead of useGameStore |

## Known Stubs

None - all components are wired to backend APIs and WebSocket channels. The MailPanel compose form uses the existing MessageService send flow (not a stub). Chat send is delegated via onSend prop to parent component for WebSocket integration.

## Self-Check: PASSED

All 21 created files verified present. All 7 commits verified in git log. Backend compiles (Java 23). Frontend builds successfully.
