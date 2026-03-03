import { test } from "@playwright/test";
import { GENERAL_COMMANDS } from "./parity-config";
import {
  createReport,
  executeGeneralCommand,
  flattenCommandTable,
  getCitySnapshot,
  getGameContext,
  getGeneralCommandTable,
  getGeneralSnapshot,
  getNationSnapshot,
  getNewSystemToken,
  hasCityEffectDelta,
  hasGeneralEffectDelta,
  hasNationEffectDelta,
  listGeneralTurns,
  pauseTurnDaemon,
  reserveGeneralCommand,
  resumeTurnDaemon,
  writeParityReport,
  type ParityCheckResult,
} from "./parity-helpers";

test.describe.serial("Parity: Commands", () => {
  test("all 55 general commands are present, reservable, and safe commands execute with effects", async ({
    request,
  }) => {
    test.setTimeout(600_000);

    const results: ParityCheckResult[] = [];
    const legacyScreenshot = "../parity-screenshots/parity-commands-legacy.png";
    const nextScreenshot = "../parity-screenshots/parity-commands-new.png";

    const token = await getNewSystemToken();
    const context = await getGameContext(request);

    try {
      const pause = await pauseTurnDaemon(request);
      results.push({
        check: "turn_daemon_paused",
        legacy: true,
        new: pause.newOk,
        match: pause.newOk,
      });

      const table = await getGeneralCommandTable(
        request,
        token,
        context.generalId,
      );
      const entries = flattenCommandTable(table);
      const entryByCode = new Map(
        entries.map((entry) => [entry.actionCode, entry]),
      );

      results.push({
        check: "general_command_count_55",
        legacy: GENERAL_COMMANDS.length === 55,
        new: entries.length === 55,
        match: entries.length === 55,
        details: `table=${entries.length}`,
      });

      for (const command of GENERAL_COMMANDS) {
        const inTable = entryByCode.has(command.actionCode);
        results.push({
          check: `command_table_has_${command.actionCode}`,
          legacy: true,
          new: inTable,
          match: inTable,
        });

        const reserveResult = await reserveGeneralCommand(
          request,
          token,
          context.generalId,
          command.actionCode,
        );
        let reservedAtTurn0: string | undefined;
        if (reserveResult.ok) {
          const reserved = await listGeneralTurns(
            request,
            token,
            context.generalId,
          );
          reservedAtTurn0 = reserved.find(
            (turn) => turn.turnIdx === 0,
          )?.actionCode;
        }

        results.push({
          check: `reserve_${command.actionCode}`,
          legacy: true,
          new: reserveResult.ok && reservedAtTurn0 === command.actionCode,
          match: reserveResult.ok && reservedAtTurn0 === command.actionCode,
          details: reserveResult.ok
            ? `reserved=${reservedAtTurn0 ?? "none"}`
            : `status=${reserveResult.status}, body=${reserveResult.errorText ?? ""}`,
        });
      }

      for (const command of GENERAL_COMMANDS.filter(
        (item) => item.safeToExecute,
      )) {
        const tableEntry = entryByCode.get(command.actionCode);
        if (!tableEntry?.enabled) {
          results.push({
            check: `execute_${command.actionCode}_skipped_disabled`,
            legacy: true,
            new: false,
            match: false,
            details: tableEntry?.reason ?? "command disabled",
          });
          continue;
        }

        const beforeGeneral = await getGeneralSnapshot(
          request,
          token,
          context.generalId,
        );
        const beforeCity = await getCitySnapshot(
          request,
          token,
          beforeGeneral.cityId,
        );
        const beforeNation = await getNationSnapshot(
          request,
          token,
          beforeGeneral.nationId,
        );

        const executeResult = await executeGeneralCommand(
          request,
          token,
          context.generalId,
          command.actionCode,
        );

        if (!executeResult.ok || !executeResult.data) {
          results.push({
            check: `execute_${command.actionCode}_success`,
            legacy: true,
            new: false,
            match: false,
            details: `HTTP ${executeResult.status}: ${executeResult.errorText ?? "no response body"}`,
          });
          continue;
        }

        const afterGeneral = await getGeneralSnapshot(
          request,
          token,
          context.generalId,
        );
        const afterCity = await getCitySnapshot(
          request,
          token,
          afterGeneral.cityId,
        );
        const afterNation = await getNationSnapshot(
          request,
          token,
          afterGeneral.nationId,
        );

        const generalChanged = hasGeneralEffectDelta(
          beforeGeneral,
          afterGeneral,
        );
        const cityChanged = hasCityEffectDelta(beforeCity, afterCity);
        const nationChanged = hasNationEffectDelta(beforeNation, afterNation);

        results.push({
          check: `execute_${command.actionCode}_success`,
          legacy: true,
          new: executeResult.data.success,
          match: executeResult.data.success,
          details: executeResult.data.logs.join(" | "),
        });

        if (command.effectChecks.length === 0) {
          results.push({
            check: `execute_${command.actionCode}_effect_changed`,
            legacy: true,
            new: true,
            match: true,
            details: `skipped (no effectChecks); general=${generalChanged}, city=${cityChanged}, nation=${nationChanged}`,
          });
        } else {
          let effectChanged = false;
          if (command.effectChecks.includes("general"))
            effectChanged ||= generalChanged;
          if (command.effectChecks.includes("city"))
            effectChanged ||= cityChanged;
          if (command.effectChecks.includes("nation"))
            effectChanged ||= nationChanged;

          results.push({
            check: `execute_${command.actionCode}_effect_changed`,
            legacy: true,
            new: effectChanged,
            match: effectChanged,
            details: `general=${generalChanged}, city=${cityChanged}, nation=${nationChanged}`,
          });
        }
      }

      results.push({
        check: "world_realtime_mode",
        legacy: true,
        new: context.realtimeMode,
        match: true,
        details:
          "Realtime mode allows execute API but may reject turn reservation.",
      });
    } finally {
      const resume = await resumeTurnDaemon(request);
      results.push({
        check: "turn_daemon_resumed",
        legacy: true,
        new: resume.newOk,
        match: resume.newOk,
      });

      await writeParityReport(
        createReport("commands", results, {
          legacy: legacyScreenshot,
          new: nextScreenshot,
        }),
      );
    }
  });
});
