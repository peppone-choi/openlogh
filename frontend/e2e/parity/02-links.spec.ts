import { test } from "@playwright/test";
import { LEGACY, NEW_SYSTEM, PAGE_MAP } from "./parity-config";
import {
  compareStructure,
  createReport,
  loginToLegacy,
  loginToNewSystem,
  pageLooksHealthy,
  safeGoto,
  uniqueStrings,
  writeParityReport,
  type ParityCheckResult,
} from "./parity-helpers";

function toAbsolute(base: string, href: string): string {
  return new URL(href, base).toString();
}

function normalizePath(input: string): string {
  try {
    const url = new URL(input, "http://localhost");
    return `${url.pathname}${url.search}`;
  } catch {
    return input;
  }
}

async function collectAnchorHrefs(page: import("@playwright/test").Page) {
  return page
    .locator("a[href]")
    .evaluateAll((links) =>
      links
        .map((link) => link.getAttribute("href") ?? "")
        .filter(
          (href) =>
            href && !href.startsWith("#") && !href.startsWith("javascript:"),
        ),
    );
}

test.describe("Parity: Links", () => {
  test("navigation links exist and navigate successfully", async ({
    browser,
  }) => {
    const legacyPage = await browser.newPage();
    const nextPage = await browser.newPage();

    const legacyScreenshot = "../parity-screenshots/parity-links-legacy.png";
    const nextScreenshot = "../parity-screenshots/parity-links-new.png";

    const results: ParityCheckResult[] = [];

    try {
      await loginToLegacy(legacyPage);
      await loginToNewSystem(nextPage);

      const legacyLinks = uniqueStrings(
        (await collectAnchorHrefs(legacyPage)).map((href) =>
          normalizePath(href),
        ),
      );
      const nextLinks = uniqueStrings(
        (await collectAnchorHrefs(nextPage)).map((href) => normalizePath(href)),
      );

      const mappedLegacy = uniqueStrings(
        PAGE_MAP.map((it) => normalizePath(it.legacy)),
      );
      const mappedNext = uniqueStrings(
        PAGE_MAP.map((it) => normalizePath(it.next)),
      );

      const legacyCoverage = compareStructure(mappedLegacy, legacyLinks);
      const newCoverage = compareStructure(mappedNext, nextLinks);

      results.push({
        check: "legacy_links_covered_by_page_map",
        legacy: legacyCoverage.missingInNew.length === 0,
        new: true,
        match: legacyCoverage.missingInNew.length === 0,
        details:
          legacyCoverage.missingInNew.length > 0
            ? `Missing legacy links in page map: ${legacyCoverage.missingInNew.join(", ")}`
            : undefined,
      });

      results.push({
        check: "new_links_covered_by_page_map",
        legacy: true,
        new: newCoverage.missingInNew.length === 0,
        match: newCoverage.missingInNew.length === 0,
        details:
          newCoverage.missingInNew.length > 0
            ? `Missing new links in page map: ${newCoverage.missingInNew.join(", ")}`
            : undefined,
      });

      const mappedPairs = PAGE_MAP.map((item) => ({
        name: item.name,
        legacy: normalizePath(item.legacy),
        next: normalizePath(item.next),
      }));
      const presentInBoth = mappedPairs
        .filter(
          (item) =>
            legacyLinks.includes(item.legacy) && nextLinks.includes(item.next),
        )
        .map((item) => item.name);
      const missingInNew = mappedPairs
        .filter(
          (item) =>
            legacyLinks.includes(item.legacy) && !nextLinks.includes(item.next),
        )
        .map((item) => item.name);
      const extraInNew = mappedPairs
        .filter(
          (item) =>
            !legacyLinks.includes(item.legacy) && nextLinks.includes(item.next),
        )
        .map((item) => item.name);

      results.push({
        check: "link_mapping_presence",
        legacy: presentInBoth.length > 0,
        new: extraInNew.length === 0,
        match: missingInNew.length === 0,
        details: `present_both=[${presentInBoth.join(", ")}], missing_in_new=[${missingInNew.join(", ")}], extra_in_new=[${extraInNew.join(", ")}]`,
      });

      for (const item of PAGE_MAP) {
        const legacyUrl = toAbsolute(LEGACY.baseUrl, item.legacy);
        const newUrl = toAbsolute(NEW_SYSTEM.baseUrl, item.next);

        const legacyOk = (await safeGoto(legacyPage, legacyUrl))
          ? await pageLooksHealthy(legacyPage)
          : false;
        const newOk = (await safeGoto(nextPage, newUrl))
          ? await pageLooksHealthy(nextPage)
          : false;

        results.push({
          check: `link_navigate_${item.name.replace(/\s+/g, "_").toLowerCase()}`,
          legacy: legacyOk,
          new: newOk,
          match: legacyOk === newOk,
          details: `${item.legacy} -> ${item.next}`,
        });
      }

      await safeGoto(legacyPage, LEGACY.gameUrl);
      await safeGoto(nextPage, `${NEW_SYSTEM.baseUrl}/`);
      await legacyPage.screenshot({ path: legacyScreenshot, fullPage: true });
      await nextPage.screenshot({ path: nextScreenshot, fullPage: true });
    } catch (error) {
      results.push({
        check: "links_spec_execution",
        legacy: false,
        new: false,
        match: false,
        details: error instanceof Error ? error.message : "unknown error",
      });
    } finally {
      await writeParityReport(
        createReport("links", results, {
          legacy: legacyScreenshot,
          new: nextScreenshot,
        }),
      );
      await legacyPage.close();
      await nextPage.close();
    }
  });
});
