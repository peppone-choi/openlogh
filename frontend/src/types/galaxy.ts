// Galaxy map types for star system visualization

export interface StarSystem {
    id: number;
    mapStarId: number;
    nameKo: string;
    nameEn: string;
    factionId: number;
    x: number;
    y: number;
    spectralType: string;
    starRgb: number[];
    level: number;
    region: number | string;
    /** Faction color from DB — used for map rendering. Overrides region-based color when present. */
    factionColor?: string;
    fortressType: FortressType;
    fortressGunPower: number;
    fortressGunRange: number;
    garrisonCapacity: number;
    connections: number[];
    planetCount: number;
}

export type FortressType =
    | 'NONE'
    | 'ISERLOHN'
    | 'GEIERSBURG'
    | 'RENTENBERG'
    | 'GARMISCH';

export interface StarRoute {
    fromStarId: number;
    toStarId: number;
    distance: number;
}

export interface GalaxyMap {
    systems: StarSystem[];
    routes: StarRoute[];
    factionTerritories: Record<number, number[]>;
}

/**
 * Faction-region color mapping for the galaxy map (gin7 art style)
 * Region codes: 0=공백지, 1=제국, 2=동맹, 3=페잔, 4=반군/독립세력
 */
export const FACTION_REGION_COLORS = {
    VACANT: '#444444',      // 공백지 (unoccupied)
    EMPIRE: '#5a6ee0',      // 은하제국 (blue)
    ALLIANCE: '#d06878',    // 자유행성동맹 (pink/red)
    FEZZAN: '#9898a0',      // 페잔 자치령 (gray)
    REBEL: '#e0a830',       // 반군/독립세력 (amber)
} as const;

/** Radial gradient color pairs for glossy orb rendering (inner highlight -> outer base) */
export const FACTION_GRADIENT_COLORS = {
    VACANT: { inner: '#666666', outer: '#333333' },
    EMPIRE: { inner: '#8298ff', outer: '#4a5cc0' },
    ALLIANCE: { inner: '#ff96aa', outer: '#c86478' },
    FEZZAN: { inner: '#c8c8d2', outer: '#9696a0' },
    REBEL: { inner: '#ffcc55', outer: '#c08820' },
} as const;

/**
 * Resolve faction gradient from region.
 * region can be: number (legacy) or string (factionType).
 * Accepts both for backward compatibility during transition.
 */
export function getFactionGradient(region: number | string): { inner: string; outer: string } {
    const key = normalizeFactionType(region);
    return FACTION_GRADIENT_COLORS[key] ?? FACTION_GRADIENT_COLORS.VACANT;
}

/** Resolve display color — prefer factionColor from DB, fallback to region code */
export function getFactionColor(region: number | string, factionColor?: string): string {
    if (factionColor) return factionColor;
    const key = normalizeFactionType(region);
    return FACTION_REGION_COLORS[key] ?? FACTION_REGION_COLORS.VACANT;
}

/** Normalize region (number or factionType string) to palette key */
function normalizeFactionType(region: number | string): keyof typeof FACTION_REGION_COLORS {
    if (typeof region === 'string') {
        switch (region.toLowerCase()) {
            case 'empire': return 'EMPIRE';
            case 'alliance': return 'ALLIANCE';
            case 'fezzan': return 'FEZZAN';
            case 'rebel': return 'REBEL';
            default: return 'VACANT';
        }
    }
    // Legacy number codes
    switch (region) {
        case 1: return 'EMPIRE';
        case 2: return 'ALLIANCE';
        case 3: return 'FEZZAN';
        case 4: return 'REBEL';
        case 0: return 'VACANT';
        default: return 'VACANT';
    }
}

/** Check whether a star system hosts a fortress */
export function isFortress(system: StarSystem): boolean {
    return system.fortressType !== 'NONE';
}

/** Map faction string names (from static JSON) to region codes */
export function factionNameToRegion(faction: string): number {
    switch (faction) {
        case '제국':
            return 1;
        case '동맹':
            return 2;
        case '페잔':
            return 3;
        default:
            return 0;
    }
}

/** Fortress display names */
export const FORTRESS_NAMES: Record<FortressType, string> = {
    NONE: '',
    ISERLOHN: '이제르론 요새',
    GEIERSBURG: '가이에스부르크 요새',
    RENTENBERG: '렌텐베르크 요새',
    GARMISCH: '가르미슈 요새',
};
