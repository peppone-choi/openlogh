import { test } from "@playwright/test";
import { LEGACY, MAIN_SECTIONS, NEW_SYSTEM } from "./parity-config";
import {
  createReport,
  extractTextContent,
  loginToLegacy,
  loginToNewSystem,
  writeParityReport,
  type ParityCheckResult,
} from "./parity-helpers";

function hasAny(text: string, candidates: readonly string[]): boolean {
  return candidates.some((candidate) => text.includes(candidate));
}

test.describe("Parity: Main", () => {
  test("main page structure parity between legacy and new", async ({
    browser,
  }) => {
    const legacyPage = await browser.newPage();
    const nextPage = await browser.newPage();

    const legacyScreenshot = "../parity-screenshots/parity-main-legacy.png";
    const nextScreenshot = "../parity-screenshots/parity-main-new.png";

    const results: ParityCheckResult[] = [];

    try {
      await loginToLegacy(legacyPage);
      await loginToNewSystem(nextPage);

      const legacyBody = (await extractTextContent(legacyPage, "body")) ?? "";
      const nextBody = (await extractTextContent(nextPage, "body")) ?? "";

      results.push({
        check: "main_page_loaded",
        legacy: legacyBody.length > 0,
        new: nextBody.length > 0,
        match: legacyBody.length > 0 && nextBody.length > 0,
      });

      const serverLegacy = hasAny(legacyBody, MAIN_SECTIONS.serverInfo);
      const serverNext = hasAny(nextBody, MAIN_SECTIONS.serverInfo);
      results.push({
        check: "server_info_displayed",
        legacy: serverLegacy,
        new: serverNext,
        match: serverLegacy === serverNext,
      });

      const mapLegacy =
        (await legacyPage.locator("img[usemap], map area, .city").count()) > 0;
      const mapNext =
        (await nextPage
          .locator("canvas, [data-city-id], [class*='city']")
          .count()) > 0;
      results.push({
        check: "map_section_exists",
        legacy: mapLegacy,
        new: mapNext,
        match: mapLegacy === mapNext,
      });

      const commandLegacy =
        (await legacyPage.locator("text=명령").count()) > 0 ||
        (await legacyPage.locator("text=예약").count()) > 0;
      const commandNext =
        (await nextPage.locator("text=12턴 예약 편집").count()) > 0 ||
        (await nextPage.locator("text=명령").count()) > 0;
      results.push({
        check: "command_list_exists",
        legacy: commandLegacy,
        new: commandNext,
        match: commandLegacy === commandNext,
      });

      const cityLegacy = hasAny(legacyBody, MAIN_SECTIONS.cityInfo);
      const cityNext = hasAny(nextBody, MAIN_SECTIONS.cityInfo);
      results.push({
        check: "city_info_panel_fields_present",
        legacy: cityLegacy,
        new: cityNext,
        match: cityLegacy === cityNext,
      });

      const nationLegacy = hasAny(legacyBody, MAIN_SECTIONS.nationInfo);
      const nationNext = hasAny(nextBody, MAIN_SECTIONS.nationInfo);
      results.push({
        check: "nation_info_panel_fields_present",
        legacy: nationLegacy,
        new: nationNext,
        match: nationLegacy === nationNext,
      });

      const legacyHas3Stats =
        legacyBody.includes("통솔") &&
        legacyBody.includes("무력") &&
        legacyBody.includes("지력");
      const nextHas5Stats =
        nextBody.includes("통솔") &&
        nextBody.includes("무력") &&
        nextBody.includes("지력") &&
        nextBody.includes("정치") &&
        nextBody.includes("매력");
      results.push({
        check: "general_info_panel_stats_present",
        legacy: legacyHas3Stats,
        new: nextHas5Stats,
        match: legacyHas3Stats && nextHas5Stats,
        details: "Legacy expects 3 stats, new system expects 5 stats",
      });

      const eventsLegacy = hasAny(legacyBody, MAIN_SECTIONS.news);
      const eventsNext = hasAny(nextBody, MAIN_SECTIONS.news);
      results.push({
        check: "news_sections_exist",
        legacy: eventsLegacy,
        new: eventsNext,
        match: eventsLegacy === eventsNext,
      });

      await legacyPage.screenshot({ path: legacyScreenshot, fullPage: true });
      await nextPage.screenshot({ path: nextScreenshot, fullPage: true });
    } catch (error) {
      results.push({
        check: "main_spec_execution",
        legacy: false,
        new: false,
        match: false,
        details: error instanceof Error ? error.message : "unknown error",
      });
    } finally {
      await writeParityReport(
        createReport("main", results, {
          legacy: legacyScreenshot,
          new: nextScreenshot,
        }),
      );
      await legacyPage.close();
      await nextPage.close();
    }
  });
});
