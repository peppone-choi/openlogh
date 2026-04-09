// Phase 14 Plan 14-18 — Real tests replacing Wave 0 scaffold stubs.
// FE-01 summary · D-32..D-34 acceptance criteria.
//
// Strategy (per 14-09 / 14-11 pattern):
// The vitest config uses `environment: 'node'` and Radix Dialog / React
// rendering fails without jsdom. We therefore DO NOT mount
// <BattleEndModal />. Instead we test:
//
//   1. Pure helpers (`resolveHeader`, `formatMeritBreakdown`, `computeMySide`)
//      exported alongside the component — these encode every D-32..D-34
//      decision (header text, merit cell format, operation highlight logic,
//      mySide resolution).
//   2. Source-text regex guards for the D-32..D-34 copy contract
//      (Korean strings, data attributes, CTA destinations).
//   3. The `tacticalApi.fetchBattleSummary` helper hits the correct URL
//      and returns the parsed BattleSummaryDto.
//   4. The `fetchBattleSummary` re-export exists on `gameApi` (acceptance
//      criterion: `grep -n "fetchBattleSummary" frontend/src/lib/gameApi.ts`).
//
// No @testing-library/react, no `render()` — same constraint 14-09 and
// 14-11 already operate under.

import { describe, it, expect, vi, afterEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

// Pure helpers exported from the component module.
import {
    resolveHeader,
    formatMeritBreakdown,
    computeMySide,
    type BattleSummaryRowView,
} from './BattleEndModal';

// Fetcher lives in tacticalApi (co-located with other /api/v1/battle helpers).
import { tacticalApi } from '@/lib/tacticalApi';

// Mock axios at the module level. Both `@/lib/api` (used by gameApi /
// officerStore / worldStore) and `@/lib/tacticalApi` call `axios.create()`
// in module scope and expect the result to support `.interceptors.request.use`
// + HTTP verbs. We return a factory that produces minimal-but-functional
// mock instances so every module that imports axios gets its own clone.
//
// The BattleEndModal test only asserts against the *tacticalApi* instance's
// `.get()` — we identify it via `createdInstances[1]` (api.ts is index 0,
// tacticalApi.ts is index 1 per import order).
const { createdInstances, axiosCreate } = vi.hoisted(() => {
    const createdInstances: Array<{
        get: ReturnType<typeof vi.fn>;
        post: ReturnType<typeof vi.fn>;
        put: ReturnType<typeof vi.fn>;
        delete: ReturnType<typeof vi.fn>;
        interceptors: {
            request: { use: ReturnType<typeof vi.fn> };
            response: { use: ReturnType<typeof vi.fn> };
        };
    }> = [];
    const axiosCreate = vi.fn(() => {
        const instance = {
            get: vi.fn(),
            post: vi.fn(),
            put: vi.fn(),
            delete: vi.fn(),
            interceptors: {
                request: { use: vi.fn() },
                response: { use: vi.fn() },
            },
        };
        createdInstances.push(instance);
        return instance;
    });
    return { createdInstances, axiosCreate };
});

vi.mock('axios', () => ({
    default: {
        create: axiosCreate,
    },
}));

describe('BattleEndModal pure helpers (FE-01 summary, D-32..D-34)', () => {
    describe('resolveHeader (D-32)', () => {
        it('returns "승리" in "win" variant when winner matches my ATTACKER side', () => {
            expect(resolveHeader('attacker_win', 'ATTACKER')).toEqual({
                text: '승리',
                variant: 'win',
            });
        });

        it('returns "승리" in "win" variant when winner matches my DEFENDER side', () => {
            expect(resolveHeader('defender_win', 'DEFENDER')).toEqual({
                text: '승리',
                variant: 'win',
            });
        });

        it('returns "패배" in "loss" variant when winner is opposite of my side', () => {
            expect(resolveHeader('attacker_win', 'DEFENDER')).toEqual({
                text: '패배',
                variant: 'loss',
            });
            expect(resolveHeader('defender_win', 'ATTACKER')).toEqual({
                text: '패배',
                variant: 'loss',
            });
        });

        it('returns "교전 종료" in "draw" variant for draw result', () => {
            expect(resolveHeader('draw', 'ATTACKER')).toEqual({
                text: '교전 종료',
                variant: 'draw',
            });
        });

        it('returns "교전 종료" in "draw" variant when winner is null (ongoing / unknown)', () => {
            expect(resolveHeader(null, 'ATTACKER')).toEqual({
                text: '교전 종료',
                variant: 'draw',
            });
        });

        it('returns "교전 종료" in "draw" variant when mySide is null (spectator)', () => {
            expect(resolveHeader('attacker_win', null)).toEqual({
                text: '교전 종료',
                variant: 'draw',
            });
        });
    });

    describe('formatMeritBreakdown (D-33)', () => {
        const baseRow: BattleSummaryRowView = {
            fleetId: 100,
            officerName: '라인하르트',
            survivingShips: 300,
            maxShips: 300,
            baseMerit: 100,
            operationMultiplier: 1,
            totalMerit: 100,
            isOperationParticipant: false,
        };

        it('renders "기본 X + 작전 +Y = 총 Z" when operation bonus > 0', () => {
            const row: BattleSummaryRowView = {
                ...baseRow,
                baseMerit: 80,
                operationMultiplier: 1.5,
                totalMerit: 120,
                isOperationParticipant: true,
            };
            // 80 * 1.5 = 120, bonus = 40
            expect(formatMeritBreakdown(row)).toBe('기본 80 + 작전 +40 = 총 120');
        });

        it('renders "기본 X = 총 Z" (no 작전 segment) when operation bonus = 0', () => {
            const row: BattleSummaryRowView = {
                ...baseRow,
                baseMerit: 100,
                operationMultiplier: 1,
                totalMerit: 100,
                isOperationParticipant: false,
            };
            expect(formatMeritBreakdown(row)).toBe('기본 100 = 총 100');
        });

        it('handles zero base merit on losing side', () => {
            const row: BattleSummaryRowView = {
                ...baseRow,
                baseMerit: 0,
                operationMultiplier: 1,
                totalMerit: 0,
            };
            expect(formatMeritBreakdown(row)).toBe('기본 0 = 총 0');
        });

        it('hides 작전 segment for an operation participant whose bonus rounds to zero', () => {
            // Edge case: participant flag set but bonus still 0 (e.g. losing side).
            const row: BattleSummaryRowView = {
                ...baseRow,
                baseMerit: 0,
                operationMultiplier: 1.5,
                totalMerit: 0,
                isOperationParticipant: true,
            };
            expect(formatMeritBreakdown(row)).toBe('기본 0 = 총 0');
        });
    });

    describe('computeMySide (D-32 header win/loss resolution)', () => {
        it('returns ATTACKER when my officerId matches an attacker-side row', () => {
            const rows = [
                { officerId: 1, side: 'ATTACKER' as const },
                { officerId: 2, side: 'DEFENDER' as const },
            ];
            expect(computeMySide(rows, 1)).toBe('ATTACKER');
        });

        it('returns DEFENDER when my officerId matches a defender-side row', () => {
            const rows = [
                { officerId: 1, side: 'ATTACKER' as const },
                { officerId: 2, side: 'DEFENDER' as const },
            ];
            expect(computeMySide(rows, 2)).toBe('DEFENDER');
        });

        it('returns null when my officerId is not in the rows (spectator)', () => {
            const rows = [
                { officerId: 1, side: 'ATTACKER' as const },
                { officerId: 2, side: 'DEFENDER' as const },
            ];
            expect(computeMySide(rows, 999)).toBeNull();
        });

        it('returns null when myOfficerId is null', () => {
            const rows = [{ officerId: 1, side: 'ATTACKER' as const }];
            expect(computeMySide(rows, null)).toBeNull();
        });
    });
});

describe('BattleEndModal source-text regression guards (D-32..D-34 copy contract)', () => {
    const sourcePath = join(__dirname, 'BattleEndModal.tsx');
    const source = readFileSync(sourcePath, 'utf8');

    it('contains the header copy "승리"', () => {
        expect(source).toContain('승리');
    });

    it('contains the header copy "패배"', () => {
        expect(source).toContain('패배');
    });

    it('contains the draw / fallback header copy "교전 종료"', () => {
        expect(source).toContain('교전 종료');
    });

    it('contains the loading copy "전투 결과를 집계하는 중입니다"', () => {
        expect(source).toContain('전투 결과를 집계하는 중입니다');
    });

    it('contains the primary CTA "전략맵으로 돌아가기"', () => {
        expect(source).toContain('전략맵으로 돌아가기');
    });

    it('contains the secondary CTA "전투 기록 보기"', () => {
        expect(source).toContain('전투 기록 보기');
    });

    it('contains the operation-participant badge copy "작전 참가"', () => {
        expect(source).toContain('작전 참가');
    });

    it('contains the "기본" merit label', () => {
        expect(source).toContain('기본');
    });

    it('contains the "작전" merit label', () => {
        expect(source).toContain('작전');
    });

    it('contains the 4 table column headers (부대 / 함선 / 격침 / 공적)', () => {
        expect(source).toContain('부대');
        expect(source).toContain('함선');
        expect(source).toContain('격침');
        expect(source).toContain('공적');
    });

    it('sets data-operation attribute on participant rows', () => {
        expect(source).toContain('data-operation');
    });

    it('uses font-mono for merit cell (D-33 numeric disambiguation)', () => {
        expect(source).toContain('font-mono');
    });

    it('imports fetchBattleSummary from tacticalApi', () => {
        expect(source).toMatch(/import[^;]*fetchBattleSummary[^;]*from\s+['"]@\/lib\/tacticalApi['"]/);
    });

    it('navigates to /world/{sessionId}/galaxy on primary CTA click', () => {
        // Primary CTA pushes a template literal containing /world/ and /galaxy
        expect(source).toMatch(/\/world\/\$\{.*\}\/galaxy/);
    });

    it('uses useEffect to watch currentBattle?.phase for ACTIVE → ENDED transition', () => {
        // Phase-watcher effect must exist and reference phase === 'ENDED'
        expect(source).toContain("phase === 'ENDED'");
    });

    it('uses next/navigation useRouter for navigation', () => {
        expect(source).toMatch(/from\s+['"]next\/navigation['"]/);
    });
});

describe('tacticalApi.fetchBattleSummary (14-02 endpoint contract)', () => {
    afterEach(() => {
        // Reset all .get/.post mocks but keep the createdInstances array
        // intact (those were captured at module evaluation time).
        for (const inst of createdInstances) {
            inst.get.mockReset();
        }
    });

    it('calls GET /api/v1/battle/{sessionId}/{battleId}/summary', async () => {
        // tacticalApi.ts calls axios.create() exactly once. Find the
        // instance whose .get has been used for /api/v1/battle paths — it
        // is the second axios.create() call in import order (api.ts is
        // index 0 and defines interceptors, tacticalApi.ts is index 1).
        expect(createdInstances.length).toBeGreaterThanOrEqual(2);

        const fixture = {
            battleId: 42,
            winner: 'attacker_win',
            durationTicks: 120,
            rows: [
                {
                    fleetId: 100,
                    officerId: 1,
                    officerName: '라인하르트',
                    side: 'ATTACKER' as const,
                    survivingShips: 300,
                    maxShips: 300,
                    baseMerit: 80,
                    operationMultiplier: 1.5,
                    totalMerit: 120,
                    isOperationParticipant: true,
                },
            ],
        };

        // Arm every instance's .get so whichever one tacticalApi resolved
        // to will hand back our fixture. The assertion below narrows
        // down to the instance that actually received the call.
        for (const inst of createdInstances) {
            inst.get.mockResolvedValueOnce({ data: fixture });
        }

        const result = await tacticalApi.fetchBattleSummary(7, 42);

        // Exactly one of the instances must have received the call with
        // the 14-02 contract URL.
        const calls = createdInstances.flatMap((inst) =>
            inst.get.mock.calls.map((c) => c[0]),
        );
        expect(calls).toContain('/api/v1/battle/7/42/summary');
        expect(result).toEqual(fixture);
    });
});

describe('gameApi fetchBattleSummary re-export (acceptance criterion)', () => {
    it('gameApi.ts exposes fetchBattleSummary (text match for grep acceptance)', () => {
        const src = readFileSync(
            join(__dirname, '..', '..', 'lib', 'gameApi.ts'),
            'utf8',
        );
        expect(src).toContain('fetchBattleSummary');
        expect(src).toContain('/summary');
    });
});
