// Fleet domain types — gin7 은하영웅전설 함대/함종 체계

/** 함종 코드 */
export type ShipClass =
    | 'battleship'  // 전함 — 주력 전투함
    | 'cruiser'     // 순양함 — 범용 전투함
    | 'destroyer'   // 구축함 — 고속 전투함
    | 'carrier'     // 항공모함 — 스파르타니안 운용
    | 'transport'   // 수송함 — 물자/병력 수송
    | 'hospital'    // 병원선 — 부상자 치료
    | 'fortress';   // 요새 — 이동 요새 (이제르론/가이에스부르크)

/** 전술 진형 코드 */
export type Formation =
    | 'SPINDLE'       // 방추진형 (紡錘)
    | 'BY_CLASS'      // 함종별진형 (艦種)
    | 'MIXED'         // 혼성진형 (混成)
    | 'THREE_COLUMN'; // 삼열진형 (三列)

/** 유닛 스탠스 */
export type UnitStance =
    | 'NAVIGATION'  // 항행 중
    | 'ANCHORING'   // 정박 중
    | 'STATIONED'   // 주둔 중
    | 'COMBAT';     // 전투 중

/** 함대 내 개별 유닛 (함종별 분리 단위) */
export interface FleetUnit {
    /** 유닛 고유 ID */
    id: number;
    /** 소속 함대 ID */
    fleetId: number;
    /** 함종 */
    shipClass: ShipClass;
    /** 함선 서브타입 코드 (세부 기종) */
    shipSubtype: string;
    /** 함선 수 */
    ships: number;
    /** 사기 (0-100) */
    morale: number;
    /** 훈련도 (0-100) */
    training: number;
    /** 현재 스탠스 */
    stance?: UnitStance;
    /** 에너지 배분 메타 */
    meta?: Record<string, unknown>;
}

/** 함대 */
export interface Fleet {
    /** 함대 고유 ID */
    id: number;
    /** 게임 세션 ID */
    worldId: number;
    /** 함대 이름 */
    name: string;
    /** 소속 진영 ID */
    factionId: number;
    /** 사령관 장교 ID (없으면 null) */
    leaderOfficerId: number | null;
    /** 총 함선 수 */
    ships: number;
    /** 주력 함종 */
    shipClass: ShipClass;
    /** 사기 (0-100) */
    morale: number;
    /** 훈련도 (0-100) */
    training: number;
    /** 현재 진형 */
    formation: Formation;
    /** 메타 데이터 (pendingFullRepair 등 상태 플래그 포함) */
    meta: Record<string, unknown>;
    createdAt?: string;
    turnTime?: string;
    /** 예약된 커맨드 요약 */
    reservedCommandBrief?: string;
}

/** 함대 요약 정보 (목록 표시용) */
export interface FleetSummary {
    id: number;
    worldId: number;
    name: string;
    factionId: number;
    leaderOfficerId: number | null;
    ships: number;
    shipClass: ShipClass;
    morale: number;
    training: number;
    formation: Formation;
}
