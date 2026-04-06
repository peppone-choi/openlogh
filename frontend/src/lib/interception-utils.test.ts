import { describe, expect, it } from 'vitest';
import { getInterceptionMarkers } from '@/lib/interception-utils';
import type { General } from '@/types';

function makeGeneral(overrides: Partial<General> & { id: number }): General {
    return {
        worldId: 1,
        userId: null,
        name: overrides.name ?? `장교${overrides.id}`,
        nationId: overrides.nationId ?? 1,
        cityId: overrides.cityId ?? 10,
        troopId: 0,
        npcState: 0,
        npcOrg: null,
        affinity: 0,
        bornYear: 180,
        deadYear: 0,
        leadership: 80,
        leadershipExp: 0,
        strength: 80,
        strengthExp: 0,
        intel: 80,
        intelExp: 0,
        politics: 80,
        politicsExp: 0,
        charm: 80,
        charmExp: 0,
        dex1: 0,
        dex2: 0,
        dex3: 0,
        dex4: 0,
        dex5: 0,
        injury: 0,
        experience: 0,
        dedication: 0,
        officerLevel: 0,
        officerCity: 0,
        gold: 0,
        rice: 0,
        crew: 1000,
        crewType: 0,
        train: 50,
        atmos: 50,
        weaponCode: '',
        bookCode: '',
        horseCode: '',
        itemCode: '',
        ownerName: '',
        newmsg: 0,
        turnTime: '',
        recentWarTime: null,
        makeLimit: 0,
        killTurn: null,
        age: 30,
        startAge: 20,
        belong: 0,
        betray: 0,
        personalCode: '',
        specialCode: '',
        specAge: 0,
        special2Code: '',
        spec2Age: 0,
        commandPoints: 0,
        commandEndTime: null,
        lastTurn: overrides.lastTurn ?? { command: '' },
        meta: {},
        penalty: {},
        picture: '',
        defenceTrain: 0,
        tournamentState: 0,
        blockState: 0,
        permission: 'user',
        imageServer: 0,
        dedLevel: 0,
        expLevel: 0,
        createdAt: '',
        updatedAt: '',
        ...overrides,
    } as General;
}

describe('getInterceptionMarkers', () => {
    const colorMap = new Map<number, string>([
        [1, '#ff0000'],
        [2, '#0000ff'],
    ]);

    it('returns markers for own nation generals with action 요격', () => {
        const generals = [
            makeGeneral({
                id: 1,
                nationId: 1,
                cityId: 10,
                lastTurn: {
                    command: '요격',
                    action: '요격',
                    originCityId: 10,
                    interceptionTargetCityId: 20,
                } as unknown as General['lastTurn'],
            }),
        ];
        const result = getInterceptionMarkers(generals, 1, colorMap);
        expect(result).toHaveLength(1);
        expect(result[0]).toEqual({
            generalName: '장교1',
            nationColor: '#ff0000',
            fromCityId: 10,
            toCityId: 20,
        });
    });

    it('excludes generals from enemy nations', () => {
        const generals = [
            makeGeneral({
                id: 2,
                nationId: 2,
                cityId: 10,
                lastTurn: {
                    command: '요격',
                    action: '요격',
                    originCityId: 10,
                    interceptionTargetCityId: 20,
                } as unknown as General['lastTurn'],
            }),
        ];
        const result = getInterceptionMarkers(generals, 1, colorMap);
        expect(result).toHaveLength(0);
    });

    it('excludes generals with other actions', () => {
        const generals = [
            makeGeneral({
                id: 3,
                nationId: 1,
                cityId: 10,
                lastTurn: { command: '훈련', action: '훈련' } as unknown as General['lastTurn'],
            }),
        ];
        const result = getInterceptionMarkers(generals, 1, colorMap);
        expect(result).toHaveLength(0);
    });

    it('excludes markers where toCityId is 0', () => {
        const generals = [
            makeGeneral({
                id: 4,
                nationId: 1,
                cityId: 10,
                lastTurn: {
                    command: '요격',
                    action: '요격',
                    originCityId: 10,
                    interceptionTargetCityId: 0,
                } as unknown as General['lastTurn'],
            }),
        ];
        const result = getInterceptionMarkers(generals, 1, colorMap);
        expect(result).toHaveLength(0);
    });

    it('uses cityId as fromCityId when originCityId is missing', () => {
        const generals = [
            makeGeneral({
                id: 5,
                nationId: 1,
                cityId: 15,
                lastTurn: {
                    command: '요격',
                    action: '요격',
                    interceptionTargetCityId: 25,
                } as unknown as General['lastTurn'],
            }),
        ];
        const result = getInterceptionMarkers(generals, 1, colorMap);
        expect(result).toHaveLength(1);
        expect(result[0].fromCityId).toBe(15);
        expect(result[0].toCityId).toBe(25);
    });

    it('uses fallback color #888 when nation not in colorMap', () => {
        const generals = [
            makeGeneral({
                id: 6,
                nationId: 99,
                cityId: 10,
                lastTurn: {
                    command: '요격',
                    action: '요격',
                    originCityId: 10,
                    interceptionTargetCityId: 20,
                } as unknown as General['lastTurn'],
            }),
        ];
        const result = getInterceptionMarkers(generals, 99, colorMap);
        expect(result).toHaveLength(1);
        expect(result[0].nationColor).toBe('#888');
    });
});
