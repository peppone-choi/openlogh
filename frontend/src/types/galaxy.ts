// Galaxy map types for star system visualization

export interface FleetPosition {
    fleetId: number;
    officerName: string;
    ships: number;
    factionId: number;
}

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
    /** Faction display name (e.g. '은하제국', '자유행성동맹'). Empty for vacant. */
    factionName: string;
    /** Faction color hex from DB — directly used for map rendering */
    factionColor: string;
    /** Control strength 0-100, drives shade selection (100 = full control) */
    controlStrength?: number;
    fortressType: FortressType;
    fortressGunPower: number;
    fortressGunRange: number;
    garrisonCapacity: number;
    connections: number[];
    planetCount: number;
    /** Planet names within this star system (from static data or API) */
    planets: string[];
    /** Fleets stationed at this star system */
    fleetPositions?: FleetPosition[];
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
 * 5-shade faction color palettes for control-strength-based rendering.
 * shade index = Math.floor(controlStrength / 20), clamped 0-4
 * shade 0 = darkest (weak control), 4 = brightest (full control)
 */
export const FACTION_SHADES = {
    empire:   ['#0a1233', '#1a2d66', '#2244aa', '#3355cc', '#4466ff'] as const,
    alliance: ['#330a0a', '#661a1a', '#aa2222', '#cc3333', '#ff4444'] as const,
    neutral:  ['#1a1a1a', '#333333', '#555555', '#777777', '#999999'] as const,
    // Fezzan: neutral gray palette (cool-tinted) per design direction
    fezzan:   ['#17181c', '#2c2e36', '#4a4d5a', '#6b6e7e', '#9598a8'] as const,
    rebel:    ['#1a0d00', '#331a00', '#663300', '#994d00', '#cc6600'] as const,
} as const;

export type FactionShadeKey = keyof typeof FACTION_SHADES;

/** Resolve 5-shade color from factionId + controlStrength */
export function getFactionShadeColor(
    factionId: number | null | undefined,
    factionType: string | null | undefined,
    controlStrength: number | null | undefined
): string {
    const strength = controlStrength ?? 50;
    const idx = Math.min(4, Math.max(0, Math.floor(strength / 20)));

    const key = resolveFactionShadeKey(factionId, factionType);
    return FACTION_SHADES[key][idx];
}

function resolveFactionShadeKey(
    factionId: number | null | undefined,
    factionType: string | null | undefined
): FactionShadeKey {
    const type = factionType?.toLowerCase();
    if (type === 'empire') return 'empire';
    if (type === 'alliance') return 'alliance';
    if (type === 'fezzan') return 'fezzan';
    if (type === 'rebel') return 'rebel';
    // Fallback by factionId numeric code
    if (factionId === 1) return 'empire';
    if (factionId === 2) return 'alliance';
    if (factionId === 3) return 'fezzan';
    if (factionId === 4) return 'rebel';
    return 'neutral';
}

/**
 * Faction-region color mapping for the galaxy map (gin7 art style)
 * Region codes: 0=공백지, 1=제국, 2=동맹, 3=페잔, 4=반군/독립세력
 */
export const FACTION_REGION_COLORS = {
    VACANT: '#444444',      // 공백지 (unoccupied)
    EMPIRE: '#5a6ee0',      // 은하제국 (blue)
    ALLIANCE: '#d06878',    // 자유행성동맹 (pink/red)
    FEZZAN: '#8d90a0',      // 페잔 자치령 (cool gray)
    REBEL: '#e0a830',       // 반군/독립세력 (amber)
} as const;

/** Radial gradient color pairs for glossy orb rendering (inner highlight -> outer base) */
export const FACTION_GRADIENT_COLORS = {
    VACANT: { inner: '#666666', outer: '#333333' },
    EMPIRE: { inner: '#8298ff', outer: '#4a5cc0' },
    ALLIANCE: { inner: '#ff96aa', outer: '#c86478' },
    FEZZAN: { inner: '#b4b6c2', outer: '#7a7d8a' },
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

/**
 * Capital star systems (mapStarId) that render larger on the galaxy map.
 * Only the two political centers of the main war render oversized — Fezzan
 * stays the same size as every other neutral system so it doesn't read as a
 * peer of the Empire/Alliance capitals.
 * - 42: Valhalla (발할라) — Galactic Empire capital
 * - 36: Barat (바라트) — Free Planets Alliance capital (Heinessen)
 */
export const CAPITAL_STAR_IDS: ReadonlySet<number> = new Set([42, 36]);

export function isCapitalStar(mapStarId: number): boolean {
    return CAPITAL_STAR_IDS.has(mapStarId);
}
