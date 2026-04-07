// Command domain types — gin7 직무권한카드 + 커맨드 포인트 체계

/** 커맨드 그룹 코드 (gin7 7-group 분류) */
export type CommandGroup =
    | 'OPERATION'      // 작전
    | 'PERSONAL'       // 개인
    | 'COMMAND'        // 지휘
    | 'LOGISTICS'      // 군수
    | 'PERSONNEL'      // 인사
    | 'POLITICS'       // 정략
    | 'INTELLIGENCE';  // 정보

/** 커맨드 포인트 타입 */
export type CpType = 'PCP' | 'MCP';
// PCP = Political Command Points (정략 커맨드 포인트)
// MCP = Military Command Points (군사 커맨드 포인트)

/** 커맨드 항목 정의 */
export interface CommandEntry {
    /** 커맨드 코드 (예: "SORTIE", "RECRUIT", "MOVE") */
    code: string;
    /** 표시 이름 (한국어) */
    name: string;
    /** 소속 커맨드 그룹 */
    group: CommandGroup;
    /** 사용 CP 타입 */
    cpType: CpType;
    /** CP 소비량 */
    cpCost: number;
    /** 쿨다운 (초) */
    cooldown: number;
    /** 실행에 필요한 직무권한카드 코드 목록 */
    requiredCards: string[];
    /** 커맨드 설명 */
    description?: string;
}

/** 커맨드 포인트 풀 상태 */
export interface CpPool {
    /** 정략 CP 현재값 */
    pcpCurrent: number;
    /** 정략 CP 최대값 */
    pcpMax: number;
    /** 군사 CP 현재값 */
    mcpCurrent: number;
    /** 군사 CP 최대값 */
    mcpMax: number;
    /** CP 재생 속도 (5분당 재생량) */
    regenRate: number;
}

/** 커맨드 제안 (부하 → 상관 제안 시스템) */
export interface CommandProposal {
    /** 제안 고유 ID */
    id: number;
    /** 세션 ID */
    worldId: number;
    /** 제안한 장교 ID */
    proposerId: number;
    /** 승인 대상 장교 ID (상관) */
    approverId: number | null;
    /** 커맨드 코드 */
    commandCode: string;
    /** 커맨드 인자 */
    args: Record<string, unknown>;
    /** 제안 상태 */
    status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXECUTED';
    /** 제안 메시지 */
    message?: string;
    createdAt: string;
    updatedAt?: string;
}

/** 커맨드 실행 결과 */
export interface CommandResult {
    /** 성공 여부 */
    success: boolean;
    /** 로그 메시지 목록 */
    log: string[];
    /** 오류 메시지 (실패 시) */
    message?: string;
}

/** 커맨드 인자 (자유 형식 JSON 객체) */
export type CommandArg = Record<string, unknown>;

/** 커맨드 테이블 항목 (UI 목록 렌더링용) */
export interface CommandTableEntry {
    code: string;
    name: string;
    group: CommandGroup;
    cpType: CpType;
    cpCost: number;
    cooldown: number;
    requiredCards: string[];
    available: boolean;
    reason?: string;
}
