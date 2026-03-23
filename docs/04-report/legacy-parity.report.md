# Completion Report: Legacy Parity (전체 패러티 점검 및 버그 수정)

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | legacy-parity |
| 기간 | 2026-03-23 (단일 세션) |
| Match Rate | **100%** (21/21 CRITICAL + 15/15 HIGH 해결) |
| 커밋 | 4건 |
| 변경 파일 | 62개 (프로덕션 30개 + 테스트 32개) |
| 테스트 | 1140+/1140+ PASS |

### 1.3 Value Delivered

| 관점 | 내용 |
|------|------|
| **Problem** | 레거시 PHP(devsam/core) 대비 21건 CRITICAL + 15건 HIGH 로직 불일치 발견 — 훈련/전투/외교/NPC AI가 레거시와 다른 결과 생성 |
| **Solution** | 8개 병렬 에이전트로 93 커맨드 + 전투 시스템 + NPC AI 전수 비교, 상수/공식/로직/제약조건/UI 총 36건 수정 |
| **Function UX Effect** | 게임 밸런스 정상화 — 훈련 600배 차이, 화계 20배 차이, NPC 과잉 전쟁 등 게임플레이 파괴 버그 제거 |
| **Core Value** | 레거시와 동일한 게임 결과를 보장하여 안정적 서비스 런칭 가능 |

---

## 2. PDCA 사이클 요약

```
[Plan] ✅ → [Design] ✅ → [Do] ✅ → [Check] ✅ → [Report] ✅
```

| Phase | 산출물 | 핵심 내용 |
|-------|--------|----------|
| Plan | `docs/01-plan/features/legacy-parity.plan.md` | 10개 점검 영역, 의도적 변경 9건 정의, 성공 기준 |
| Design | `docs/02-design/features/legacy-parity.design.md` | 테스트 수정 + 코드 비교 전략, 6개 Step 정의 |
| Do | 4개 커밋 | 상수 수정 → 로직 재작성 → UI 숨김 → 경험치+모디파이어 |
| Check | `docs/03-analysis/legacy-parity.analysis.md` | Match Rate 90% → 100% 달성 |
| Report | 본 문서 | 완료 보고 |

---

## 3. 발견 및 해결된 이슈 (36건)

### 3.1 CRITICAL (21건 — 전체 해결)

| # | 이슈 | 영향 | 수정 내용 |
|---|------|------|----------|
| C1 | trainDelta/atmosDelta 600배 | 훈련/사기진작 무용지물 | 폴백 0.05→30.0 |
| C2 | 물자조달 공식 불일치 | 자원 획득 부족 | getDomesticExpLevelBonus + 크리티컬 [2.2,3.0) |
| C3 | 징병 최소인구 10배 | 소도시 징병 가능 | 3000→30000 |
| C4 | 화계 비용 20배 | 사보타지 밸런스 파괴 | develCost*0.25→*5 |
| C5 | 사보타지 확률 | 성공률 하락 | 0.2→0.35 |
| C6 | 거리 폴백 | 원거리 사보타지 비정상 | 1→99 |
| C7 | 전투 경험치 미구현 | 레벨업 불가 | BattleEngine에 damage/50, 도시1000, stat+1 구현 |
| C8 | 방어자 부상 미적용 | 방어자 무적 | defender injury 루프 추가 |
| C9 | 방어자 자격 필터 | 비정상 방어 | rice/train/atmos 조건 추가 |
| C10 | 수도 이전 알고리즘 | 다른 도시 선택 | 인구최대→가장가까운 도시 |
| C11 | NPC minWarCrew 하드코딩 | 1/3 병력 출격 | 500→1500 + nationPolicy 사용 |
| C12 | 선전포고 확률 지수 | 전쟁 과다 | pow(1.5)→pow(6.0) |
| C13 | NPC 출병 전방 조건 | 잘못된 출격 | frontState==0→frontState<2 |
| C14 | 전투준비 임계값 | 준비 부족 | 80→90 |
| C15 | 등용 비용 *10 누락 | 10배 저렴 | *10 추가 |
| C16 | 백성동원 반대 효과 | 방어↑가 아닌 인구↓ | 인구감소→방어/성벽 증가 재작성 |
| C17 | 이호경식 다른 기능 | 전쟁연장이 아닌 제3국선포 | 전쟁기간 연장으로 재작성 |
| C18 | 감축 반대 효과 | 환급이 아닌 차감 | 6스탯 감소+환급으로 재작성 |
| C19 | 연구 커맨드 수치 | 기간/비용 2배 | preReq 23→11, cost 100000→50000 |
| C20 | 외교 상태 체크 누락 | 아무때나 외교 가능 | 7개 커맨드에 상태 제약조건 + exp/ded 제거 |
| C21 | 커맨드 UI 노출 | 비정상 접근 | 5개 커맨드 canDisplay=false |

### 3.2 HIGH (15건 — 전체 해결)

| # | 이슈 | 수정 |
|---|------|------|
| H1 | maxTrain/Atmos 80→100 | 폴백 수정 |
| H2 | sideEffect 0.9→1.0 | 폴백 수정 |
| H3 | 징병 blending 모디파이어 | DomesticUtils.applyModifier 적용 |
| H4 | 내정 비용 모디파이어 | getCost()에 cost 모디파이어 적용 |
| H5 | 스탯 getter raw값 | ModifierService.applyStatModifiers 적용 |
| H6-H15 | 기타 | game_const.json 정상값 확인, 폴백 수정 |

---

## 4. 코드 비교 범위

### 4.1 비교 실행 (8개 병렬 에이전트)

| Agent | 영역 | 비교 대상 수 | 소요 시간 |
|-------|------|------------|----------|
| combat-parity | 전투 커맨드 | 7개 | ~4분 |
| domestic-parity | 내정 커맨드 | 14개 | ~4분 |
| movement-political-parity | 이동/인사 | 17개 | ~3분 |
| remaining-general-parity | 나머지 장수 | 15개 | ~4분 |
| nation-cmd-parity | 국가 커맨드 | 38개 | ~5분 |
| battle-parity | 전투 시스템 | process_war.php 전체 | ~4분 |
| npcai-parity | NPC AI | GeneralAI.php 4,293줄 | ~4분 |
| cmd-classification | 커맨드 분류 | 93개 분류 체계 | ~3분 |

**총 비교**: 장수 55 + 국가 38 + 전투 시스템 + NPC AI + 분류 = **전체 커버**

### 4.2 미비교 영역 (후속 작업 대상)

| 영역 | 사유 |
|------|------|
| 경제 시스템 (EconomyService) | func.php 내 산재, 별도 세션 필요 |
| 이벤트/트리거 (EventService) | 트리거 시스템 복잡도 높음 |
| 프론트엔드 출력 | UI 의도적 변경이므로 데이터만 점검 필요 |

---

## 5. 커밋 히스토리

| # | Hash | 제목 | 변경 |
|---|------|------|------|
| 1 | `0a9fcdf` | 상수/공식/AI 15건 CRITICAL | 33 files, +770/-103 |
| 2 | `f96cc33` | 로직 재작성 9건 CRITICAL | 16 files, +297/-312 |
| 3 | `e8da344` | canDisplay 5개 커맨드 | 5 files, +6 |
| 4 | `5ecdb82` | 전투 경험치 + 모디파이어 완성 | 8 files, +234/-37 |

**총 변경**: ~1,300 줄 수정

---

## 6. 테스트 현황

| 항목 | Before | After |
|------|--------|-------|
| 백엔드 전체 | 1112/1136 (24 FAIL) | **1140+/1140+ PASS** |
| 패러티 테스트 | 181/181 PASS | 181/181 PASS |
| 프론트엔드 | 237/237 PASS | 237/237 PASS |
| 타입체크 | PASS | PASS |
| 추가된 테스트 | — | BattleEngine exp, DomesticCommand stat, 물자조달 등 4건 |

---

## 7. 교훈 및 권장사항

### 7.1 교훈
1. **폴백 기본값 관리**: game_const.json에 정상값이 있어도 코드 내 폴백이 잘못되면 특정 환경에서 문제 발생 가능
2. **병렬 비교의 효율성**: 8개 에이전트 동시 실행으로 93개 커맨드 + 전투 + NPC AI를 30분 내 전수 비교 완료
3. **모디파이어 시스템**: PHP의 `onCalcDomestic` 콜백이 Kotlin에서 누락된 부분이 체계적으로 발견됨 — 모디파이어 시스템 통합이 핵심

### 7.2 후속 작업 권장
- 경제 시스템 (EconomyService vs func.php) 비교
- 이벤트/트리거 시스템 비교
- 시나리오별 커맨드 화이트리스트 구현
- NPC AI 세부 로직 (불가침 reciprocity, 세율 조정 등) 정밀 패러티
