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
    region: number;
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

/** Faction-region color mapping for the galaxy map */
export const FACTION_REGION_COLORS = {
    EMPIRE: '#4488ff',
    ALLIANCE: '#44cc66',
    FEZZAN: '#ffcc44',
    NEUTRAL: '#888888',
} as const;

/** Return the display color for a given region code */
export function getFactionColor(region: number): string {
    switch (region) {
        case 1:
            return FACTION_REGION_COLORS.EMPIRE;
        case 2:
            return FACTION_REGION_COLORS.ALLIANCE;
        case 3:
            return FACTION_REGION_COLORS.FEZZAN;
        default:
            return FACTION_REGION_COLORS.NEUTRAL;
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
