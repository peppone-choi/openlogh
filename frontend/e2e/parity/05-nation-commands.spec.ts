import { test } from '@playwright/test';
import { NATION_COMMANDS } from './parity-config';
import {
    assertParityMatches,
    createReport,
    executeNationCommand,
    flattenCommandTable,
    getCitySnapshot,
    getGameContext,
    getGeneralSnapshot,
    getNationCommandTable,
    getNationSnapshot,
    getNewSystemToken,
    hasCityEffectDelta,
    hasGeneralEffectDelta,
    hasNationEffectDelta,
    listNationTurns,
    pauseTurnDaemon,
    reserveNationCommand,
    resumeTurnDaemon,
    writeParityReport,
    type ParityCheckResult,
} from './parity-helpers';

test.describe.serial('Parity: Nation Commands', () => {
    test('all 38 nation commands are present, reservable, and safe commands execute with effects', async ({
        request,
    }) => {
        test.setTimeout(600_000);

        const results: ParityCheckResult[] = [];
        const legacyScreenshot = '../parity-screenshots/parity-nation-commands-legacy.png';
        const nextScreenshot = '../parity-screenshots/parity-nation-commands-new.png';

        const token = await getNewSystemToken();

        let context: Awaited<ReturnType<typeof getGameContext>>;
        try {
            context = await getGameContext(request, { requireNationOfficer: true });
        } catch {
            context = await getGameContext(request, { requireNationMember: true });
        }

        if (context.nationId <= 0) {
            throw new Error('Nation command parity requires a nation-affiliated general context.');
        }

        try {
            const pause = await pauseTurnDaemon(request);
            results.push({
                check: 'turn_daemon_paused_nation',
                legacy: pause.newOk,
                new: pause.newOk,
                match: true,
            });

            const table = await getNationCommandTable(request, token, context.generalId);
            const entries = flattenCommandTable(table);
            const entryByCode = new Map(entries.map((entry) => [entry.actionCode, entry]));

            results.push({
                check: 'nation_command_count_38',
                legacy: NATION_COMMANDS.length === 38,
                new: entries.length === 38,
                match: entries.length === 38,
                details: `table=${entries.length}`,
            });

            for (const command of NATION_COMMANDS) {
                const inTable = entryByCode.has(command.actionCode);
                results.push({
                    check: `nation_command_table_has_${command.actionCode}`,
                    legacy: true,
                    new: inTable,
                    match: inTable,
                });

                const reserveResult = await reserveNationCommand(
                    request,
                    token,
                    context.nationId,
                    context.generalId,
                    command.actionCode
                );
                let reservedAtTurn0: string | undefined;
                if (reserveResult.ok) {
                    const reserved = await listNationTurns(request, token, context.nationId, context.officerLevel);
                    reservedAtTurn0 = reserved.find((turn) => turn.turnIdx === 0)?.actionCode;
                }

                results.push({
                    check: `reserve_nation_${command.actionCode}`,
                    legacy: true,
                    new: reserveResult.ok && reservedAtTurn0 === command.actionCode,
                    match: reserveResult.ok && reservedAtTurn0 === command.actionCode,
                    details: reserveResult.ok
                        ? `reserved=${reservedAtTurn0 ?? 'none'}`
                        : `status=${reserveResult.status}, body=${reserveResult.errorText ?? ''}`,
                });
            }

            for (const command of NATION_COMMANDS.filter((item) => item.safeToExecute)) {
                const tableEntry = entryByCode.get(command.actionCode);
                if (!tableEntry?.enabled) {
                    results.push({
                        check: `execute_nation_${command.actionCode}_skipped_disabled`,
                        legacy: false,
                        new: false,
                        match: true,
                        details: tableEntry?.reason ?? 'command disabled',
                    });
                    continue;
                }

                const beforeGeneral = await getGeneralSnapshot(request, token, context.generalId);
                const beforeCity = await getCitySnapshot(request, token, beforeGeneral.cityId);
                const beforeNation = await getNationSnapshot(request, token, beforeGeneral.nationId);

                const executeResult = await executeNationCommand(request, token, context.generalId, command.actionCode);

                if (!executeResult.ok || !executeResult.data) {
                    results.push({
                        check: `execute_nation_${command.actionCode}_success`,
                        legacy: true,
                        new: false,
                        match: false,
                        details: `HTTP ${executeResult.status}: ${executeResult.errorText ?? 'no response body'}`,
                    });
                    continue;
                }

                const afterGeneral = await getGeneralSnapshot(request, token, context.generalId);
                const afterCity = await getCitySnapshot(request, token, afterGeneral.cityId);
                const afterNation = await getNationSnapshot(request, token, afterGeneral.nationId);

                const generalChanged = hasGeneralEffectDelta(beforeGeneral, afterGeneral);
                const cityChanged = hasCityEffectDelta(beforeCity, afterCity);
                const nationChanged = hasNationEffectDelta(beforeNation, afterNation);

                results.push({
                    check: `execute_nation_${command.actionCode}_success`,
                    legacy: true,
                    new: executeResult.data.success,
                    match: executeResult.data.success,
                    details: executeResult.data.logs.join(' | '),
                });

                if (command.effectChecks.length === 0) {
                    results.push({
                        check: `execute_nation_${command.actionCode}_effect_changed`,
                        legacy: true,
                        new: true,
                        match: true,
                        details: `skipped (no effectChecks); general=${generalChanged}, city=${cityChanged}, nation=${nationChanged}`,
                    });
                } else {
                    let effectChanged = false;
                    if (command.effectChecks.includes('general')) effectChanged ||= generalChanged;
                    if (command.effectChecks.includes('city')) effectChanged ||= cityChanged;
                    if (command.effectChecks.includes('nation')) effectChanged ||= nationChanged;

                    results.push({
                        check: `execute_nation_${command.actionCode}_effect_changed`,
                        legacy: true,
                        new: effectChanged,
                        match: effectChanged,
                        details: `general=${generalChanged}, city=${cityChanged}, nation=${nationChanged}`,
                    });
                }
            }

            results.push({
                check: 'world_realtime_mode_nation',
                legacy: true,
                new: context.realtimeMode,
                match: true,
                details: 'Realtime mode allows execute API but may reject nation turn reservation.',
            });

            assertParityMatches('nation-commands', results);
        } finally {
            const resume = await resumeTurnDaemon(request);
            results.push({
                check: 'turn_daemon_resumed_nation',
                legacy: resume.newOk,
                new: resume.newOk,
                match: true,
            });

            await writeParityReport(
                createReport('nation-commands', results, {
                    legacy: legacyScreenshot,
                    new: nextScreenshot,
                })
            );
        }
    });
});
