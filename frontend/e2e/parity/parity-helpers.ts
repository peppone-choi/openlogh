import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import type { APIRequestContext, Page } from "@playwright/test";
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

const REPORT_DIR = process.env.PARITY_REPORT_DIR ?? "../parity-screenshots";

function normalizeText(value: string): string {
  return value.replace(/\s+/g, " ").trim();
}

async function firstVisible(page: Page, selectors: string[]) {
  for (const selector of selectors) {
    const locator = page.locator(selector).first();
    if (await locator.count()) {
      return locator;
    }
  }
  return null;
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

  await page.goto(LEGACY.gameUrl, { waitUntil: "domcontentloaded" });
}

export async function loginToNewSystem(page: Page): Promise<void> {
  const response = await page.request.post(`${NEW_SYSTEM.apiUrl}/auth/login`, {
    data: NEW_SYSTEM.credentials,
  });

  if (!response.ok()) {
    throw new Error(`New system login failed: HTTP ${response.status()}`);
  }

  const body = (await response.json()) as { token?: string };
  if (!body.token) {
    throw new Error("New system login did not return token");
  }

  await page.addInitScript((token) => {
    window.localStorage.setItem("token", token);
  }, body.token);

  await page.goto(`${NEW_SYSTEM.baseUrl}/lobby`, {
    waitUntil: "domcontentloaded",
  });

  const worldMeta = page
    .locator("p.text-xs.text-muted-foreground")
    .filter({ hasText: /년\s*\d+월/ })
    .first();
  if (await worldMeta.count()) {
    await worldMeta.click();
  } else {
    const fallbackWorld = page
      .locator('[role="button"], .cursor-pointer')
      .filter({ hasText: /월드|서버/ })
      .first();
    if (await fallbackWorld.count()) {
      await fallbackWorld.click();
    }
  }

  const enterButton = page.getByRole("button", { name: "입장" }).first();
  if (!(await enterButton.count())) {
    throw new Error("New system lobby enter button not found");
  }
  await enterButton.click();
  await page.waitForURL(/localhost:(80|3000)\/?$/, { timeout: 20_000 });
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

async function callTurnEndpoint(
  request: APIRequestContext,
  method: "pause" | "resume",
  url: string,
) {
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
    "pause",
    `${NEW_SYSTEM.gameAppUrl}/internal/turn/pause`,
  );

  const legacyCandidates = [
    `${LEGACY.baseUrl}/sam/che/api.php?path=internal/turn/pause`,
    `${LEGACY.baseUrl}/api.php?path=internal/turn/pause`,
  ];

  let legacyOk = false;
  for (const url of legacyCandidates) {
    if (await callTurnEndpoint(request, "pause", url)) {
      legacyOk = true;
      break;
    }
  }

  return { newOk, legacyOk };
}

export async function resumeTurnDaemon(request: APIRequestContext) {
  const newOk = await callTurnEndpoint(
    request,
    "resume",
    `${NEW_SYSTEM.gameAppUrl}/internal/turn/resume`,
  );

  const legacyCandidates = [
    `${LEGACY.baseUrl}/sam/che/api.php?path=internal/turn/resume`,
    `${LEGACY.baseUrl}/api.php?path=internal/turn/resume`,
  ];

  let legacyOk = false;
  for (const url of legacyCandidates) {
    if (await callTurnEndpoint(request, "resume", url)) {
      legacyOk = true;
      break;
    }
  }

  return { newOk, legacyOk };
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
    await page.goto(url, { waitUntil: "domcontentloaded", timeout: 20_000 });
    return true;
  } catch {
    return false;
  }
}

export async function pageLooksHealthy(page: Page): Promise<boolean> {
  const body = (await extractTextContent(page, "body")) ?? "";
  if (!body) return false;
  const badSignals = ["404", "not found", "오류", "error", "exception"];
  const lowered = body.toLowerCase();
  return !badSignals.some((signal) => lowered.includes(signal));
}
