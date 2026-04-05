---
name: verify-frontend-parity
description: 레거시 PHP 대비 프론트엔드 라우트/출력/실시간 요소와 구조적 패러티 스크립트를 확인합니다. 프론트엔드 페이지 추가/수정 후 사용.
---

# Frontend Parity 검증

## Purpose

레거시 PHP 화면 대비 프론트엔드 구현 상태와 구조적 패러티 체크 상태를 검증합니다:

1. **페이지 수 패러티** — 레거시 35개 화면(view/info/admin 포함)이 프론트엔드 라우트로 존재하는지 확인
2. **출력 정보 패러티** — 각 페이지가 레거시와 동일한 데이터 항목을 표시하는지 확인
3. **UI 구성 요소** — 테이블, 폼, 맵, 차트 등 핵심 UI 컴포넌트가 존재하는지 확인
4. **권한별 분기** — Public/Authed/Admin 상태에 따른 정보 노출 범위가 현재 게임 흐름과 일치하는지 확인
5. **실시간 업데이트** — WebSocket/SSE 연결이 필요한 화면이 구현되어 있는지 확인

## When to Run

- 새로운 프론트엔드 페이지를 추가한 후
- 기존 페이지의 출력 정보를 수정한 후
- 라우터 구성을 변경한 후
- parity route/spec 추가 후 구조적 체크를 검증할 때
- 레거시 화면과의 비교가 필요할 때

## Related Files

| File                                          | Purpose                          |
| --------------------------------------------- | -------------------------------- |
| `legacy-core/hwe/index.php`                   | 레거시 메인 페이지               |
| `legacy-core/hwe/b_*.php`                     | 레거시 인증 사용자 화면          |
| `legacy-core/hwe/v_*.php`                     | 레거시 뷰 화면                   |
| `legacy-core/hwe/a_*.php`                     | 레거시 공개 통계/랭킹 화면       |
| `legacy-core/hwe/ts/`                         | 레거시 Vue/TS 컴포넌트           |
| `frontend/src/app/`                           | 프론트엔드 라우트 (구현 대상)    |
| `frontend/src/components/`                    | 프론트엔드 컴포넌트 (구현 대상)  |
| `frontend/e2e/parity/`                        | 프론트엔드 Playwright 패러티 스펙 |
| `scripts/verify/frontend-parity.mjs`          | 구조적 프론트엔드 패러티 체크    |

## Workflow

### Step 1: 레거시 화면 카탈로그 구축

**검사:** 레거시 PHP 화면 목록을 현재 프론트엔드 라우트와 대조합니다.

레거시 화면 목록 (35개):

**Public/통계 (a\_ 계열, 7개):**
`a_bestGeneral`, `a_emperior`, `a_emperior_detail`, `a_genList`, `a_hallOfFame`, `a_kingdomList`, `a_npcList`, `a_traffic`

**인증 사용자 (b\_ 계열, 10개):**
`b_betting`, `b_currentCity`, `b_genList`, `b_myBossInfo`, `b_myCityInfo`, `b_myGenInfo`, `b_myKingdomInfo`, `b_myPage`, `b_tournament`

**뷰 (v\_ 계열, 13개):**
`v_cachedMap`, `v_join`, `v_processing`, `v_board`, `v_history`, `v_vote`, `v_auction`, `v_battleCenter`, `v_chiefCenter`, `v_globalDiplomacy`, `v_inheritPoint`, `v_NPCControl`, `v_nationBetting`, `v_nationGeneral`, `v_nationStratFinan`, `v_troop`

**기타:**
`index` (메인)

**현재 프론트엔드 추가 페이지 (레거시 매핑):**

| 프론트엔드 경로     | 레거시 대응                                  |
| ------------------- | -------------------------------------------- |
| `battle-simulator/` | `v_battleCenter` 일부 (모의 전투)            |
| `internal-affairs/` | 내정 커맨드 그룹 (레거시에서 별도 화면 없음) |
| `my-page/`          | `b_myPage`                                   |
| `personnel/`        | 인사/등용 커맨드 그룹                        |
| `select-npc/`       | `v_NPCControl` 일부                          |
| `select-pool/`      | 장수 선택 풀 (가입 시)                       |
| `spy/`              | 첩보 커맨드 그룹                             |

### Step 2: 프론트엔드 라우트 존재 확인

**파일:** `frontend/src/app/` 또는 라우터 설정

**검사:** 각 레거시 화면에 대응하는 프론트엔드 라우트가 존재하는지 확인합니다.

```bash
# Next.js app router 페이지 목록
find frontend/src/app -name "page.tsx" -o -name "page.ts" 2>/dev/null | sort

# 또는 라우터 설정에서 라우트 추출
grep -rn "path:\|route\|Router\|Link\|href=" frontend/src/ 2>/dev/null | grep -v node_modules | head -30
```

**PASS:** 레거시 화면에 대응하는 라우트가 존재
**FAIL:** 누락된 라우트 발견

### Step 2a: 구조적 패러티 스크립트 확인

**검사:** pre-commit/CI에서 재사용되는 구조적 패러티 스크립트가 핵심 라우트와 parity spec 존재 여부를 검증하는지 확인합니다.

```bash
node scripts/verify/frontend-parity.mjs
```

**PASS:** 스크립트 실행 성공
**FAIL:** 누락된 route/spec 또는 스크립트 오류

### Step 3: 페이지별 출력 정보 확인

**검사:** 구현된 각 페이지가 레거시와 동일한 데이터 항목을 표시하는지 확인합니다.

핵심 페이지별 필수 정보:

| 페이지    | 필수 출력 정보                                               |
| --------- | ------------------------------------------------------------ |
| 내 장수   | 5-stat, crew/train/atmos, 소속 국가/도시, 관직, 아이템, 특기 |
| 내 도시   | 인구, 농업/상업/치안/수비/성벽, 자원, 장수 목록              |
| 내 국가   | 국호, 수도, 금/쌀, 기술, 소속 장수 목록, 외교 상태           |
| 세계 지도 | 도시 위치, 소속 국가 색상, 연결선, 도시 정보 팝업            |
| 장수 목록 | 이름, 국가, 5-stat, 관직, 레벨                               |
| 국가 목록 | 국호, 수도, 금/쌀, 장수 수, 도시 수                          |

```bash
# 각 페이지에서 표시하는 데이터 필드 검색
grep -rn "leadership\|strength\|intel\|politics\|charm\|crew\|train\|atmos" frontend/src/ 2>/dev/null | grep -v node_modules
```

**PASS:** 필수 출력 정보가 해당 페이지에 존재
**FAIL:** 레거시 대비 누락된 데이터 항목 발견

### Step 4: UI 컴포넌트 존재 확인

**검사:** 핵심 UI 컴포넌트가 존재하는지 확인합니다.

```bash
# 프론트엔드 컴포넌트 목록
ls frontend/src/components/ 2>/dev/null
```

필수 UI 컴포넌트:

- **맵 (Konva/Canvas)** — 세계 지도 렌더링
- **데이터 테이블** — 장수/국가/도시 목록
- **커맨드 선택기** — 장수/국가 커맨드 설정 UI
- **메시지함** — 수신/발신 메시지 목록
- **게시판** — 글 목록/작성/댓글
- **외교 패널** — 외교 상태 표시/제의 UI

**PASS:** 핵심 UI 컴포넌트 존재
**FAIL:** 필수 컴포넌트 누락

### Step 5: 권한별 정보 노출 확인

**검사:** Public/Authed/Admin 상태에 따른 정보 노출이 현재 구현과 일치하는지 확인합니다.

```bash
# 인증 가드/미들웨어 검색
grep -rn "auth\|guard\|middleware\|protected\|public" frontend/src/ 2>/dev/null | grep -v node_modules | head -20
```

**현재 기준:**

- Public: 10분 캐시 지도 + 동향(최소 정보)
- Authed: 대부분의 정보 접근 허용
- Admin: 운영자 전용 화면

**PASS:** 권한별 분기가 SPA plan과 일치
**FAIL:** 권한 분기 누락 또는 불일치

### Step 6: 실시간 업데이트 확인

**검사:** WebSocket/SSE가 필요한 화면에 실시간 연결이 구현되어 있는지 확인합니다.

```bash
# WebSocket/SSE 관련 코드 검색
grep -rn "WebSocket\|STOMP\|SSE\|EventSource\|useWebSocket\|stompjs" frontend/src/ 2>/dev/null | grep -v node_modules
```

주요 실시간 대상: 지도, 명령 목록, 현재 도시 정보, 소속 국가 정보, 장수 스탯, 장수 동향, 개인 기록, 중원 정세, 메시지함

**PASS:** 실시간 업데이트 대상 화면에 WebSocket/SSE 연결 존재
**FAIL:** 실시간 업데이트 누락

## Output Format

```markdown
## Frontend Parity 검증 결과

### 페이지 라우트 (35개 레거시 화면)

| 상태   | 수  | 비율 |
| ------ | --- | ---- |
| 구현됨 | X   | X%   |
| 미구현 | Y   | Y%   |

### 상세 (미구현 페이지)

| #   | 레거시 화면      | 카테고리 | 프론트엔드 라우트 | 출력 정보 | UI 컴포넌트 |
| --- | ---------------- | -------- | ----------------- | --------- | ----------- |
| 1   | `b_myGenInfo`    | Core     | X                 | -         | -           |
| 2   | `v_battleCenter` | Advanced | X                 | -         | -           |

### 실시간 업데이트

| 대상      | WebSocket/SSE | 상태      |
| --------- | ------------- | --------- |
| 세계 지도 | O/X           | PASS/FAIL |
| 메시지함  | O/X           | PASS/FAIL |
```

## Exceptions

다음은 **위반이 아닙니다**:

1. **구현 우선순위 차이** — Public → Core → Advanced 순서로 구현하므로, 후순위 페이지가 없는 것은 정상
2. **UI 프레임워크 차이** — 레거시와 다른 UI 스택을 써도 기능적 패러티가 유지되면 PASS
3. **레이아웃/디자인 차이** — "고전 게임 감성 유지" 원칙에 따라 레거시와 레이아웃이 유사하면 충분; 픽셀 단위 일치는 불필요
4. **Admin 화면 후순위** — Admin/GM 화면은 SPA plan에서 후순위로 지정됨; 부재가 이슈가 아님
5. **실시간 토글** — 실시간 동기화 토글이 없어도 필수 화면의 실시간 정보가 노출되면 warning으로 보고
6. **REST 구현 차이** — 레거시와 UI 구조/정보가 맞으면 내부 API 방식 차이는 허용
