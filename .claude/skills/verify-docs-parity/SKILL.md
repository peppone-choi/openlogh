---
name: verify-docs-parity
description: `README.md`와 `CLAUDE.md`에 기록된 개발/검증/아키텍처 설명이 현재 구현과 일치하는지 확인합니다. 기능 구현 후 사용.
---

# Docs Parity 검증

## Purpose

저장소에 실제로 존재하는 프로젝트 문서와 현재 구현이 일치하는지 검증합니다:

1. **실행 명령** — `README.md`와 `CLAUDE.md`에 기록된 개발/검증 명령이 실제로 존재하는지 확인
2. **아키텍처 설명** — `CLAUDE.md`의 멀티 프로세스/월드/턴 엔진 설명이 현재 코드 구조와 일치하는지 확인
3. **검증 파이프라인 설명** — 문서에 기록된 `./verify`/훅/CI 흐름이 실제 파일과 일치하는지 확인
4. **프론트엔드/백엔드 진입점** — 문서의 실행 경로가 실제 `backend/`, `frontend/` 구조와 맞는지 확인
5. **배포 설명** — 배포 및 검증 관련 설명이 현재 워크플로/스크립트와 충돌하지 않는지 확인

## When to Run

- `README.md` 또는 `CLAUDE.md`를 수정한 후
- 검증 스크립트/CI/훅을 수정한 후
- 아키텍처 설명이 바뀌는 기능을 구현한 후
- PR 전 문서와 구현이 일치하는지 확인할 때

## Related Files

| File                                                     | Purpose                     |
| -------------------------------------------------------- | --------------------------- |
| `README.md`                                              | 저장소 실행/배포/검증 명령  |
| `CLAUDE.md`                                              | 프로젝트 지침/아키텍처 설명 |
| `verify`                                                 | 공통 검증 엔트리포인트      |
| `scripts/verify/run.sh`                                  | 프로필별 검증 파이프라인    |
| `scripts/verify/install-hooks.sh`                        | 훅 설치 스크립트            |
| `.githooks/pre-commit`                                   | 커밋 전 훅 템플릿           |
| `.github/workflows/verify.yml`                           | CI 검증 워크플로            |
| `backend/game-app/src/main/kotlin/com/opensam/`         | 게임 백엔드 구현            |
| `backend/gateway-app/src/main/kotlin/com/opensam/gateway/` | 게이트웨이 구현          |
| `frontend/src/`                                          | 프론트엔드 구현             |

## Workflow

### Step 1: 문서화된 명령 존재 확인

**파일:** `README.md`, `CLAUDE.md`

**검사:** 문서에 적힌 개발/검증 명령이 실제로 존재하는지 확인합니다.

```bash
grep -n "./verify\|pnpm dev\|./gradlew" README.md CLAUDE.md 2>/dev/null
ls verify scripts/verify/run.sh scripts/verify/install-hooks.sh .githooks/pre-commit 2>/dev/null
```

**PASS:** 문서화된 핵심 명령이 실제 파일/스크립트와 일치
**FAIL:** 문서에 적힌 명령이 존재하지 않음

### Step 2: 아키텍처 설명 일치 확인

**파일:** `CLAUDE.md`

**검사:** `CLAUDE.md`의 멀티 프로세스/월드/턴 엔진 설명이 현재 코드 구조와 맞는지 확인합니다.

```bash
grep -n "Multi-Process\|World = Profile\|Turn Engine" CLAUDE.md 2>/dev/null
ls backend/gateway-app/src/main/kotlin/com/opensam/gateway/ 2>/dev/null
ls backend/game-app/src/main/kotlin/com/opensam/engine/ 2>/dev/null
```

**PASS:** 문서의 아키텍처 설명과 실제 구조가 일치
**FAIL:** 문서 설명과 실제 구조가 불일치

### Step 3: 검증 파이프라인 문서 반영 확인

**검사:** 문서에 기록된 검증 파이프라인이 실제 파일과 일치하는지 확인합니다.

```bash
grep -n "install-hooks\|verify pre-commit\|verify ci" README.md CLAUDE.md 2>/dev/null
ls .github/workflows/verify.yml verify scripts/verify/run.sh scripts/verify/tdd-gate.sh 2>/dev/null
```

**PASS:** 문서화된 검증 흐름과 실제 스크립트/워크플로가 일치
**FAIL:** 문서에는 있지만 구현되지 않은 검증 흐름 발견

### Step 4: 프론트엔드/백엔드 진입점 확인

**검사:** 문서의 실행 경로가 실제 백엔드/프론트엔드 진입점과 일치하는지 확인합니다.

```bash
ls backend/gateway-app/src/main/kotlin/com/opensam/gateway/ 2>/dev/null
ls backend/game-app/src/main/kotlin/com/opensam/ 2>/dev/null
ls frontend/src/app/ 2>/dev/null
```

**PASS:** 문서의 실행 경로 설명이 현재 디렉토리 구조와 일치
**FAIL:** 문서 설명과 실제 진입점 불일치

### Step 5: 배포/검증 설명 확인

**검사:** 문서의 배포/검증 설명이 현재 워크플로와 충돌하지 않는지 확인합니다.

```bash
grep -n "opensamguk-deploy\|verify ci\|verify pre-commit" README.md CLAUDE.md 2>/dev/null
ls .github/workflows/*.yml 2>/dev/null
```

**PASS:** 배포/검증 문서와 현재 워크플로가 충돌하지 않음
**FAIL:** 문서와 실제 배포/검증 흐름 불일치

## Output Format

```markdown
## Docs Parity 검증 결과

| #   | 항목                  | 상태      | 상세 |
| --- | --------------------- | --------- | ---- |
| 1   | 실행 명령             | PASS/FAIL | ...  |
| 2   | 아키텍처 설명         | PASS/FAIL | ...  |
| 3   | 검증 파이프라인       | PASS/FAIL | ...  |
| 4   | 프론트/백엔드 진입점  | PASS/FAIL | ...  |
| 5   | 배포/검증 설명        | PASS/FAIL | ...  |
```

## Exceptions

다음은 **위반이 아닙니다**:

1. **문서 표현 차이** — 문서의 설명 방식이 실제 디렉토리명과 완전히 같지 않아도 핵심 명령/구조가 맞으면 PASS
2. **게이트 상세 구현 차이** — 문서에 특정 훅 매니저가 명시되지 않아도 `./verify`가 공통 엔트리포인트면 PASS
3. **검증 범위 차이** — lint/E2E를 CI 또는 후속 단계로 두는 것은 즉시 FAIL이 아님; 테스트/TDD/패러티 게이트가 핵심 기준
