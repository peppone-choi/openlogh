import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import type { APIRequestContext, APIResponse, Page } from "@playwright/test";
import { LEGACY, NEW_SYSTEM } from "./parity-config";

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

const REPORT_DIR = process.env.PARITY_REPORT_DIR ?? "../parity-screenshots";

const GENERAL_EFFECT_FIELDS: (keyof GeneralSnapshot)[] = [
  "gold",
  "rice",
  "crew",
  "train",
  "atmos",
  "commandPoints",
  "injury",
];
const CITY_EFFECT_FIELDS: (keyof CitySnapshot)[] = [
  "agri",
  "comm",
  "secu",
  "def",
  "wall",
  "pop",
  "trust",
  "trade",
];
const NATION_EFFECT_FIELDS: (keyof NationSnapshot)[] = [
  "gold",
  "rice",
  "tech",
  "power",
  "level",
  "bill",
  "rate",
];

function normalizeText(value: string): string {
  return value.replace(/\s+/g, " ").trim();
}

export function compareStructure(
  legacyData: Record<string, unknown> | string[] | null | undefined,
  newData: Record<string, unknown> | string[] | null | undefined,
): StructureCompareResult {
  const legacyKeys = Array.isArray(legacyData)
    ? legacyData
    : Object.keys(legacyData ?? {});
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

export async function extractTextContent(
  page: Page,
  selector: string,
): Promise<string | null> {
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
  await page.goto(LEGACY.baseUrl, { waitUntil: "domcontentloaded" });

  const idField = page.getByRole("textbox", { name: "계정명" });
  const passwordField = page.getByRole("textbox", { name: "비밀번호" });

  await idField.fill(LEGACY.credentials.username);
  await passwordField.fill(LEGACY.credentials.password);
  await passwordField.press("Enter");

  try {
    await page.waitForURL(/entrance\.php|sam\/che\//, { timeout: 10_000 });
  } catch {
    // allow fallback click below
  }

  const enterLink = page.getByRole("link", { name: "입장" }).first();
  if (await enterLink.count()) {
    await enterLink.click();
  }

  try {
    await page.goto(LEGACY.gameUrl, { waitUntil: "domcontentloaded" });
  } catch {
    await page.goto(`${LEGACY.baseUrl}/sam/che`, {
      waitUntil: "domcontentloaded",
    });
  }
}

let cachedNewSystemToken: string | null = null;

export async function getNewSystemToken(
  _request?: APIRequestContext,
): Promise<string> {
  if (cachedNewSystemToken) return cachedNewSystemToken;

  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const res = await fetch(`${NEW_SYSTEM.apiUrl}/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(NEW_SYSTEM.credentials),
        signal: AbortSignal.timeout(30_000),
      });

      if (!res.ok) {
        throw new Error(`New system login failed: HTTP ${res.status}`);
      }

      const body = (await res.json()) as { token?: string };
      if (!body.token) {
        throw new Error("New system login did not return token");
      }

      cachedNewSystemToken = body.token;
      return body.token;
    } catch (err) {
      if (attempt === 2) throw err;
      await new Promise((r) => setTimeout(r, 2_000));
    }
  }

  throw new Error("Unreachable");
}

export async function loginToNewSystem(page: Page): Promise<void> {
  const token = await getNewSystemToken();

  await page.addInitScript((t) => {
    window.localStorage.setItem("token", t);
  }, token);

  await page.goto(`${NEW_SYSTEM.baseUrl}/lobby`, {
    waitUntil: "domcontentloaded",
  });

  const worldCard = page
    .locator("[class*='cursor-pointer']")
    .filter({ hasText: /반동탁|시나리오.*역사모드|턴제.*인원/ })
    .first();
  await worldCard.waitFor({ state: "visible", timeout: 10_000 });
  await worldCard.click();

  const enterButton = page.getByRole("button", { name: "입장" }).first();
  await enterButton.waitFor({ state: "visible", timeout: 10_000 });
  await enterButton.click();
  await page.waitForURL(/localhost(:(80|3000))?\/?$/, { timeout: 30_000 });
}

function authHeaders(token: string): Record<string, string> {
  return {
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
  };
}

async function parseErrorBody(response: APIResponse): Promise<string> {
  try {
    return await response.text();
  } catch {
    return "<unreadable-body>";
  }
}

async function assertOk(response: APIResponse, label: string): Promise<void> {
  if (response.ok()) return;
  const body = await parseErrorBody(response);
  throw new Error(`${label} failed: HTTP ${response.status()} ${body}`);
}

export async function getJson<T>(
  request: APIRequestContext,
  endpoint: string,
  token: string,
): Promise<T> {
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
  data: unknown,
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
): Promise<{ worldId: number; me: GeneralSnapshot } | null> {
  const worlds = await getJson<Array<{ id: number }>>(
    request,
    "/worlds",
    token,
  );
  for (const world of worlds) {
    const response = await request.get(
      `${NEW_SYSTEM.apiUrl}/worlds/${world.id}/generals/me`,
      { headers: authHeaders(token), timeout: 30_000 },
    );
    if (response.ok()) {
      const me = (await response.json()) as GeneralSnapshot;
      return { worldId: world.id, me };
    }
  }
  return null;
}

export async function getGameContext(
  request: APIRequestContext,
): Promise<GameContext> {
  const token = await getNewSystemToken(request);

  const generalMeResponse = await request.get(
    `${NEW_SYSTEM.apiUrl}/generals/me`,
    {
      headers: authHeaders(token),
      timeout: 30_000,
    },
  );

  if (generalMeResponse.ok()) {
    const me = (await generalMeResponse.json()) as GeneralSnapshot;
    const world = await getJson<{ realtimeMode: boolean }>(
      request,
      `/worlds/${me.worldId}`,
      token,
    );
    return {
      worldId: me.worldId,
      generalId: me.id,
      cityId: me.cityId,
      nationId: me.nationId,
      officerLevel: me.officerLevel,
      realtimeMode: world.realtimeMode,
    };
  }

  const resolved = await tryResolveWorldGeneralMe(request, token);
  if (!resolved) {
    throw new Error("Could not resolve my general context from API");
  }

  const world = await getJson<{ realtimeMode: boolean }>(
    request,
    `/worlds/${resolved.worldId}`,
    token,
  );
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
  generalId: number,
): Promise<GeneralSnapshot> {
  return getJson<GeneralSnapshot>(request, `/generals/${generalId}`, token);
}

export async function getCitySnapshot(
  request: APIRequestContext,
  token: string,
  cityId: number,
): Promise<CitySnapshot> {
  return getJson<CitySnapshot>(request, `/cities/${cityId}`, token);
}

export async function getNationSnapshot(
  request: APIRequestContext,
  token: string,
  nationId: number,
): Promise<NationSnapshot> {
  return getJson<NationSnapshot>(request, `/nations/${nationId}`, token);
}

export async function getGeneralCommandTable(
  request: APIRequestContext,
  token: string,
  generalId: number,
): Promise<Record<string, CommandTableEntry[]>> {
  return getJson<Record<string, CommandTableEntry[]>>(
    request,
    `/generals/${generalId}/command-table`,
    token,
  );
}

export async function getNationCommandTable(
  request: APIRequestContext,
  token: string,
  generalId: number,
): Promise<Record<string, CommandTableEntry[]>> {
  return getJson<Record<string, CommandTableEntry[]>>(
    request,
    `/generals/${generalId}/nation-command-table`,
    token,
  );
}

export function flattenCommandTable(
  table: Record<string, CommandTableEntry[]>,
): CommandTableEntry[] {
  return Object.values(table).flat();
}

export async function reserveGeneralCommand(
  request: APIRequestContext,
  token: string,
  generalId: number,
  actionCode: string,
): Promise<ApiCallResult<GeneralTurnEntry[]>> {
  const response = await request.post(
    `${NEW_SYSTEM.apiUrl}/generals/${generalId}/turns`,
    {
      headers: authHeaders(token),
      data: { turns: [{ turnIdx: 0, actionCode }] },
      timeout: 60_000,
    },
  );
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
  generalId: number,
): Promise<GeneralTurnEntry[]> {
  return getJson<GeneralTurnEntry[]>(
    request,
    `/generals/${generalId}/turns`,
    token,
  );
}

export async function reserveNationCommand(
  request: APIRequestContext,
  token: string,
  nationId: number,
  generalId: number,
  actionCode: string,
): Promise<ApiCallResult<GeneralTurnEntry[]>> {
  const response = await request.post(
    `${NEW_SYSTEM.apiUrl}/nations/${nationId}/turns?generalId=${generalId}`,
    {
      headers: authHeaders(token),
      data: { turns: [{ turnIdx: 0, actionCode }] },
      timeout: 60_000,
    },
  );
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
  officerLevel: number,
): Promise<GeneralTurnEntry[]> {
  return getJson<GeneralTurnEntry[]>(
    request,
    `/nations/${nationId}/turns?officerLevel=${officerLevel}`,
    token,
  );
}

export async function executeGeneralCommand(
  request: APIRequestContext,
  token: string,
  generalId: number,
  actionCode: string,
): Promise<ApiCallResult<{ success: boolean; logs: string[] }>> {
  const response = await request.post(
    `${NEW_SYSTEM.apiUrl}/generals/${generalId}/execute`,
    { headers: authHeaders(token), data: { actionCode }, timeout: 60_000 },
  );
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
  actionCode: string,
): Promise<ApiCallResult<{ success: boolean; logs: string[] }>> {
  const response = await request.post(
    `${NEW_SYSTEM.apiUrl}/generals/${generalId}/execute-nation`,
    { headers: authHeaders(token), data: { actionCode }, timeout: 60_000 },
  );
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

export function hasGeneralEffectDelta(
  before: GeneralSnapshot,
  after: GeneralSnapshot,
): boolean {
  return GENERAL_EFFECT_FIELDS.some((field) => before[field] !== after[field]);
}

export function hasCityEffectDelta(
  before: CitySnapshot,
  after: CitySnapshot,
): boolean {
  return CITY_EFFECT_FIELDS.some((field) => before[field] !== after[field]);
}

export function hasNationEffectDelta(
  before: NationSnapshot,
  after: NationSnapshot,
): boolean {
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
  const newOk = await callTurnEndpoint(
    request,
    `${NEW_SYSTEM.gameAppUrl}/internal/turn/pause`,
  );
  return { newOk, legacyOk: false };
}

export async function resumeTurnDaemon(request: APIRequestContext) {
  const newOk = await callTurnEndpoint(
    request,
    `${NEW_SYSTEM.gameAppUrl}/internal/turn/resume`,
  );
  return { newOk, legacyOk: false };
}

export async function ensureReportDir(): Promise<string> {
  const dir = path.resolve(process.cwd(), REPORT_DIR);
  await mkdir(dir, { recursive: true });
  return dir;
}

export async function writeParityReport(report: ParityReport): Promise<string> {
  const dir = await ensureReportDir();
  const filePath = path.join(dir, `parity-report-${report.section}.json`);
  await writeFile(filePath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  return filePath;
}

export function createReport(
  section: string,
  results: ParityCheckResult[],
  screenshots: { legacy: string; new: string },
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
    await page.goto(url, { waitUntil: "domcontentloaded", timeout: 30_000 });
    return true;
  } catch {
    return false;
  }
}

export async function pageLooksHealthy(page: Page): Promise<boolean> {
  const body = (await extractTextContent(page, "body")) ?? "";
  if (!body) return false;
  const lowered = body.toLowerCase();
  const badSignals = ["404", "not found", "exception", "internal server error"];
  return !badSignals.some((signal) => lowered.includes(signal));
}

export async function navigateInGame(
  page: Page,
  route: string,
): Promise<boolean> {
  if (route === "/") {
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
    await page.waitForURL(
      new RegExp(`${route.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}$`),
      {
        timeout: 20_000,
      },
    );
    return true;
  }

  return safeGoto(page, `${NEW_SYSTEM.baseUrl}${route}`);
}

export function includesAnyMarker(
  text: string,
  markers: readonly string[],
): boolean {
  return markers.some((marker) => text.includes(marker));
}
