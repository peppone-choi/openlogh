import { test } from "@playwright/test";
import { LEGACY, NEW_SYSTEM, PAGE_MAP } from "./parity-config";
import {
  compareStructure,
  createReport,
  extractTextContent,
  loginToLegacy,
  loginToNewSystem,
  pageLooksHealthy,
  safeGoto,
  writeParityReport,
  type ParityCheckResult,
} from "./parity-helpers";

function toAbsolute(base: string, href: string): string {
  return new URL(href, base).toString();
}

function pickHeadingFromText(text: string): string {
  const lines = text
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean)
    .slice(0, 15);
  return lines[0] ?? "";
}

function tokenizeStructure(text: string): string[] {
  const tokens = text
    .replace(/[^\p{L}\p{N}\s]/gu, " ")
    .split(/\s+/)
    .filter((token) => token.length >= 2);
  return [...new Set(tokens)].slice(0, 200);
}

test.describe("Parity: Pages", () => {
  test("mapped pages load and keep structural parity", async ({ browser }) => {
    const legacyPage = await browser.newPage();
    const nextPage = await browser.newPage();

    const legacyScreenshot = "../parity-screenshots/parity-pages-legacy.png";
    const nextScreenshot = "../parity-screenshots/parity-pages-new.png";

    const results: ParityCheckResult[] = [];

    try {
      await loginToLegacy(legacyPage);
      await loginToNewSystem(nextPage);

      for (const map of PAGE_MAP) {
        const legacyOk = await safeGoto(
          legacyPage,
          toAbsolute(LEGACY.baseUrl, map.legacy),
        );
        const newOk = await safeGoto(
          nextPage,
          toAbsolute(NEW_SYSTEM.baseUrl, map.next),
        );

        const legacyHealthy = legacyOk
          ? await pageLooksHealthy(legacyPage)
          : false;
        const newHealthy = newOk ? await pageLooksHealthy(nextPage) : false;

        results.push({
          check: `page_load_${map.name.replace(/\s+/g, "_").toLowerCase()}`,
          legacy: legacyHealthy,
          new: newHealthy,
          match: legacyHealthy === newHealthy,
          details: `${map.legacy} -> ${map.next}`,
        });

        const legacyBody = (await extractTextContent(legacyPage, "body")) ?? "";
        const nextBody = (await extractTextContent(nextPage, "body")) ?? "";

        const legacyHeading = pickHeadingFromText(legacyBody);
        const nextHeading = pickHeadingFromText(nextBody);
        results.push({
          check: `heading_presence_${map.name.replace(/\s+/g, "_").toLowerCase()}`,
          legacy: legacyHeading.length > 0,
          new: nextHeading.length > 0,
          match: legacyHeading.length > 0 && nextHeading.length > 0,
          details: `legacy="${legacyHeading.slice(0, 60)}", new="${nextHeading.slice(0, 60)}"`,
        });

        const legacySections = await legacyPage
          .locator("section, article, table, .card")
          .count();
        const nextSections = await nextPage
          .locator("section, article, table, .card")
          .count();
        results.push({
          check: `key_sections_present_${map.name.replace(/\s+/g, "_").toLowerCase()}`,
          legacy: legacySections > 0,
          new: nextSections > 0,
          match: legacySections > 0 && nextSections > 0,
          details: `legacy_sections=${legacySections}, new_sections=${nextSections}`,
        });

        const structure = compareStructure(
          tokenizeStructure(legacyBody),
          tokenizeStructure(nextBody),
        );
        results.push({
          check: `structural_overlap_${map.name.replace(/\s+/g, "_").toLowerCase()}`,
          legacy: structure.shared.length > 0,
          new: structure.shared.length > 0,
          match: structure.shared.length > 0,
          details:
            structure.shared.length > 0
              ? `shared_tokens=${structure.shared.length}, missing_in_new=${structure.missingInNew.length}, extra_in_new=${structure.extraInNew.length}`
              : "No overlapping structural tokens found",
        });
      }

      await safeGoto(legacyPage, LEGACY.gameUrl);
      await safeGoto(nextPage, `${NEW_SYSTEM.baseUrl}/`);
      await legacyPage.screenshot({ path: legacyScreenshot, fullPage: true });
      await nextPage.screenshot({ path: nextScreenshot, fullPage: true });
    } catch (error) {
      results.push({
        check: "pages_spec_execution",
        legacy: false,
        new: false,
        match: false,
        details: error instanceof Error ? error.message : "unknown error",
      });
    } finally {
      await writeParityReport(
        createReport("pages", results, {
          legacy: legacyScreenshot,
          new: nextScreenshot,
        }),
      );
      await legacyPage.close();
      await nextPage.close();
    }
  });
});
