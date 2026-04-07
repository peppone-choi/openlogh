# Open LOGH (오픈 은하영웅전설)

웹 기반 은하영웅전설 다인원 온라인 전략 시뮬레이션 게임.

은하영웅전설VII(gin7, 2004 BOTHTEC)을 웹 기반으로 재구현한 프로젝트입니다. 플레이어는 은하제국 또는 자유행성동맹의 장교로 참가하여 조직 내에서 협력하며 진영의 승리를 목표로 합니다. 직무권한카드 기반 커맨드 시스템으로 계급 구조 안에서 명령/제안/인사/정치를 수행하며, 원작의 라인하르트나 양웬리의 입장을 체험할 수 있습니다.

## 주요 특징

- **조직 시뮬레이션**: 직무권한카드 기반 81종 커맨드 시스템 (작전/개인/지휘/병참/인사/정치/첩보)
- **실시간 전술전**: 에너지 배분(6채널), 무기 시스템(빔/건/미사일/전투정), 진형, 커맨드레인지서클, 색적, 요새포, 지상전
- **함종 시스템**: 11함종 x I~VIII 서브타입 (88종 상세 스탯), 진영별 고유 함종
- **경제 시스템**: 행성 자원, 조병창 자동생산, 세율/납입, 페잔 차관
- **AI 시스템**: 성격 기반 NPC AI, 진영 AI, 시나리오 이벤트 (쿠데타/내전)
- **10개 시나리오**: LOGH 원작 기반 시나리오 + 커스텀 캐릭터 생성 (8스탯 배분)

## 기술 스택

| 계층         | 기술                                 |
| ------------ | ------------------------------------ |
| 백엔드       | Spring Boot 3, Kotlin 2.1            |
| 프론트엔드   | Next.js 16, React 19, TypeScript     |
| 3D/2D        | React Three Fiber, React Konva       |
| 데이터베이스 | PostgreSQL 16                        |
| 캐시         | Redis 7                              |
| 실시간 통신  | WebSocket (STOMP), 1초 tick (24배속)  |
| 배포         | Docker Compose, GitHub Actions, GHCR |

## 저장소 구성

```text
openlogh/
├── backend/                  # Spring Boot 멀티모듈
│   ├── gateway-app/          #   인증·라우팅 게이트웨이 (port 8080)
│   ├── game-app/             #   게임 엔진·전투·커맨드·경제·AI (port 9001+)
│   └── shared/               #   공유 모듈 (DTO, 엔티티, 보안)
├── frontend/                 # Next.js 앱
│   └── src/
│       ├── components/       #   게임 UI 컴포넌트
│       │   ├── galaxy/       #     은하맵 (React Konva)
│       │   ├── tactical/     #     전술전 (R3F + Konva)
│       │   └── game/         #     전략 게임 UI
│       ├── stores/           #   Zustand 상태관리
│       └── types/            #   gin7 도메인 타입
├── docs/                     # 게임 설계 문서 + 참조 자료
│   ├── REWRITE_PROMPT.md     #   gin7 전면 재작성 스펙
│   ├── reference/            #   gin4 EX 위키, 시나리오 상세
│   ├── scenarios.json        #   10개 시나리오 데이터
│   └── star_systems.json     #   80개 성계 데이터
├── nginx/                    # Reverse proxy 설정
├── docker-compose.yml        # 로컬 개발 환경
└── README.md
```

## 게임 시스템

### 부대 편성

| 부대 | 규모 | 인원 |
|------|------|------|
| 단독함 | 기함 1척 | 1명 |
| 함대 | 60유닛 (18,000척) | 사령관+부사령관+참모장+참모6+부관 = 10명 |
| 순찰대 | 3유닛 (900척) | 사령관+부사령관+부관 = 3명 |
| 수송함대 | 수송함20+전투함3유닛 | 사령관+부사령관+부관 = 3명 |
| 지상부대 | 양륙함3+육전대3유닛 | 사령관 1명 |
| 행성수비대 | 육전대 10유닛 | 지휘관 1명 |

편성 제한: 인구 10억당 함대/수송함대 1, 순찰대/지상부대 6

### 장교 스탯 (8종)

| 스탯 | 설명 | CP 그룹 |
|------|------|---------|
| 통솔 (leadership) | 인재 활용, 함대 최대 사기 | PCP |
| 정치 (politics) | 시민 지지 획득 | PCP |
| 운영 (administration) | 행성 통치, 사무 관리 | PCP |
| 정보 (intelligence) | 정보 수집/분석, 첩보 | PCP |
| 지휘 (command) | 부대 지휘 능력 | MCP |
| 기동 (mobility) | 함대 이동/기동 지휘 | MCP |
| 공격 (attack) | 공격 지휘 능력 | MCP |
| 방어 (defense) | 방어 지휘 능력 | MCP |

### 승리 조건

- 적 수도 성계 점령
- 적 정규군 보유 성계 3개 이하 (수도 포함)
- 시간 제한 도달 시 인구 비교

## 로컬 개발 실행

### 1) DB/Redis 실행

```bash
docker compose up -d postgres redis
```

### 2) 백엔드 실행 (각각 별도 터미널)

```bash
cd backend
./gradlew :gateway-app:bootRun
```

```bash
cd backend
./gradlew :game-app:bootRun
```

### 3) 프론트엔드 실행

```bash
cd frontend
pnpm install
pnpm dev
```

## 빌드 및 테스트

### 백엔드

```bash
cd backend
./gradlew build
./gradlew test
```

### 프론트엔드

```bash
cd frontend
pnpm build
```

## Docker 배포

운영 배포는 배포 전용 저장소를 사용합니다.

```bash
git clone https://github.com/peppone-choi/openlogh-deploy.git
cd openlogh-deploy
cp .env.example .env
docker compose pull
docker compose up -d
```

## 참조 문서

- `docs/REWRITE_PROMPT.md` — gin7 전면 재작성 상세 스펙
- `docs/reference/gin4ex_wiki.md` — gin4 EX 위키 요약
- `docs/reference/unit_composition.md` — 부대 편성 규칙
- `/Users/apple/Downloads/gin7manualsaved.pdf` — gin7 공식 매뉴얼 (101페이지)

## 크레딧 & 감사

Open LOGH는 **[Hide_D](https://storage.hided.net/gitea/devsam)** 님의 삼국지 웹 게임 프로젝트를 기반으로 탄생했습니다. Hide_D 님이 오랜 기간 쌓아온 게임 설계, 시나리오 데이터, 전투 시스템, NPC AI, 턴 엔진 등의 방대한 구현물이 없었다면 이 프로젝트는 시작조차 어려웠을 것입니다.

| 프로젝트            | 설명                      | 링크                                            |
| ------------------- | ------------------------- | ----------------------------------------------- |
| **devsam/core**     | 원본 삼국지 웹 게임 (PHP) | https://storage.hided.net/gitea/devsam/core     |
| **devsam/core2026** | 차세대 버전               | https://storage.hided.net/gitea/devsam/core2026 |

은하영웅전설VII(gin7)의 게임 메카닉스를 참조하여 Spring Boot(Kotlin) + Next.js 기술 스택으로 재구현한 프로젝트입니다.

## 라이선스

MIT
