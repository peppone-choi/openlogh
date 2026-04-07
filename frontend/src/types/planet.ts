// Planet and Faction domain types — gin7 은하영웅전설 행성/진영 체계

/** 진영 타입 */
export type FactionType =
    | 'empire'    // 은하제국 — 전제군주제, 귀족 체계
    | 'alliance'  // 자유행성동맹 — 민주공화제
    | 'fezzan'    // 페잔 자치령 — 중립 교역 국가
    | 'rebel';    // 반란군 — 쿠데타/반란 세력

/** 행성 (ex-City) */
export interface Planet {
    /** 행성 고유 ID */
    id: number;
    /** 게임 세션 ID */
    worldId: number;
    /** 행성 이름 */
    name: string;
    /** 행성 등급 */
    level: number;
    /** 소속 진영 ID */
    factionId: number;
    /** 공급 상태 (0=차단, 1=정상) */
    supplyState: number;
    /** 전선 상태 */
    frontState?: number;

    // 경제/인구 지표
    /** 인구 (ex-pop) */
    population: number;
    /** 인구 최대값 */
    populationMax?: number;
    /** 생산력 — 함선/물자 생산 (ex-agri) */
    production: number;
    /** 생산 최대값 */
    productionMax?: number;
    /** 교역/경제 (ex-comm) */
    commerce: number;
    /** 교역 최대값 */
    commerceMax?: number;
    /** 치안 (ex-secu) */
    security: number;
    /** 치안 최대값 */
    securityMax?: number;
    /** 주민 지지도 (ex-trust) */
    approval: number;
    /** 교역 항로 수 (ex-trade) */
    trade_route: number;

    // 방어 시설
    /** 궤도 방어력 (ex-def) */
    orbital_defense: number;
    /** 궤도 방어 최대값 */
    orbital_defenseMax?: number;
    /** 요새 방어력 (ex-wall) */
    fortress: number;
    /** 요새 최대값 */
    fortressMax?: number;

    /** 진영 분쟁 점수 맵: 진영 ID(string) → 점수 */
    conflict?: Record<string, number>;
    /** 메타 데이터 (shipyardClass 등 포함) */
    meta: Record<string, unknown>;
}

/** 진영 (ex-Nation) */
export interface Faction {
    /** 진영 고유 ID */
    id: number;
    /** 게임 세션 ID */
    worldId: number;
    /** 진영 이름 */
    name: string;
    /** 진영 약칭 (예: 제국, 동맹) */
    abbreviation: string;
    /** 진영 대표 색상 (hex) */
    color: string;
    /** 수도 행성 ID */
    capitalPlanetId: number | null;
    /** 진영 타입 */
    faction_type: FactionType;

    // 경제 자원
    /** 자금 (ex-gold) */
    funds: number;
    /** 군수 물자 (ex-rice) */
    supplies: number;

    // 정책 수치
    /** 세율 (ex-bill) */
    tax_rate: number;
    /** 징병률 (ex-rate) */
    conscription_rate: number;
    /** 기술력 레벨 (ex-tech) */
    tech_level: number;
    /** 군사력 (ex-power) */
    military_power: number;
    /** 진영 등급 (ex-level) */
    faction_rank: number;

    /** 첩보 맵: 행성 ID(string) → 첩보 레벨 */
    spy?: Record<string, number>;
    /** 메타 데이터 */
    meta: Record<string, unknown>;
    createdAt?: string;
    updatedAt?: string;
}

/** 함대 위치 정보 (은하 맵 오버레이용) */
export interface FleetPosition {
    /** 함대 ID */
    fleetId: number;
    /** 사령관 이름 */
    officerName: string;
    /** 총 함선 수 */
    ships: number;
    /** 소속 진영 ID */
    factionId: number;
}
