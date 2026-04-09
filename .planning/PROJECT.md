# Open LOGH — gin7 전면 재작성 프로젝트

## 개요

OpenSamguk(삼국지 웹게임)을 포크하여 은하영웅전설VII(gin7, 2004 BOTHTEC) 기반 웹 MMO로 전환하는 프로젝트.
현재 엔티티 이름과 일부 모델은 LOGH로 변환했으나, **게임 로직 자체는 여전히 삼국지 기반**이다.
이 계획은 삼국지 게임 로직을 gin7 매뉴얼 기준으로 **전면 재작성**하기 위한 것이다.

## 핵심 가치

gin7의 핵심인 "조직 시뮬레이션" — 직무권한카드 기반 커맨드 시스템으로 다수 플레이어가 계급 구조 안에서 명령/제안/인사/정치를 수행하며, 원작의 라인하르트나 양웬리의 입장을 체험할 수 있는 것.

## 현재 상태 (v1.0 완료 기준)

### 이미 구현된 것 (유지)
- **DB 스키마**: V1~V44 마이그레이션, gin7 도메인 용어로 전환 완료
- **엔티티 모델**: Officer(8스탯), Planet, Faction, Fleet, SessionState 등 48개
- **실시간 엔진**: TickDaemon/TickEngine (1초 tick, 24배속), GameTimeConstants
- **CP 시스템**: PCP/MCP 분리, 회복/대용(2배) 로직, CpService
- **직무권한카드**: 82종 PositionCard, 7개 CommandGroup, 권한 게이팅
- **조직 구조**: 6종 UnitType, CrewSlotRole, UnitCrew, FormationCapService
- **은하맵**: 80개 성계, StarSystem/StarRoute, React Konva 2D
- **계급/인사**: 11단계 RankTitle, 공적/평가/명성 포인트
- **시나리오**: 10개 시나리오 JSON, 커스텀 캐릭터 생성
- **전술전 기초**: TacticalBattle 엔티티, BattleTriggerService, SVG 맵
- **정치 기초**: CoupPhase, CouncilSeat, Election, FezzanLoan 엔티티
- **NPC AI 기초**: PersonalityTrait(5종), OfflinePlayerAIService
- **승리/세션**: VictoryService(3조건, 4등급), SessionLifecycleService
- **통신**: ChatService(3스코프), MessageService(120통 제한)
- **인프라**: Spring Boot 3 + Kotlin, Next.js 15, PostgreSQL, Redis, WebSocket/STOMP

### 핵심 문제점 (재작성 대상)
1. **커맨드 시스템 불일치**: CommandRegistry에 93개 삼국지 커맨드가 실제 동작, commands.json의 gin7 81종은 문서용
2. **전투 로직 미완**: 삼국지 수치비교 자동전투 잔재, gin7 에너지/무기/색적/요새포 미구현
3. **경제 미구현**: gin7 매뉴얼에서도 "미구현" 명시, 조병창/세율/창고/차관 필요
4. **함종 시스템 부재**: 11함종×서브타입 전투 스탯 미반영, 기함 시스템 미완
5. **NPC 데이터**: general_pool.json이 여전히 삼국지 인물
6. **프론트엔드 삼국지 잔재**: city/nation/troop 용어, 삼국지 스타일 UI

## 기술 스택

- Backend: Spring Boot 3 (Kotlin 2.1.0) + JPA + PostgreSQL 16 + Redis 7
- Frontend: Next.js 16 + React 19 + TypeScript + Tailwind CSS + React Konva + Three.js
- 실시간: WebSocket/STOMP + 1초 tick (24배속)
- 빌드: Gradle 8 + pnpm
- 배포: Docker + GitHub Actions + EC2

## 참조 문서

- `CLAUDE.md` — 도메인 매핑, 스탯, 조직, 계급, 함종
- `/Users/apple/Downloads/gin7manualsaved.pdf` — gin7 공식 매뉴얼 (101페이지)
- `docs/REWRITE_PROMPT.md` — 전면 재작성 상세 프롬프트
- `docs/reference/gin4ex_wiki.md` — gin4 EX 위키 요약
- `docs/reference/unit_composition.md` — 부대 편성 규칙
- `docs/reference/scenarios_detail.md` — 시나리오 상세
- `backend/shared/src/main/resources/data/commands.json` — gin7 81종 커맨드 정의
- `backend/shared/src/main/resources/data/ship_stats_empire.json` — 제국 함선 스탯
- `backend/shared/src/main/resources/data/ship_stats_alliance.json` — 동맹 함선 스탯
- `backend/shared/src/main/resources/data/ground_unit_stats.json` — 육전병 스탯

## Current Milestone: v2.1 전술전 지휘체계 + AI

**Goal:** 전술전 지휘체계(작전계획-발령 연동, 부대별 지휘권, 커맨드레인지서클 기반 명령 전달)와 전술/전략 AI를 구현

**Target features:**
- 작전계획-발령 → 전술전 연동 (작전 목적이 전술 AI 기본 행동 결정)
- 부대별 지휘권 분배 (온라인→계급→평가→공적 순)
- 커맨드레인지서클 기반 명령 전달 (서클 내 유닛만 지휘)
- 지휘권 변경 커맨드 (서클 밖 + 정지 유닛만)
- 전술 AI (오프라인/NPC 유닛 자동 행동)
- 성격 기반 전술 차이
- 기함 격침 → 지휘 승계 (공백 시간 + 차순위 자동)
- 전략 AI 보강 (전술전 진입 시 AI 행동)

### Completed: v2.0 gin7 게임 로직 전면 재작성
- 삼국지 로직 완전 제거, 함종 시스템(11종×서브타입), 81종 커맨드
- 전술전 엔진(에너지/무기/진형/색적/요새포/지상전), 경제 시스템
- AI 시스템(성격기반/진영AI), 프론트엔드 재작성, 10개 시나리오

## 제약사항

- DB 마이그레이션은 V45__ 이후로 추가
- 기존 엔티티 필드 중 삼국지 전용은 마이그레이션으로 제거
- gin7 매뉴얼의 게임 메카닉스를 최대한 충실히 재현
- 리얼타임 (1초 tick = 24 게임초) 기반
- 모든 UI 텍스트는 한국어
- 삼국지 용어/개념 완전 제거

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd:transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-04-09 after Phase 13 complete (전략 AI — SAI-01/02 validated)*
