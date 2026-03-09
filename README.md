# 오픈삼국 (OpenSamguk)

웹 기반 삼국지 전략 시뮬레이션 게임.

삼국시대를 배경으로 한 턴 기반 전략 게임으로, 장수를 선택하고 내정·외교·전투를 통해 천하통일을 이루는 것이 목표입니다. 멀티플레이어 환경에서 NPC와 플레이어가 함께 경쟁하며, 실시간 WebSocket 기반 턴 진행과 채팅을 지원합니다.

## 기술 스택

| 계층         | 기술                                 |
| ------------ | ------------------------------------ |
| 백엔드       | Spring Boot 3, Kotlin                |
| 프론트엔드   | Next.js 15, React 19                 |
| 데이터베이스 | PostgreSQL 16                        |
| 캐시         | Redis 7                              |
| 실시간 통신  | WebSocket (STOMP)                    |
| 배포         | Docker Compose, GitHub Actions, GHCR |

## 저장소 구성

```text
opensamguk/
├── backend/                  # Spring Boot 멀티모듈
│   ├── gateway-app/          #   인증·라우팅 게이트웨이
│   ├── game-app/             #   게임 엔진·턴 처리·API
│   └── shared/               #   공유 모듈
├── frontend/                 # Next.js 앱
├── nginx/                    # Reverse proxy 설정
├── docs/                     # 아키텍처·패러티 문서
├── legacy-core/              # 레거시 PHP 참조 코드
├── docker-compose.yml        # GHCR 이미지 기반 실행 구성
└── README.md
```

## 관련 저장소

- **배포**: [opensamguk-deploy](https://github.com/peppone-choi/opensamguk-deploy)
- **이미지 에셋**: [opensamguk-image](https://github.com/peppone-choi/opensamguk-image)

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

운영 배포는 배포 전용 저장소([opensamguk-deploy](https://github.com/peppone-choi/opensamguk-deploy))를 사용합니다.

```bash
git clone https://github.com/peppone-choi/opensamguk-deploy.git
cd opensamguk-deploy
cp .env.example .env
docker compose pull
docker compose up -d
```

배포 스택에는 `bootstrap` 컨테이너가 포함되며, DB 마이그레이션을 1회 수행한 뒤 종료합니다.
`gateway`는 환경변수(`ADMIN_LOGIN_ID`, `ADMIN_PASSWORD` 등)를 사용해 초기 어드민 계정을 생성/갱신합니다.

## 이미지 CDN 설정

기본 CDN:

```
https://cdn.jsdelivr.net/gh/peppone-choi/opensamguk-image@master/
```

환경변수 `NEXT_PUBLIC_IMAGE_CDN_BASE`로 변경 가능합니다.

---

## 크레딧 & 감사

오픈삼국은 **[Hide_D](https://storage.hided.net/gitea/devsam)** 님의 삼국지 웹 게임 프로젝트를 제작 배경으로 삼아 탄생했습니다.

Hide_D 님이 오랜 기간 쌓아온 게임 설계, 시나리오 데이터, 전투 시스템, NPC AI, 턴 엔진 등의 방대한 구현물이 없었다면 이 프로젝트는 시작조차 어려웠을 것입니다. 게임 로직의 깊이와 세밀함, 그리고 수백 명의 장수 데이터를 정성스럽게 구축해 주신 노력에 깊은 존경과 감사를 드립니다.

### 원본 프로젝트

| 프로젝트            | 설명                      | 링크                                            |
| ------------------- | ------------------------- | ----------------------------------------------- |
| **devsam/core**     | 원본 삼국지 웹 게임 (PHP) | https://storage.hided.net/gitea/devsam/core     |
| **devsam/core2026** | 차세대 버전               | https://storage.hided.net/gitea/devsam/core2026 |

오픈삼국은 위 프로젝트의 게임 메커니즘과 데이터를 참조하여, Spring Boot(Kotlin) + Next.js 기술 스택으로 재구현한 프로젝트입니다.

## 라이선스

MIT
