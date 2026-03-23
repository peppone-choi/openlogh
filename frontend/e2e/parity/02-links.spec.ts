import { test } from '@playwright/test';
import { LEGACY, NEW_SYSTEM, PAGE_ROUTES } from './parity-config';
import {
    assertParityMatches,
    createReport,
    navigateInGame,
    safeGoto,
    loginToLegacy,
    loginToNewSystem,
    writeParityReport,
    type ParityCheckResult,
} from './parity-helpers';

function routeKey(route: string): string {
    return route.replaceAll('/', '_').replace(/^_+/, '') || 'root';
}

test.describe('Parity: Links', () => {
    test('all configured routes are valid and reachable', async ({ browser }) => {
        test.setTimeout(600_000);
        const legacyPage = await browser.newPage();
        const nextPage = await browser.newPage();

        const legacyScreenshot = '../parity-screenshots/parity-links-legacy.png';
        const nextScreenshot = '../parity-screenshots/parity-links-new.png';
        const results: ParityCheckResult[] = [];

        try {
            await loginToLegacy(legacyPage);
            await loginToNewSystem(nextPage);

            results.push({
                check: 'route_count_expected_37',
                legacy: PAGE_ROUTES.length === 37,
                new: PAGE_ROUTES.length === 37,
                match: PAGE_ROUTES.length === 37,
                details: `count=${PAGE_ROUTES.length}`,
            });

            for (const pageDef of PAGE_ROUTES) {
                const key = routeKey(pageDef.route);

                const legacyOk = await safeGoto(legacyPage, new URL(pageDef.legacy, LEGACY.baseUrl).toString());
                const newOk = await navigateInGame(nextPage, pageDef.route);
                const atExpectedRoute = nextPage.url().includes(pageDef.route);

                results.push({
                    check: `route_reachable_${key}`,
                    legacy: legacyOk,
                    new: newOk,
                    match: legacyOk === newOk,
                    details: `${pageDef.legacy} -> ${pageDef.route}`,
                });

                results.push({
                    check: `route_path_exact_${key}`,
                    legacy: true,
                    new: atExpectedRoute,
                    match: atExpectedRoute,
                    details: `url=${nextPage.url()}`,
                });
            }

            await safeGoto(legacyPage, LEGACY.gameUrl);
            await navigateInGame(nextPage, '/');
            await legacyPage.screenshot({ path: legacyScreenshot, fullPage: true });
            await nextPage.screenshot({ path: nextScreenshot, fullPage: true });

            assertParityMatches('links', results);
        } finally {
            await writeParityReport(
                createReport('links', results, {
                    legacy: legacyScreenshot,
                    new: nextScreenshot,
                })
            );
            await legacyPage.close();
            await nextPage.close();
        }
    });
});
