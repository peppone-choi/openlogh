import api from '../api';
import type { GalaxyMap, StarSystem } from '@/types/galaxy';

/** Fetch the full galaxy map for a game session */
export async function fetchGalaxyMap(sessionId: number): Promise<GalaxyMap> {
    const { data } = await api.get<GalaxyMap>(`/world/${sessionId}/galaxy`);
    return data;
}

/**
 * Fetch the public (unauthenticated) cached galaxy map. Used by the
 * lobby/login screen so the map preview works before the player joins a world.
 *
 * If `worldId` is omitted, the most recently updated world is returned.
 */
export async function fetchPublicCachedGalaxy(worldId?: number): Promise<GalaxyMap> {
    const url = worldId != null ? `/public/cached-galaxy?worldId=${worldId}` : '/public/cached-galaxy';
    const { data } = await api.get<GalaxyMap>(url);
    return data;
}

/** Fetch details for a single star system */
export async function fetchStarSystem(
    sessionId: number,
    mapStarId: number
): Promise<StarSystem> {
    const { data } = await api.get<StarSystem>(
        `/world/${sessionId}/galaxy/system/${mapStarId}`
    );
    return data;
}

/** Fetch all fortress systems for a session */
export async function fetchFortresses(
    sessionId: number
): Promise<StarSystem[]> {
    const { data } = await api.get<StarSystem[]>(
        `/world/${sessionId}/galaxy/fortresses`
    );
    return data;
}

/** Fetch the static map layout (pre-session preview) */
export async function fetchStaticMap(): Promise<GalaxyMap> {
    const { data } = await api.get<GalaxyMap>('/maps/logh');
    return data;
}
