// Officer domain types — gin7 은하영웅전설 8-stat system

/** 계급 레벨 (0=소위, 10=원수/의장) */
export type RankLevel = 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10;

/** 계급 칭호 (제국군 기준) */
export type RankTitle =
    | 'Sub-Lieutenant'   // 소위 (0)
    | 'Lieutenant'       // 대위 (1)
    | 'Lieutenant Commander' // 소령 (2)
    | 'Commander'        // 중령 (3)
    | 'Captain'          // 대령 (4)
    | 'Commodore'        // 준장 (5)
    | 'Rear Admiral'     // 소장 (6)
    | 'Vice Admiral'     // 중장 (7)
    | 'Admiral'          // 대장 (8)
    | 'Fleet Admiral'    // 상급대장 (9)
    | 'Reichsmarschall'; // 원수 (10)

/** 8-stat 구조 — gin7 통솔/지휘/정보/정치/운영/기동/공격/방어 */
export interface OfficerStat {
    /** 통솔 — 인재 활용, 함대 최대 사기 */
    leadership: number;
    /** 지휘 — 부대 지휘 능력 (ex-strength) */
    command: number;
    /** 정보 — 정보 수집/분석, 첩보, 색적 (ex-intel) */
    intelligence: number;
    /** 정치 — 시민 지지 획득 */
    politics: number;
    /** 운영 — 행성 통치, 사무 관리 (ex-charm) */
    administration: number;
    /** 기동 — 함대 이동/기동 지휘 (신규) */
    mobility: number;
    /** 공격 — 공격 지휘 능력 (신규) */
    attack: number;
    /** 방어 — 방어 지휘 능력 (신규) */
    defense: number;
}

/** 직무권한카드 코드 (예: "FLEET_CMD", "SORTIE", "PLANET_GOV") */
export type PositionCard = string;

/** 장교/제독 전체 정보 */
export interface Officer {
    /** 장교 고유 ID */
    id: number;
    /** 게임 세션 ID */
    worldId: number;
    /** 장교 이름 */
    name: string;
    /** 사용자 계정 ID (NPC이면 null) */
    userId: number | null;
    /** 소속 진영 ID */
    factionId: number;
    /** 현재 위치 행성 ID */
    planetId: number;
    /** 지휘 중인 함대 ID (없으면 null) */
    fleetId: number | null;
    /** 장교 레벨 */
    officerLevel: number;
    /** 계급 레벨 (0-10) */
    rankLevel: number;
    /** 계급 칭호 */
    rankTitle: string;

    // 8-stat 필드 (flat, gin7 체계)
    /** 통솔 */
    leadership: number;
    /** 지휘 (ex-strength) */
    command: number;
    /** 정보 (ex-intel) */
    intelligence: number;
    /** 정치 */
    politics: number;
    /** 운영 (ex-charm) */
    administration: number;
    /** 기동 */
    mobility: number;
    /** 공격 */
    attack: number;
    /** 방어 */
    defense: number;

    // 경험치
    leadershipExp: number;
    commandExp: number;
    intelligenceExp: number;
    politicsExp: number;
    administrationExp: number;
    mobilityExp: number;
    attackExp: number;
    defenseExp: number;

    // 커맨드 포인트
    /** 정략 CP 현재값 */
    pcpPool: number;
    /** 정략 CP 최대값 */
    pcpMax: number;
    /** 군사 CP 현재값 */
    mcpPool: number;
    /** 군사 CP 최대값 */
    mcpMax: number;

    /** 직무권한카드 코드 목록 (JSONB) */
    positionCards: string[];

    /** NPC 상태 (0=인간, 1=NPC) */
    npcState: number;
    /** 부상 레벨 */
    injury: number;
    /** 경험 */
    experience: number;
    /** 헌신도 */
    dedication: number;

    // 아이템 코드 (gin7 매핑)
    /** 기함 코드 (ex-weapon) */
    flagshipCode?: string;
    /** 특수장비 코드 (ex-book) */
    equipCode?: string;
    /** 기관 코드 (ex-horse) */
    engineCode?: string;
    /** 부속품 코드 (ex-item) */
    accessoryCode?: string;

    /** 프로필 이미지 경로 */
    picture: string;
    /** 마지막 커맨드 종료 시각 */
    commandEndTime: string | null;
    /** 메타 데이터 */
    meta: Record<string, unknown>;
    createdAt: string;
    updatedAt: string;
}

/** 장교 요약 정보 (목록 표시용) */
export interface OfficerSummary {
    id: number;
    worldId: number;
    name: string;
    factionId: number;
    planetId: number;
    rankLevel: number;
    rankTitle: string;
    leadership: number;
    command: number;
    intelligence: number;
    politics: number;
    administration: number;
    mobility: number;
    attack: number;
    defense: number;
    picture: string;
    npcState: number;
    positionCards: string[];
}
