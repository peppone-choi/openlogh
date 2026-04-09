// @vitest-environment jsdom
//
// Phase 14 — Plan 14-14 — command-execution-panel gating UI (FE-03, D-09, D-11).
//
// Live tests (flipped from 14-05 scaffold). Asserts:
//  - Disabled command button when selectedUnit is outside my chain
//  - Radix tooltip (text: "지휘권 없음") wrapping the disabled button
//  - Tooltip body includes a `kbd` element hinting at Shift+click
//
// Gating is computed via canCommandUnit (14-14 Task 1). The panel reads the
// tactical store's hierarchy slice + selectedUnit to decide which buttons get
// gated; we seed both via a helper factory.

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { CommandExecutionPanel } from './command-execution-panel';
import { useTacticalStore } from '@/stores/tacticalStore';
import type { CommandTableEntry } from '@/types';
import type { CommandHierarchyDto, TacticalUnit } from '@/types/tactical';

// Silence publishWebSocket + commandApi.execute so gating tests never hit the
// real transport — buttons here are always disabled, but vi.mock is cheap.
vi.mock('@/lib/websocket', () => ({
    publishWebSocket: vi.fn(),
}));
vi.mock('@/lib/gameApi', async () => {
    const actual = await vi.importActual<typeof import('@/lib/gameApi')>('@/lib/gameApi');
    return {
        ...actual,
        commandApi: {
            ...actual.commandApi,
            execute: vi.fn().mockResolvedValue({ data: { success: true, logs: ['ok'] } }),
        },
    };
});

function makeUnit(overrides: Partial<TacticalUnit> = {}): TacticalUnit {
    return {
        fleetId: 10,
        officerId: 2000,
        officerName: 'Target',
        factionId: 1,
        side: 'ATTACKER',
        posX: 500,
        posY: 500,
        hp: 1000,
        maxHp: 1000,
        ships: 300,
        maxShips: 300,
        training: 50,
        morale: 80,
        energy: { beam: 20, gun: 20, shield: 20, engine: 20, warp: 10, sensor: 10 },
        formation: 'MIXED',
        commandRange: 100,
        isAlive: true,
        isRetreating: false,
        retreatProgress: 0,
        unitType: 'battleship',
        ...overrides,
    };
}

function hierarchy(): CommandHierarchyDto {
    return {
        fleetCommander: 1000,
        subFleets: [
            {
                commanderOfficerId: 2000,
                commanderName: 'Sub1',
                memberFleetIds: [10, 11],
                commanderRank: 7,
            },
        ],
        successionQueue: [1000],
        designatedSuccessor: null,
        vacancyStartTick: -1,
        commJammed: false,
        jammingTicksRemaining: 0,
        activeCommander: null,
    };
}

const command: CommandTableEntry = {
    actionCode: 'SET_TARGET',
    name: '공격 지정',
    description: '',
    commandGroup: 'COMMAND',
    enabled: true,
    reason: null,
    durationSeconds: 0,
    commandPointCost: 1,
    poolType: 'MCP',
} as unknown as CommandTableEntry;

describe('command-execution-panel gating UI (FE-03, D-09, D-11)', () => {
    afterEach(() => {
        cleanup();
    });

    beforeEach(() => {
        // Reset the tactical store to a clean slate between tests.
        useTacticalStore.setState({
            currentBattle: {
                id: 1,
                sessionId: 1,
                starSystemId: 1,
                attackerFactionId: 1,
                defenderFactionId: 2,
                phase: 'ACTIVE',
                startedAt: '',
                tickCount: 10,
                attackerFleetIds: [10],
                defenderFleetIds: [],
                units: [],
                attackerHierarchy: hierarchy(),
                defenderHierarchy: null,
            },
            units: [makeUnit({ fleetId: 42, officerId: 3001, side: 'ATTACKER' })],
            myOfficerId: 9999, // plain officer NOT in the hierarchy
        });
    });

    it('disables command button when target unit is outside hierarchy (D-09)', () => {
        const target = makeUnit({ fleetId: 42, officerId: 3001, side: 'ATTACKER' });
        render(
            <CommandExecutionPanel
                commands={[command]}
                officerId={9999}
                sessionId={1}
                pcpCurrent={100}
                mcpCurrent={100}
                selectedGroup={null}
                selectedUnit={target}
                myHierarchy={hierarchy()}
            />,
        );

        const button = screen.getByRole('button', { name: /지휘권 없음/ });
        // Gated buttons use aria-disabled (not native `disabled`) so clicks
        // still fire and onClick can branch on event.shiftKey per D-10.
        expect(button.getAttribute('aria-disabled')).toBe('true');
    });

    it('renders tooltip text "지휘권 없음" inside the disabled wrapper (D-09)', () => {
        const target = makeUnit({ fleetId: 42, officerId: 3001, side: 'ATTACKER' });
        const { container } = render(
            <CommandExecutionPanel
                commands={[command]}
                officerId={9999}
                sessionId={1}
                pcpCurrent={100}
                mcpCurrent={100}
                selectedGroup={null}
                selectedUnit={target}
                myHierarchy={hierarchy()}
            />,
        );

        // Radix Tooltip content is portalled into document.body only on open.
        // We assert the tooltip copy is present in the rendered DOM via the
        // sr-only / aria-label path — the disabled button is wrapped in a span
        // with an aria-label we set to the Korean reason string so hover isn't
        // required.
        expect(container.textContent).toContain('지휘권 없음');
    });

    it('tooltip body hints at Shift+click in the gated wrapper markup', () => {
        const target = makeUnit({ fleetId: 42, officerId: 3001, side: 'ATTACKER' });
        const { container } = render(
            <CommandExecutionPanel
                commands={[command]}
                officerId={9999}
                sessionId={1}
                pcpCurrent={100}
                mcpCurrent={100}
                selectedGroup={null}
                selectedUnit={target}
                myHierarchy={hierarchy()}
            />,
        );

        // Accept either the literal "Shift+클릭" phrase or a <kbd>Shift</kbd>.
        const kbd = container.querySelector('kbd');
        const hasKbd = kbd !== null && /shift/i.test(kbd.textContent ?? '');
        const hasPhrase = /Shift\+클릭/i.test(container.textContent ?? '');
        expect(hasKbd || hasPhrase).toBe(true);
    });
});
