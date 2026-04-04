const DEFAULT_CDN_BASE = 'https://cdn.jsdelivr.net/gh/peppone-choi/openlogh-image@master/';

function normalizeCdnBase(url: string): string {
    return `${url.replace(/\/+$/, '')}/`;
}

export const CDN_BASE = normalizeCdnBase(process.env.NEXT_PUBLIC_IMAGE_CDN_BASE ?? DEFAULT_CDN_BASE);
const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080/api';

export const CDN_ROOT = CDN_BASE.slice(0, -1);
export const GAME_CDN_ROOT = `${CDN_ROOT}/game`;
export const ICON_CDN_ROOT = `${CDN_ROOT}/icons`;

export function getPortraitUrl(picture?: string | null): string {
    if (!picture) return `${ICON_CDN_ROOT}/0.jpg`;
    const normalized = picture.trim().replace(/^\/+/, '');
    if (!normalized || normalized === 'default.jpg') return `${ICON_CDN_ROOT}/0.jpg`;
    // picture is a numeric string (e.g. "1146") — append .jpg
    if (/^\d+$/.test(normalized)) return `${ICON_CDN_ROOT}/${normalized}.jpg`;
    if (/^\d+\.jpg$/i.test(normalized)) return `${ICON_CDN_ROOT}/${normalized}`;
    // Uploaded icon served from backend
    if (normalized.startsWith('uploads/')) return `${API_BASE}/${normalized}`;
    // Already has extension or is a full path
    return `${CDN_BASE}${normalized}`;
}

export function getMapAssetUrl(asset: string): string {
    return `${GAME_CDN_ROOT}/${asset}`;
}

export function getPlanetLevelIcon(level: number): string {
    return `${GAME_CDN_ROOT}/cast_${level}.gif`;
}

export function getEventIcon(state: number): string {
    return `${GAME_CDN_ROOT}/event${state}.gif`;
}

export function getShipClassIconUrl(crewType: number): string {
    const normalizedCrewType = crewType >= 1000 ? crewType : 1100 + Math.max(0, crewType) * 100;
    return `${GAME_CDN_ROOT}/crewtype${normalizedCrewType}.png`;
}

export function getProgressBarBg(height: number): string {
    return `${GAME_CDN_ROOT}/pr${height - 2}.gif`;
}

export function getProgressBarFill(height: number): string {
    return `${GAME_CDN_ROOT}/pb${height - 2}.gif`;
}

/** Faction territory background as CSS radial gradient */
export function getFactionBgGradient(color: string): string {
    const hex = color.replace('#', '');
    const r = parseInt(hex.substring(0, 2), 16);
    const g = parseInt(hex.substring(2, 4), 16);
    const b = parseInt(hex.substring(4, 6), 16);
    return `radial-gradient(ellipse closest-side, rgba(${r},${g},${b},0.92) 0%, rgba(${r},${g},${b},0.4) 50%, transparent 100%)`;
}

/** Animated flag GIF for supplied cities (12×12, 4 frames) */
export function getSuppliedFlagUrl(colorHex: string): string {
    const hex = colorHex.replace('#', '');
    return `${GAME_CDN_ROOT}/f${hex}.gif`;
}

/** Static dot icon for depleted cities (16×16) */
export function getDepletedFlagUrl(colorHex: string): string {
    const hex = colorHex.replace('#', '');
    return `${GAME_CDN_ROOT}/d${hex}.gif`;
}

/** Map background layer for a season — `bg_spring.jpg`, `bg_summer.jpg`, etc. */
export function getMapBgUrl(mapFolder: string, season: 'spring' | 'summer' | 'fall' | 'winter'): string {
    return `${GAME_CDN_ROOT}/map/${mapFolder}/bg_${season}.jpg`;
}

/** Map road overlay — e.g. `che_road.png` */
export function getMapRoadUrl(mapCode: string): string {
    if (mapCode.includes('miniche')) return `${GAME_CDN_ROOT}/map/che/miniche_road.png`;
    const folder = mapCode === 'ludo_rathowm' ? 'ludo_rathowm' : mapCode;
    const file = mapCode === 'ludo_rathowm' ? 'road.png' : `${mapCode}_road.png`;
    return `${GAME_CDN_ROOT}/map/${folder}/${file}`;
}

/** Special-event city icon (e.g. event51 = revolt) */
export function getSpecialEventIcon(eventId: number): string {
    return `${GAME_CDN_ROOT}/event${eventId}.gif`;
}

/** Item icon by item type/id */
export function getItemIconUrl(itemId: number): string {
    return `${GAME_CDN_ROOT}/item${itemId}.png`;
}

/** Skill icon by skill id */
export function getSkillIconUrl(skillId: number): string {
    return `${GAME_CDN_ROOT}/skill${skillId}.png`;
}

/** Building icon by building type */
export function getBuildingIconUrl(buildingType: number): string {
    return `${GAME_CDN_ROOT}/building${buildingType}.png`;
}
