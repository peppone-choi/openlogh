import { test } from "@playwright/test";
import { MAIN_SECTIONS } from "./parity-config";
import {
  createReport,
  extractTextContent,
  includesAnyMarker,
  loginToLegacy,
  loginToNewSystem,
  writeParityReport,
  type ParityCheckResult,
} from "./parity-helpers";

test.describe("Parity: Main", () => {
  test("main page sections are visible in legacy and new", async ({
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
        check: "main_loaded",
        legacy: legacyBody.length > 0,
        new: nextBody.length > 0,
        match: legacyBody.length > 0 && nextBody.length > 0,
      });

      const sectionEntries = Object.entries(MAIN_SECTIONS);
      for (const [sectionName, markers] of sectionEntries) {
        const legacyHas = includesAnyMarker(legacyBody, markers);
        const nextHas = includesAnyMarker(nextBody, markers);
        results.push({
          check: `main_section_${sectionName}`,
          legacy: legacyHas,
          new: nextHas,
          match: legacyHas === nextHas,
          details: `markers=${markers.join(",")}`,
        });
      }

      const legacyMapVisible =
        (await legacyPage.locator("img[usemap], map area, .city").count()) > 0;
      const newMapVisible =
        (await nextPage
          .locator("canvas, [data-city-id], [class*='map']")
          .count()) > 0;
      results.push({
        check: "main_map_widget_visible",
        legacy: legacyMapVisible,
        new: newMapVisible,
        match: legacyMapVisible === newMapVisible,
      });

      await legacyPage.screenshot({ path: legacyScreenshot, fullPage: true });
      await nextPage.screenshot({ path: nextScreenshot, fullPage: true });
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
