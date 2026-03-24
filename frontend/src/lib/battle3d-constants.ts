// CrewType code → display info mapping (from legacy)
export const CREW_TYPE_INFO: Record<number, { name: string; modelKey: string; color: string }> = {
    0: { name: '보병', modelKey: 'footman', color: '#8B4513' },
    1: { name: '궁병', modelKey: 'archer', color: '#2E8B57' },
    2: { name: '기병', modelKey: 'cavalry', color: '#4169E1' },
    3: { name: '술사', modelKey: 'wizard', color: '#9932CC' },
    4: { name: '공성', modelKey: 'siege', color: '#808080' },
    5: { name: '특수', modelKey: 'misc', color: '#DAA520' },
    6: { name: '수군', modelKey: 'navy', color: '#1E90FF' },
};

// Default nation colors fallback
export const DEFAULT_NATION_COLORS: Record<number, string> = {
    0: '#888888', // 무소속
    1: '#0055FF', // 위
    2: '#00AA00', // 촉
    3: '#FF0000', // 오
};

// Animation timing
export const PHASE_DURATION_MS = 1500; // base duration per phase at 1x speed
export const ADVANCE_DURATION_RATIO = 0.3; // 30% of phase for advance
export const CLASH_DURATION_RATIO = 0.4; // 40% for clash
export const RETREAT_DURATION_RATIO = 0.3; // 30% for retreat

// Scene layout
export const BATTLEFIELD_WIDTH = 40;
export const BATTLEFIELD_DEPTH = 30;
export const ATTACKER_START_X = -15;
export const DEFENDER_START_X = 15;
export const CLASH_POINT_X = 0;

// Unit scaling
export const MAX_VISIBLE_UNITS = 20; // max units rendered per side
export const UNIT_SPACING = 1.2;
export const FORMATION_COLS = 4;
