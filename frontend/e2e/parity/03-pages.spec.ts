import { test } from '@playwright/test';
import { LEGACY, PAGE_ROUTES } from './parity-config';
import {
    assertParityMatches,
    createReport,
    extractTextContent,
    includesAnyMarker,
    loginToLegacy,
    loginToNewSystem,
    navigateInGame,
    safeGoto,
    writeParityReport,
    type ParityCheckResult,
} from './parity-helpers';

function routeKey(route: string): string {
    return route.replaceAll('/', '_').replace(/^_+/, '') || 'root';
}

test.describe('Parity: Pages', () => {
    test('all game routes show expected page content', async ({ browser }) => {
        test.setTimeout(600_000);
        const legacyPage = await browser.newPage();
        const nextPage = await browser.newPage();

        const legacyScreenshot = '../parity-screenshots/parity-pages-legacy.png';
        const nextScreenshot = '../parity-screenshots/parity-pages-new.png';
        const results: ParityCheckResult[] = [];

        try {
            await loginToLegacy(legacyPage);
            await loginToNewSystem(nextPage);

            for (const pageDef of PAGE_ROUTES) {
                const key = routeKey(pageDef.route);

                await safeGoto(legacyPage, new URL(pageDef.legacy, LEGACY.baseUrl).toString());
                await navigateInGame(nextPage, pageDef.route);

                const legacyBody = (await extractTextContent(legacyPage, 'body')) ?? '';
                const nextBody = (await extractTextContent(nextPage, 'body')) ?? '';

                const legacyHasMarker = includesAnyMarker(legacyBody, pageDef.markers);
                const nextHasMarker = includesAnyMarker(nextBody, pageDef.markers);

                results.push({
                    check: `page_markers_${key}`,
                    legacy: legacyHasMarker,
                    new: nextHasMarker,
                    match: legacyHasMarker === nextHasMarker,
                    details: `markers=${pageDef.markers.join(',')}`,
                });

                const legacyTables = await legacyPage.locator('table, section, article, main').count();
                const nextTables = await nextPage.locator('table, section, article, main').count();

                results.push({
                    check: `page_structure_${key}`,
                    legacy: legacyTables > 0,
                    new: nextTables > 0,
                    match: legacyTables > 0 && nextTables > 0,
                    details: `legacy=${legacyTables},new=${nextTables}`,
                });
            }

            await safeGoto(legacyPage, LEGACY.gameUrl);
            await navigateInGame(nextPage, '/');

            await legacyPage.screenshot({ path: legacyScreenshot, fullPage: true });
            await nextPage.screenshot({ path: nextScreenshot, fullPage: true });

            assertParityMatches('pages', results);
        } finally {
            await writeParityReport(
                createReport('pages', results, {
                    legacy: legacyScreenshot,
                    new: nextScreenshot,
                })
            );
            await legacyPage.close();
            await nextPage.close();
        }
    });
});
