import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import type { APIRequestContext, APIResponse, Page } from '@playwright/test';
import { LEGACY, NEW_SYSTEM } from './parity-config';

export interface ParityCheckResult {
    check: string;
    legacy: boolean;
    new: boolean;
    match: boolean;
    details?: string;
}

export interface ParityReport {
    section: string;
    timestamp: string;
    results: ParityCheckResult[];
    screenshots: {
        legacy: string;
        new: string;
    };
}

export interface StructureCompareResult {
    match: boolean;
    missingInNew: string[];
    extraInNew: string[];
    shared: string[];
}

export interface CommandTableEntry {
    actionCode: string;
    name: string;
    category: string;
    enabled: boolean;
    reason?: string | null;
}

export interface GeneralTurnEntry {
    turnIdx: number;
    actionCode: string;
}

export interface GameContext {
    worldId: number;
    generalId: number;
    cityId: number;
    nationId: number;
    officerLevel: number;
    realtimeMode: boolean;
}

export interface ApiCallResult<T> {
    ok: boolean;
    status: number;
    data?: T;
    errorText?: string;
}

export interface GeneralSnapshot {
    id: number;
    worldId: number;
    cityId: number;
    nationId: number;
    officerLevel: number;
    gold: number;
    rice: number;
    crew: number;
    train: number;
    atmos: number;
    commandPoints: number;
    injury: number;
}

export interface CitySnapshot {
    id: number;
    agri: number;
    comm: number;
    secu: number;
    def: number;
    wall: number;
    pop: number;
    trust: number;
    trade: number;
}

export interface NationSnapshot {
    id: number;
    gold: number;
    rice: number;
    tech: number;
    power: number;
    level: number;
    bill: number;
    rate: number;
}

interface ResolveContextOptions {
    requireNationOfficer?: boolean;
    requireNationMember?: boolean;
}

interface WorldListItem {
    id: number;
    name?: string;
    scenarioCode?: string;
}

interface NationLite {
    id: number;
    level?: number;
    capitalCityId?: number | null;
}

interface CityLite {
    id: number;
}

const REPORT_DIR = process.env.PARITY_REPORT_DIR ?? '../parity-screenshots';

const GENERAL_EFFECT_FIELDS: (keyof GeneralSnapshot)[] = [
    'gold',
    'rice',
    'crew',
    'train',
    'atmos',
    'commandPoints',
    'injury',
];
const CITY_EFFECT_FIELDS: (keyof CitySnapshot)[] = ['agri', 'comm', 'secu', 'def', 'wall', 'pop', 'trust', 'trade'];
const NATION_EFFECT_FIELDS: (keyof NationSnapshot)[] = ['gold', 'rice', 'tech', 'power', 'level', 'bill', 'rate'];

async function ensureHttpReachable(url: string, label: string): Promise<void> {
    let lastError: unknown;

    for (let attempt = 0; attempt < 3; attempt++) {
        try {
            const response = await fetch(url, {
                method: 'GET',
                signal: AbortSignal.timeout(8_000),
            });

            if (response.ok || response.status >= 300) {
                return;
            }

            lastError = new Error(`${label} reachable check failed: HTTP ${response.status}`);
        } catch (error) {
            lastError = error;
        }

        await new Promise((resolve) => setTimeout(resolve, 1_000 * (attempt + 1)));
    }

    const reason = lastError instanceof Error ? lastError.message : String(lastError ?? 'unknown error');
    throw new Error(`${label} is not reachable: ${reason}`);
}

function normalizeText(value: string): string {
    return value.replace(/\s+/g, ' ').trim();
}

export function compareStructure(
    legacyData: Record<string, unknown> | string[] | null | undefined,
    newData: Record<string, unknown> | string[] | null | undefined
): StructureCompareResult {
    const legacyKeys = Array.isArray(legacyData) ? legacyData : Object.keys(legacyData ?? {});
    const newKeys = Array.isArray(newData) ? newData : Object.keys(newData ?? {});

    const legacySet = new Set(legacyKeys);
    const newSet = new Set(newKeys);

    const missingInNew = legacyKeys.filter((key) => !newSet.has(key));
    const extraInNew = newKeys.filter((key) => !legacySet.has(key));
    const shared = legacyKeys.filter((key) => newSet.has(key));

    return {
        match: missingInNew.length === 0,
        missingInNew,
        extraInNew,
        shared,
    };
}

export async function extractTextContent(page: Page, selector: string): Promise<string | null> {
    try {
        const locator = page.locator(selector).first();
        if (!(await locator.count())) {
            return null;
        }
        const text = await locator.innerText();
        return normalizeText(text);
    } catch {
        return null;
    }
}

export async function loginToLegacy(page: Page): Promise<void> {
    await ensureHttpReachable(LEGACY.baseUrl, 'Legacy base URL');

    let lastError: unknown;

    for (let attempt = 0; attempt < 4; attempt++) {
        try {
            await safeGoto(page, LEGACY.baseUrl);

            const apiLoginResponse = await page.request.post(`${LEGACY.baseUrl}/sam/api.php?path=Login%2FLoginByID`, {
                headers: { 'Content-Type': 'application/json' },
                data: {
                    username: LEGACY.credentials.username,
                    password: LEGACY.credentials.password,
                },
                timeout: 30_000,
            });

            if (!apiLoginResponse.ok()) {
                throw new Error(`Legacy login request failed: HTTP ${apiLoginResponse.status()}`);
            }

            const apiLoginBody = (await apiLoginResponse.json()) as { result?: boolean; reason?: string };
            if (!apiLoginBody.result) {
                throw new Error(`Legacy login failed: ${apiLoginBody.reason ?? 'unknown reason'}`);
            }

            const enteredGame = await safeGoto(page, LEGACY.gameUrl);
            if (!enteredGame) {
                const fallbackEnteredGame = await safeGoto(page, `${LEGACY.baseUrl}/sam/che`);
                if (!fallbackEnteredGame) {
                    throw new Error('Legacy game route is unreachable after login');
                }
            }

            return;
        } catch (error) {
            lastError = error;
            if (attempt === 3) {
                break;
            }
            await new Promise((resolve) => setTimeout(resolve, 1_500 * (attempt + 1)));
        }
    }

    throw lastError instanceof Error ? lastError : new Error('Legacy login failed after retries');
}

let cachedNewSystemToken: string | null = null;

export async function getNewSystemToken(_request?: APIRequestContext): Promise<string> {
    const tokenFromEnv = process.env.PARITY_NEW_TOKEN?.trim();
    if (tokenFromEnv) {
        cachedNewSystemToken = tokenFromEnv;
        return tokenFromEnv;
    }

    if (cachedNewSystemToken) return cachedNewSystemToken;

    await ensureHttpReachable(`${NEW_SYSTEM.apiUrl}/health`, 'New API health endpoint');

    for (let attempt = 0; attempt < 6; attempt++) {
        try {
            const res = await fetch(`${NEW_SYSTEM.apiUrl}/auth/login`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(NEW_SYSTEM.credentials),
                signal: AbortSignal.timeout(30_000),
            });

            if (!res.ok) {
                throw new Error(`New system login failed: HTTP ${res.status}`);
            }

            const body = (await res.json()) as { token?: string };
            if (!body.token) {
                throw new Error('New system login did not return token');
            }

            cachedNewSystemToken = body.token;
            return body.token;
        } catch (err) {
            if (attempt === 5) throw err;
            await new Promise((r) => setTimeout(r, 1_500 * (attempt + 1)));
        }
    }

    throw new Error('Unreachable');
}

export async function loginToNewSystem(page: Page): Promise<void> {
    const token = await getNewSystemToken();
    const { worlds, targetWorldName } = await ensurePlayableWorldForLobby(token);

    await page.addInitScript((t) => {
        window.localStorage.setItem('token', t);
    }, token);

    await page.goto(`${NEW_SYSTEM.baseUrl}/lobby`, {
        waitUntil: 'domcontentloaded',
    });

    const loadingText = page.getByText('로딩 중...').first();
    if (await loadingText.count()) {
        await loadingText.waitFor({ state: 'detached', timeout: 20_000 }).catch(() => {});
    }

    const selectWorldCard = async (): Promise<boolean> => {
        const allWorldCards = page.locator("[class*='cursor-pointer']");

        if (targetWorldName) {
            const targetCard = allWorldCards.filter({ hasText: targetWorldName }).filter({ hasText: '인원:' }).first();
            if (await targetCard.count()) {
                await targetCard.click();
                return true;
            }
        }

        for (const world of worlds) {
            const worldName = world.name?.trim();
            if (!worldName) continue;

            const targetCard = allWorldCards.filter({ hasText: worldName }).filter({ hasText: '인원:' }).first();
            if (await targetCard.count()) {
                await targetCard.click();
                return true;
            }
        }

        const fallbackCard = allWorldCards.filter({ hasText: '인원:' }).first();
        if (await fallbackCard.count()) {
            await fallbackCard.click();
            return true;
        }

        return false;
    };

    const selectedWorld = await selectWorldCard();
    if (!selectedWorld) {
        await page.waitForTimeout(2_000);
        await selectWorldCard();
    }

    const enterButton = page.getByRole('button', { name: '입장' }).first();
    const hasEnterButton = await enterButton
        .waitFor({ state: 'visible', timeout: 5_000 })
        .then(() => true)
        .catch(() => false);

    if (!hasEnterButton) {
        await page.goto(`${NEW_SYSTEM.baseUrl}/lobby/join`, {
            waitUntil: 'domcontentloaded',
        });

        if (/\/login/.test(page.url())) {
            throw new Error(`Token is not authenticated for lobby flow: currentUrl=${page.url()}`);
        }

        const needsWorldSelection = await page.getByText('월드를 선택해주세요.').count();
        if (needsWorldSelection) {
            await page.goto(`${NEW_SYSTEM.baseUrl}/lobby`, {
                waitUntil: 'domcontentloaded',
            });
            await selectWorldCard();
            await page.goto(`${NEW_SYSTEM.baseUrl}/lobby/join`, {
                waitUntil: 'domcontentloaded',
            });
        }

        if (await page.getByText('월드를 선택해주세요.').count()) {
            throw new Error('World selection did not persist to join flow.');
        }

        const nameInput = page.getByPlaceholder('장수 이름 입력');
        if (await nameInput.count()) {
            await nameInput.fill(`parity_${Date.now() % 100000}`);
        }

        const nationSelect = page.locator('select').nth(1);
        if (await nationSelect.count()) {
            await nationSelect.selectOption({ index: 1 }).catch(() => {});
        }

        const citySelect = page.locator('select').nth(2);
        if (await citySelect.count()) {
            await citySelect.selectOption({ index: 1 });
        }

        const balancedButton = page.getByRole('button', { name: '균형형' }).first();
        if (await balancedButton.count()) {
            await balancedButton.click();
        }

        const createButton = page.getByRole('button', { name: '장수 생성' }).first();
        await createButton.waitFor({ state: 'visible', timeout: 15_000 });
        await createButton.click();
        await page.waitForURL(/localhost(:(80|3000))?\/?$/, { timeout: 30_000 });
        return;
    }

    await enterButton.click();
    await page.waitForURL(/localhost(:(80|3000))?\/?$/, { timeout: 30_000 });
}

function authHeaders(token: string): Record<string, string> {
    return {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
    };
}

async function parseErrorBody(response: APIResponse): Promise<string> {
    try {
        return await response.text();
    } catch {
        return '<unreadable-body>';
    }
}

async function ensurePlayableWorldForLobby(token: string): Promise<{
    worlds: WorldListItem[];
    targetWorldName?: string;
}> {
    const worldsResponse = await fetch(`${NEW_SYSTEM.apiUrl}/worlds`, {
        headers: authHeaders(token),
        signal: AbortSignal.timeout(30_000),
    });
    if (!worldsResponse.ok) {
        return { worlds: [] };
    }

    const worlds = (await worldsResponse.json()) as WorldListItem[];
    let fallbackWorldName: string | undefined;

    for (const world of worlds) {
        const meResponse = await fetch(`${NEW_SYSTEM.apiUrl}/worlds/${world.id}/generals/me`, {
            headers: authHeaders(token),
            signal: AbortSignal.timeout(30_000),
        });
        if (!meResponse.ok) continue;

        const me = (await meResponse.json()) as GeneralSnapshot;
        const worldName = world.name?.trim() || String(world.id);
        if (!fallbackWorldName) {
            fallbackWorldName = worldName;
        }
        if (me.nationId > 0) {
            return {
                worlds,
                targetWorldName: worldName,
            };
        }
    }

    for (const world of worlds) {
        const meResponse = await fetch(`${NEW_SYSTEM.apiUrl}/worlds/${world.id}/generals/me`, {
            headers: authHeaders(token),
            signal: AbortSignal.timeout(30_000),
        });
        if (meResponse.ok) {
            continue;
        }

        const [citiesResponse, nationsResponse] = await Promise.all([
            fetch(`${NEW_SYSTEM.apiUrl}/worlds/${world.id}/cities`, {
                headers: authHeaders(token),
                signal: AbortSignal.timeout(30_000),
            }),
            fetch(`${NEW_SYSTEM.apiUrl}/worlds/${world.id}/nations`, {
                headers: authHeaders(token),
                signal: AbortSignal.timeout(30_000),
            }),
        ]);

        if (!citiesResponse.ok || !nationsResponse.ok) {
            continue;
        }

        const cities = (await citiesResponse.json()) as CityLite[];
        const nations = (await nationsResponse.json()) as NationLite[];
        const selectedNation = nations.find((nation) => nation.id > 0) ?? null;
        const cityId = selectedNation?.capitalCityId ?? cities[0]?.id ?? 0;
        if (cityId <= 0) {
            continue;
        }

        const createResponse = await fetch(`${NEW_SYSTEM.apiUrl}/worlds/${world.id}/generals`, {
            method: 'POST',
            headers: authHeaders(token),
            body: JSON.stringify({
                name: `parity_lobby_${world.id}_${Date.now() % 100000}`,
                cityId,
                nationId: selectedNation?.id ?? 0,
                leadership: 70,
                strength: 70,
                intel: 70,
                politics: 70,
                charm: 70,
                crewType: 0,
            }),
            signal: AbortSignal.timeout(30_000),
        });

        if (createResponse.ok) {
            return {
                worlds,
                targetWorldName: world.name?.trim() || String(world.id),
            };
        }
    }

    return {
        worlds,
        targetWorldName: fallbackWorldName ?? worlds[0]?.name?.trim(),
    };
}

async function assertOk(response: APIResponse, label: string): Promise<void> {
    if (response.ok()) return;
    const body = await parseErrorBody(response);
    throw new Error(`${label} failed: HTTP ${response.status()} ${body}`);
}

export async function getJson<T>(request: APIRequestContext, endpoint: string, token: string): Promise<T> {
    const response = await request.get(`${NEW_SYSTEM.apiUrl}${endpoint}`, {
        headers: authHeaders(token),
        timeout: 60_000,
    });
    await assertOk(response, `GET ${endpoint}`);
    return (await response.json()) as T;
}

export async function postJson<T>(
    request: APIRequestContext,
    endpoint: string,
    token: string,
    data: unknown
): Promise<T> {
    const response = await request.post(`${NEW_SYSTEM.apiUrl}${endpoint}`, {
        headers: authHeaders(token),
        data,
        timeout: 60_000,
    });
    await assertOk(response, `POST ${endpoint}`);
    return (await response.json()) as T;
}

export async function tryResolveWorldGeneralMe(
    request: APIRequestContext,
    token: string,
    options: ResolveContextOptions = {}
): Promise<{ worldId: number; me: GeneralSnapshot } | null> {
    const { requireNationOfficer = false, requireNationMember = false } = options;
    const worlds = await getJson<WorldListItem[]>(request, '/worlds', token);

    const isUsable = (me: GeneralSnapshot) => {
        if (requireNationOfficer) {
            return me.nationId > 0 && me.officerLevel >= 5;
        }
        if (requireNationMember) {
            return me.nationId > 0;
        }
        return true;
    };

    for (const world of worlds) {
        const response = await request.get(`${NEW_SYSTEM.apiUrl}/worlds/${world.id}/generals/me`, {
            headers: authHeaders(token),
            timeout: 30_000,
        });
        if (response.ok()) {
            const me = (await response.json()) as GeneralSnapshot;
            if (isUsable(me)) {
                return { worldId: world.id, me };
            }
        }
    }

    const tryGetMe = async (worldId: number): Promise<GeneralSnapshot | null> => {
        const meResponse = await request.get(`${NEW_SYSTEM.apiUrl}/worlds/${worldId}/generals/me`, {
            headers: authHeaders(token),
            timeout: 30_000,
        });
        if (!meResponse.ok()) return null;
        const me = (await meResponse.json()) as GeneralSnapshot;
        return isUsable(me) ? me : null;
    };

    for (const world of worlds) {
        const availableNpcResponse = await request.get(`${NEW_SYSTEM.apiUrl}/worlds/${world.id}/available-npcs`, {
            headers: authHeaders(token),
            timeout: 30_000,
        });

        if (availableNpcResponse.ok()) {
            const availableNpcs = (await availableNpcResponse.json()) as GeneralSnapshot[];
            const preferredNpc =
                availableNpcs.find((npc) => npc.nationId > 0 && npc.officerLevel >= 5) ??
                (requireNationOfficer ? null : (availableNpcs[0] ?? null));

            if (preferredNpc) {
                const selectNpcResponse = await request.post(`${NEW_SYSTEM.apiUrl}/worlds/${world.id}/select-npc`, {
                    headers: authHeaders(token),
                    data: { generalId: preferredNpc.id },
                    timeout: 30_000,
                });

                if (selectNpcResponse.ok()) {
                    const me = await tryGetMe(world.id);
                    if (me) {
                        return { worldId: world.id, me };
                    }
                }
            }
        }

        if (!requireNationOfficer && !requireNationMember) {
            const poolResponse = await request.get(`${NEW_SYSTEM.apiUrl}/worlds/${world.id}/pool`, {
                headers: authHeaders(token),
                timeout: 30_000,
            });

            if (poolResponse.ok()) {
                const pool = (await poolResponse.json()) as GeneralSnapshot[];
                const pick = pool[0];
                if (pick) {
                    const selectPoolResponse = await request.post(
                        `${NEW_SYSTEM.apiUrl}/worlds/${world.id}/select-pool`,
                        {
                            headers: authHeaders(token),
                            data: { generalId: pick.id },
                            timeout: 30_000,
                        }
                    );

                    if (selectPoolResponse.ok()) {
                        const me = await tryGetMe(world.id);
                        if (me) {
                            return { worldId: world.id, me };
                        }
                    }
                }
            }
        }
    }

    if (requireNationOfficer) {
        return null;
    }

    for (const world of worlds) {
        const citiesResponse = await request.get(`${NEW_SYSTEM.apiUrl}/worlds/${world.id}/cities`, {
            headers: authHeaders(token),
            timeout: 30_000,
        });
        const nationsResponse = await request.get(`${NEW_SYSTEM.apiUrl}/worlds/${world.id}/nations`, {
            headers: authHeaders(token),
            timeout: 30_000,
        });

        if (!citiesResponse.ok() || !nationsResponse.ok()) {
            continue;
        }

        const cities = (await citiesResponse.json()) as CityLite[];
        const nations = (await nationsResponse.json()) as NationLite[];

        const selectedNation = nations.find((n) => (n.level ?? 0) > 0) ?? nations.find((n) => n.id > 0) ?? null;
        const cityIds = new Set(cities.map((c) => c.id));
        const cityId =
            (selectedNation?.capitalCityId != null && cityIds.has(selectedNation.capitalCityId)
                ? selectedNation.capitalCityId
                : cities[0]?.id) ?? 0;

        if (cityId <= 0) {
            continue;
        }

        const createResponse = await request.post(`${NEW_SYSTEM.apiUrl}/worlds/${world.id}/generals`, {
            headers: authHeaders(token),
            data: {
                name: `parity_${world.id}_${Date.now() % 100000}`,
                cityId,
                nationId: selectedNation?.id ?? 0,
                leadership: 70,
                strength: 70,
                intel: 70,
                politics: 70,
                charm: 70,
                crewType: 0,
            },
            timeout: 30_000,
        });

        if (createResponse.ok()) {
            const me = await tryGetMe(world.id);
            if (me) {
                return { worldId: world.id, me };
            }
        }
    }

    return null;
}

export async function getGameContext(
    request: APIRequestContext,
    options: ResolveContextOptions = {}
): Promise<GameContext> {
    const { requireNationOfficer = false, requireNationMember = false } = options;
    const token = await getNewSystemToken(request);

    const generalMeResponse = await request.get(`${NEW_SYSTEM.apiUrl}/generals/me`, {
        headers: authHeaders(token),
        timeout: 30_000,
    });

    if (generalMeResponse.ok()) {
        const me = (await generalMeResponse.json()) as GeneralSnapshot;
        const meMatches = requireNationOfficer
            ? me.nationId > 0 && me.officerLevel >= 5
            : requireNationMember
              ? me.nationId > 0
              : true;

        if (meMatches) {
            const world = await getJson<{ realtimeMode: boolean }>(request, `/worlds/${me.worldId}`, token);
            return {
                worldId: me.worldId,
                generalId: me.id,
                cityId: me.cityId,
                nationId: me.nationId,
                officerLevel: me.officerLevel,
                realtimeMode: world.realtimeMode,
            };
        }
    }

    const resolved = await tryResolveWorldGeneralMe(request, token, {
        requireNationOfficer,
        requireNationMember,
    });
    if (!resolved) {
        throw new Error(
            requireNationOfficer
                ? 'Could not resolve nation-officer general context from API (officerLevel>=5, nationId>0)'
                : 'Could not resolve my general context from API'
        );
    }

    const world = await getJson<{ realtimeMode: boolean }>(request, `/worlds/${resolved.worldId}`, token);
    return {
        worldId: resolved.worldId,
        generalId: resolved.me.id,
        cityId: resolved.me.cityId,
        nationId: resolved.me.nationId,
        officerLevel: resolved.me.officerLevel,
        realtimeMode: world.realtimeMode,
    };
}

export async function getGeneralSnapshot(
    request: APIRequestContext,
    token: string,
    generalId: number
): Promise<GeneralSnapshot> {
    return getJson<GeneralSnapshot>(request, `/generals/${generalId}`, token);
}

export async function getCitySnapshot(
    request: APIRequestContext,
    token: string,
    cityId: number
): Promise<CitySnapshot> {
    return getJson<CitySnapshot>(request, `/cities/${cityId}`, token);
}

export async function getNationSnapshot(
    request: APIRequestContext,
    token: string,
    nationId: number
): Promise<NationSnapshot> {
    return getJson<NationSnapshot>(request, `/nations/${nationId}`, token);
}

export async function getGeneralCommandTable(
    request: APIRequestContext,
    token: string,
    generalId: number
): Promise<Record<string, CommandTableEntry[]>> {
    return getJson<Record<string, CommandTableEntry[]>>(request, `/generals/${generalId}/command-table`, token);
}

export async function getNationCommandTable(
    request: APIRequestContext,
    token: string,
    generalId: number
): Promise<Record<string, CommandTableEntry[]>> {
    return getJson<Record<string, CommandTableEntry[]>>(request, `/generals/${generalId}/nation-command-table`, token);
}

export function flattenCommandTable(table: Record<string, CommandTableEntry[]>): CommandTableEntry[] {
    return Object.values(table).flat();
}

export async function reserveGeneralCommand(
    request: APIRequestContext,
    token: string,
    generalId: number,
    actionCode: string
): Promise<ApiCallResult<GeneralTurnEntry[]>> {
    const response = await request.post(`${NEW_SYSTEM.apiUrl}/generals/${generalId}/turns`, {
        headers: authHeaders(token),
        data: { turns: [{ turnIdx: 0, actionCode }] },
        timeout: 60_000,
    });
    if (response.ok()) {
        return {
            ok: true,
            status: response.status(),
            data: (await response.json()) as GeneralTurnEntry[],
        };
    }
    return {
        ok: false,
        status: response.status(),
        errorText: await parseErrorBody(response),
    };
}

export async function listGeneralTurns(
    request: APIRequestContext,
    token: string,
    generalId: number
): Promise<GeneralTurnEntry[]> {
    return getJson<GeneralTurnEntry[]>(request, `/generals/${generalId}/turns`, token);
}

export async function reserveNationCommand(
    request: APIRequestContext,
    token: string,
    nationId: number,
    generalId: number,
    actionCode: string
): Promise<ApiCallResult<GeneralTurnEntry[]>> {
    const response = await request.post(`${NEW_SYSTEM.apiUrl}/nations/${nationId}/turns?generalId=${generalId}`, {
        headers: authHeaders(token),
        data: { turns: [{ turnIdx: 0, actionCode }] },
        timeout: 60_000,
    });
    if (response.ok()) {
        return {
            ok: true,
            status: response.status(),
            data: (await response.json()) as GeneralTurnEntry[],
        };
    }
    return {
        ok: false,
        status: response.status(),
        errorText: await parseErrorBody(response),
    };
}

export async function listNationTurns(
    request: APIRequestContext,
    token: string,
    nationId: number,
    officerLevel: number
): Promise<GeneralTurnEntry[]> {
    return getJson<GeneralTurnEntry[]>(request, `/nations/${nationId}/turns?officerLevel=${officerLevel}`, token);
}

export async function executeGeneralCommand(
    request: APIRequestContext,
    token: string,
    generalId: number,
    actionCode: string
): Promise<ApiCallResult<{ success: boolean; logs: string[] }>> {
    const response = await request.post(`${NEW_SYSTEM.apiUrl}/generals/${generalId}/execute`, {
        headers: authHeaders(token),
        data: { actionCode },
        timeout: 60_000,
    });
    if (response.ok()) {
        return {
            ok: true,
            status: response.status(),
            data: (await response.json()) as { success: boolean; logs: string[] },
        };
    }
    return {
        ok: false,
        status: response.status(),
        errorText: await parseErrorBody(response),
    };
}

export async function executeNationCommand(
    request: APIRequestContext,
    token: string,
    generalId: number,
    actionCode: string
): Promise<ApiCallResult<{ success: boolean; logs: string[] }>> {
    const response = await request.post(`${NEW_SYSTEM.apiUrl}/generals/${generalId}/execute-nation`, {
        headers: authHeaders(token),
        data: { actionCode },
        timeout: 60_000,
    });
    if (response.ok()) {
        return {
            ok: true,
            status: response.status(),
            data: (await response.json()) as { success: boolean; logs: string[] },
        };
    }
    return {
        ok: false,
        status: response.status(),
        errorText: await parseErrorBody(response),
    };
}

export function hasGeneralEffectDelta(before: GeneralSnapshot, after: GeneralSnapshot): boolean {
    return GENERAL_EFFECT_FIELDS.some((field) => before[field] !== after[field]);
}

export function hasCityEffectDelta(before: CitySnapshot, after: CitySnapshot): boolean {
    return CITY_EFFECT_FIELDS.some((field) => before[field] !== after[field]);
}

export function hasNationEffectDelta(before: NationSnapshot, after: NationSnapshot): boolean {
    return NATION_EFFECT_FIELDS.some((field) => before[field] !== after[field]);
}

async function callTurnEndpoint(request: APIRequestContext, url: string) {
    try {
        const response = await request.post(url);
        return response.ok();
    } catch {
        return false;
    }
}

export async function pauseTurnDaemon(request: APIRequestContext) {
    const newOk = await callTurnEndpoint(request, `${NEW_SYSTEM.gameAppUrl}/internal/turn/pause`);
    return { newOk, legacyOk: false };
}

export async function resumeTurnDaemon(request: APIRequestContext) {
    const newOk = await callTurnEndpoint(request, `${NEW_SYSTEM.gameAppUrl}/internal/turn/resume`);
    return { newOk, legacyOk: false };
}

export async function ensureReportDir(): Promise<string> {
    const dir = path.resolve(process.cwd(), REPORT_DIR);
    await mkdir(dir, { recursive: true });
    return dir;
}

export async function writeParityReport(report: ParityReport): Promise<string> {
    const reportName = `parity-report-${report.section}.json`;
    const contents = `${JSON.stringify(report, null, 2)}\n`;

    try {
        const dir = await ensureReportDir();
        const filePath = path.join(dir, reportName);
        await writeFile(filePath, contents, 'utf8');
        return filePath;
    } catch (error) {
        const maybeErr = error as NodeJS.ErrnoException;
        if (maybeErr?.code !== 'ENOSPC') {
            throw error;
        }

        const fallbackDir = '/tmp/opensamguk-parity-screenshots';
        await mkdir(fallbackDir, { recursive: true });
        const fallbackPath = path.join(fallbackDir, reportName);
        await writeFile(fallbackPath, contents, 'utf8');
        return fallbackPath;
    }
}

export function createReport(
    section: string,
    results: ParityCheckResult[],
    screenshots: { legacy: string; new: string }
): ParityReport {
    return {
        section,
        timestamp: new Date().toISOString(),
        results,
        screenshots,
    };
}

export function uniqueStrings(values: string[]) {
    return [...new Set(values.map((v) => normalizeText(v)).filter(Boolean))];
}

export async function safeGoto(page: Page, url: string): Promise<boolean> {
    try {
        await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 30_000 });
        return true;
    } catch {
        return false;
    }
}

export async function pageLooksHealthy(page: Page): Promise<boolean> {
    const body = (await extractTextContent(page, 'body')) ?? '';
    if (!body) return false;
    const lowered = body.toLowerCase();
    const badSignals = ['404', 'not found', 'exception', 'internal server error'];
    return !badSignals.some((signal) => lowered.includes(signal));
}

export async function navigateInGame(page: Page, route: string): Promise<boolean> {
    if (route === '/') {
        const homeLink = page.locator(`a[href='${route}']`).first();
        if (await homeLink.count()) {
            await homeLink.click();
            await page.waitForURL(/\/$/, { timeout: 20_000 });
            return true;
        }
        return safeGoto(page, `${NEW_SYSTEM.baseUrl}/`);
    }

    const navLink = page.locator(`a[href='${route}']`).first();
    if (await navLink.count()) {
        await navLink.click();
        await page.waitForURL(new RegExp(`${route.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`), {
            timeout: 20_000,
        });
        return true;
    }

    return safeGoto(page, `${NEW_SYSTEM.baseUrl}${route}`);
}

export function includesAnyMarker(text: string, markers: readonly string[]): boolean {
    return markers.some((marker) => text.includes(marker));
}

export function assertParityMatches(section: string, results: ParityCheckResult[]): void {
    const mismatches = results.filter((result) => !result.match);
    if (mismatches.length === 0) {
        return;
    }

    const preview = mismatches
        .slice(0, 12)
        .map((result) => `${result.check} [legacy=${result.legacy}, new=${result.new}]`)
        .join('; ');

    throw new Error(`[${section}] parity mismatches: ${mismatches.length}/${results.length}. ${preview}`);
}
