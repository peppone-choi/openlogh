import { test } from "@playwright/test";
import { LEGACY, NEW_SYSTEM } from "./parity-config";
import {
  createReport,
  extractTextContent,
  loginToLegacy,
  loginToNewSystem,
  pauseTurnDaemon,
  resumeTurnDaemon,
  safeGoto,
  writeParityReport,
  type ParityCheckResult,
} from "./parity-helpers";

function metricValue(text: string, label: string): number | null {
  const regex = new RegExp(`${label}\\D+(\\d{1,3}(?:,\\d{3})*)`);
  const matched = text.match(regex);
  if (!matched) return null;
  return Number(matched[1].replaceAll(",", ""));
}

function personalRecordSlice(text: string): string {
  const start = text.indexOf("개인 기록");
  if (start < 0) return "";
  return text.slice(start, start + 500);
}

async function reserveLegacyAgriculture(page: import("@playwright/test").Page) {
  try {
    const agriculture = page.getByText(/농지개간|농업/).first();
    if (await agriculture.count()) {
      await agriculture.click();
      return true;
    }

    const commandButtons = page
      .locator("button, a")
      .filter({ hasText: /명령|예약|선택/ })
      .first();
    if (await commandButtons.count()) {
      await commandButtons.click();
      const fallback = page.getByText(/농지개간|농업/).first();
      if (await fallback.count()) {
        await fallback.click();
        return true;
      }
    }
    return false;
  } catch {
    return false;
  }
}

async function reserveNewAgriculture(page: import("@playwright/test").Page) {
  try {
    const opened = await safeGoto(page, `${NEW_SYSTEM.baseUrl}/commands`);
    if (!opened) return false;

    const fillButton = page
      .getByRole("button", { name: "선택 채우기" })
      .first();
    if (await fillButton.count()) {
      await fillButton.click();
    } else {
      const editButton = page
        .locator("button")
        .filter({ hasText: /✎|Pencil/ })
        .first();
      if (await editButton.count()) {
        await editButton.click();
      }
    }

    const agriculture = page
      .getByRole("button", { name: /농지개간|농업/ })
      .first();
    if (await agriculture.count()) {
      await agriculture.click();
      return true;
    }

    const anyAgriculture = page.getByText(/농지개간|농업/).first();
    if (await anyAgriculture.count()) {
      await anyAgriculture.click();
      return true;
    }
    return false;
  } catch {
    return false;
  }
}

test.describe.serial("Parity: Commands", () => {
  test("command execution parity after one turn", async ({
    browser,
    request,
  }) => {
    test.setTimeout(180_000);

    const legacyPage = await browser.newPage();
    const nextPage = await browser.newPage();

    const legacyScreenshot = "../parity-screenshots/parity-commands-legacy.png";
    const nextScreenshot = "../parity-screenshots/parity-commands-new.png";

    const results: ParityCheckResult[] = [];

    try {
      await loginToLegacy(legacyPage);
      await loginToNewSystem(nextPage);

      const pause = await pauseTurnDaemon(request);
      results.push({
        check: "pause_turn_daemon",
        legacy: pause.legacyOk,
        new: pause.newOk,
        match: pause.newOk,
        details: "Legacy pause endpoint is best-effort; new system must pause.",
      });

      const legacyBeforeText =
        (await extractTextContent(legacyPage, "body")) ?? "";
      const nextBeforeText = (await extractTextContent(nextPage, "body")) ?? "";

      const legacyBeforeAgri = metricValue(legacyBeforeText, "농업");
      const nextBeforeAgri = metricValue(nextBeforeText, "농업");
      const legacyBeforeRecord = personalRecordSlice(legacyBeforeText);
      const nextBeforeRecord = personalRecordSlice(nextBeforeText);

      const legacyReserved = await reserveLegacyAgriculture(legacyPage);
      const newReserved = await reserveNewAgriculture(nextPage);
      results.push({
        check: "reserve_agriculture_command",
        legacy: legacyReserved,
        new: newReserved,
        match: legacyReserved === newReserved,
      });

      const resume = await resumeTurnDaemon(request);
      results.push({
        check: "resume_turn_daemon",
        legacy: resume.legacyOk,
        new: resume.newOk,
        match: resume.newOk,
        details:
          "Legacy resume endpoint is best-effort; new system must resume.",
      });

      await legacyPage.waitForTimeout(65_000);
      await nextPage.waitForTimeout(65_000);

      await safeGoto(legacyPage, LEGACY.gameUrl);
      await safeGoto(nextPage, `${NEW_SYSTEM.baseUrl}/`);

      const legacyAfterText =
        (await extractTextContent(legacyPage, "body")) ?? "";
      const nextAfterText = (await extractTextContent(nextPage, "body")) ?? "";

      const legacyAfterAgri = metricValue(legacyAfterText, "농업");
      const nextAfterAgri = metricValue(nextAfterText, "농업");
      const legacyAfterRecord = personalRecordSlice(legacyAfterText);
      const nextAfterRecord = personalRecordSlice(nextAfterText);

      const legacyAgriChanged =
        legacyBeforeAgri !== null && legacyAfterAgri !== null
          ? legacyBeforeAgri !== legacyAfterAgri
          : false;
      const nextAgriChanged =
        nextBeforeAgri !== null && nextAfterAgri !== null
          ? nextBeforeAgri !== nextAfterAgri
          : false;

      results.push({
        check: "city_stat_changed_after_command",
        legacy: legacyAgriChanged,
        new: nextAgriChanged,
        match: legacyAgriChanged === nextAgriChanged,
        details: `legacy(${legacyBeforeAgri} -> ${legacyAfterAgri}), new(${nextBeforeAgri} -> ${nextAfterAgri})`,
      });

      const legacyRecordChanged =
        legacyBeforeRecord.length > 0 &&
        legacyBeforeRecord !== legacyAfterRecord;
      const nextRecordChanged =
        nextBeforeRecord.length > 0 && nextBeforeRecord !== nextAfterRecord;

      results.push({
        check: "personal_record_updated",
        legacy: legacyRecordChanged,
        new: nextRecordChanged,
        match: legacyRecordChanged === nextRecordChanged,
      });

      await legacyPage.screenshot({ path: legacyScreenshot, fullPage: true });
      await nextPage.screenshot({ path: nextScreenshot, fullPage: true });
    } catch (error) {
      results.push({
        check: "commands_spec_execution",
        legacy: false,
        new: false,
        match: false,
        details: error instanceof Error ? error.message : "unknown error",
      });
    } finally {
      await resumeTurnDaemon(request);
      await writeParityReport(
        createReport("commands", results, {
          legacy: legacyScreenshot,
          new: nextScreenshot,
        }),
      );
      await legacyPage.close();
      await nextPage.close();
    }
  });
});
