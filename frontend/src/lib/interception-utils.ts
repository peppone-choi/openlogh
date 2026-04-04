import type { General } from '@/types';

export interface InterceptionMarker {
    generalName: string;
    nationColor: string;
    fromCityId: number;
    toCityId: number;
}

/** Extract interception markers from generals, filtered to myNationId only */
export function getInterceptionMarkers(
    generals: General[],
    myNationId: number,
    nationColorMap: Map<number, string>
): InterceptionMarker[] {
    return generals
        .filter((g) => {
            if (g.nationId !== myNationId) return false;
            const action = (g.lastTurn as unknown as Record<string, unknown>)?.action;
            return action === '요격';
        })
        .map((g) => {
            const lt = g.lastTurn as unknown as Record<string, unknown>;
            return {
                generalName: g.name,
                nationColor: nationColorMap.get(g.nationId) ?? '#888',
                fromCityId: Number(lt.originCityId ?? g.cityId),
                toCityId: Number(lt.interceptionTargetCityId ?? 0),
            };
        })
        .filter((m) => m.toCityId > 0);
}
