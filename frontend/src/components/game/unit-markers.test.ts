import { describe, expect, it } from 'vitest';
import { buildUnitMarkers } from '@/components/game/unit-markers';
import type { General } from '@/types';

function makeGeneral(overrides: Partial<General> & { id: number }): General {
    return {
        worldId: 1,
        userId: null,
        name: overrides.name ?? `장수${overrides.id}`,
        nationId: overrides.nationId ?? 1,
        cityId: 10,
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
        crew: overrides.crew ?? 1000,
        crewType: overrides.crewType ?? 0,
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
        lastTurn: { command: '' },
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
        posX: overrides.posX ?? 100,
        posY: overrides.posY ?? 200,
        destX: overrides.destX,
        destY: overrides.destY,
        ...overrides,
    } as General;
}

const colorMap = new Map<number, string>([
    [1, '#ff0000'],
    [2, '#0000ff'],
]);

describe('buildUnitMarkers', () => {
    it('returns correct markers for generals with posX > 0 and crew > 0', () => {
        const generals = [makeGeneral({ id: 1, nationId: 1, posX: 100, posY: 200, crew: 500 })];
        const result = buildUnitMarkers(generals, 1, colorMap);
        expect(result).toHaveLength(1);
        expect(result[0]).toMatchObject({
            generalId: 1,
            name: '장수1',
            posX: 100,
            posY: 200,
            crew: 500,
            nationColor: '#ff0000',
            isEnemy: false,
        });
    });

    it('marks enemy generals as isEnemy=true', () => {
        const generals = [makeGeneral({ id: 2, nationId: 2, posX: 50, posY: 80 })];
        const result = buildUnitMarkers(generals, 1, colorMap);
        expect(result).toHaveLength(1);
        expect(result[0].isEnemy).toBe(true);
        expect(result[0].nationColor).toBe('#0000ff');
    });

    it('excludes generals with crew=0', () => {
        const generals = [makeGeneral({ id: 3, nationId: 1, posX: 100, posY: 200, crew: 0 })];
        const result = buildUnitMarkers(generals, 1, colorMap);
        expect(result).toHaveLength(0);
    });

    it('excludes generals with posX=0', () => {
        const generals = [makeGeneral({ id: 4, nationId: 1, posX: 0, posY: 200 })];
        const result = buildUnitMarkers(generals, 1, colorMap);
        expect(result).toHaveLength(0);
    });

    it('marks isMoving=true when destX/destY differ from pos', () => {
        const generals = [makeGeneral({ id: 5, nationId: 1, posX: 100, posY: 200, destX: 150, destY: 250 })];
        const result = buildUnitMarkers(generals, 1, colorMap);
        expect(result[0].isMoving).toBe(true);
        expect(result[0].destX).toBe(150);
        expect(result[0].destY).toBe(250);
    });

    it('marks isMoving=false when destX/destY are null', () => {
        const generals = [makeGeneral({ id: 6, nationId: 1, posX: 100, posY: 200, destX: null, destY: null })];
        const result = buildUnitMarkers(generals, 1, colorMap);
        expect(result[0].isMoving).toBe(false);
    });

    it('uses fallback color #888 when nation not in colorMap', () => {
        const generals = [makeGeneral({ id: 7, nationId: 99, posX: 100, posY: 200 })];
        const result = buildUnitMarkers(generals, 99, colorMap);
        expect(result[0].nationColor).toBe('#888');
    });
});
