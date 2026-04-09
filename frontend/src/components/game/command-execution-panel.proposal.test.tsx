// @vitest-environment jsdom
//
// Phase 14 — Plan 14-14 — command-execution-panel Shift+click proposal path
// (FE-03, D-10). Live tests (flipped from 14-05 scaffold). Asserts:
//
//  - Shift+click on a disabled button dispatches commandStore.createProposal
//    with { targetOfficerId, superiorOfficerId, targetFleetId }
//  - publishWebSocket is NOT called (no double-fire path)
//  - sonner toast fires with the Korean body "{cmd.name} 제안을 ... 발송했습니다."
//  - Plain (non-Shift) click on a disabled button does nothing

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { CommandExecutionPanel } from './command-execution-panel';
import { useCommandStore } from '@/stores/commandStore';
import { publishWebSocket } from '@/lib/websocket';
import { toast } from 'sonner';
import type { CommandTableEntry } from '@/types';
import type { CommandHierarchyDto, TacticalUnit } from '@/types/tactical';

vi.mock('@/lib/websocket', () => ({
    publishWebSocket: vi.fn(),
}));

// commandApi.execute must never be hit by the gated path — it's also mocked
// so any accidental call would blow up here instead of flaking.
vi.mock('@/lib/gameApi', async () => {
    const actual = await vi.importActual<typeof import('@/lib/gameApi')>('@/lib/gameApi');
    return {
        ...actual,
        commandApi: {
            ...actual.commandApi,
            execute: vi.fn().mockResolvedValue({ data: { success: true, logs: ['ok'] } }),
        },
        proposalApi: {
            ...actual.proposalApi,
            submit: vi.fn().mockResolvedValue({
                data: {
                    id: 1,
                    requesterId: 9999,
                    requesterName: 'me',
                    approverId: 1000,
                    approverName: 'Boss',
                    actionCode: 'SET_TARGET',
                    args: {},
                    status: 'pending' as const,
                    reason: null,
                    createdAt: '',
                    resolvedAt: null,
                },
            }),
            my: vi.fn().mockResolvedValue({ data: [] }),
        },
    };
});

vi.mock('sonner', () => ({
    toast: {
        success: vi.fn(),
        error: vi.fn(),
    },
}));

function makeUnit(overrides: Partial<TacticalUnit> = {}): TacticalUnit {
    return {
        fleetId: 42,
        officerId: 3001,
        officerName: 'Enemy Sub',
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
        subFleets: [],
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

describe('command-execution-panel Shift+click proposal path (FE-03, D-10)', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    afterEach(() => {
        cleanup();
    });

    it('Shift+click on a disabled button calls createProposal once with correct payload', async () => {
        const createProposal = vi.fn().mockResolvedValue(undefined);
        // Patch the store's createProposal for this test
        useCommandStore.setState({ createProposal });

        const target = makeUnit({ fleetId: 42, officerId: 3001 });
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
        // jsdom fireEvent.click carries shiftKey via the init object.
        fireEvent.click(button, { shiftKey: true });

        // Wait a microtask for the async handler.
        await Promise.resolve();
        await Promise.resolve();

        expect(createProposal).toHaveBeenCalledTimes(1);
        const args = createProposal.mock.calls[0];
        // Signature: createProposal(requesterOfficerId, commandCode, payload)
        expect(args[0]).toBe(9999);
        expect(args[1]).toBe('SET_TARGET');
        expect(args[2]).toMatchObject({
            targetOfficerId: 3001,
            superiorOfficerId: 1000,
            targetFleetId: 42,
        });
    });

    it('Shift+click on disabled button does NOT call publishWebSocket', async () => {
        const createProposal = vi.fn().mockResolvedValue(undefined);
        useCommandStore.setState({ createProposal });

        const target = makeUnit({ fleetId: 42, officerId: 3001 });
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
        fireEvent.click(button, { shiftKey: true });

        await Promise.resolve();
        await Promise.resolve();

        expect(publishWebSocket).not.toHaveBeenCalled();
    });

    it('Shift+click fires sonner toast with Korean proposal-sent body', async () => {
        const createProposal = vi.fn().mockResolvedValue(undefined);
        useCommandStore.setState({ createProposal });

        const target = makeUnit({ fleetId: 42, officerId: 3001 });
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
        fireEvent.click(button, { shiftKey: true });

        // microtasks for async createProposal().then(toast)
        await Promise.resolve();
        await Promise.resolve();
        await Promise.resolve();

        expect(toast.success).toHaveBeenCalled();
        const msg = (toast.success as unknown as { mock: { calls: unknown[][] } }).mock.calls[0][0] as string;
        expect(msg).toContain('공격 지정');
        expect(msg).toContain('제안');
        expect(msg).toContain('발송');
    });

    it('plain (no-Shift) click on a disabled button is a no-op (no createProposal, no publishWebSocket)', () => {
        const createProposal = vi.fn().mockResolvedValue(undefined);
        useCommandStore.setState({ createProposal });

        const target = makeUnit({ fleetId: 42, officerId: 3001 });
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
        // Plain click — the button is `disabled`, so the DOM shouldn't fire
        // the handler at all. Assert the handler wasn't called either way.
        fireEvent.click(button);

        expect(createProposal).not.toHaveBeenCalled();
        expect(publishWebSocket).not.toHaveBeenCalled();
    });
});
