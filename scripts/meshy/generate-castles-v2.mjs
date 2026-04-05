#!/usr/bin/env node
// Meshy.ai 성곽/거점 v2 — 일관된 스타일로 재생성
// 공통 스타일 앵커: 동일 색상 팔레트, 동일 카메라 앵글, 동일 디테일 수준
// Usage: node scripts/meshy/generate-castles-v2.mjs

const API_KEY = 'msy_RFPRi7C0B9DvHlDYSk5RCeICIe8fzxvyOriX';
const API_BASE = 'https://api.meshy.ai/openapi/v2/text-to-3d';
const OUTPUT_DIR = 'scripts/meshy/output-v2';

// 공통 스타일 앵커 — 모든 프롬프트에 적용
const STYLE_ANCHOR = [
  'ancient Han dynasty China (200 AD) architectural style',
  'warm earthy color palette (brown wood, grey stone, dark red accents, green bronze)',
  'consistent level of detail',
  'single isolated building complex on flat ground',
  'viewed from above at 45 degree angle',
  'miniature diorama scale',
  'game asset, clean topology',
  'no background, no terrain',
].join(', ');

const MODELS = [
  // --- 특수 거점 (Lv1-4) — 작고 소박한 군사 시설 ---
  {
    name: 'spot_naval',
    prompt: `Small riverside military dock with 2 wooden piers, 1 small watchtower, thatched roof storage hut, wooden palisade on 3 sides, river-facing open side, ${STYLE_ANCHOR}`,
  },
  {
    name: 'spot_camp',
    prompt: `Small military encampment with 3 canvas tents arranged in triangle, central campfire, wooden palisade perimeter, 1 banner pole with triangular flag, weapon rack, ${STYLE_ANCHOR}`,
  },
  {
    name: 'spot_gate',
    prompt: `Narrow stone mountain pass gate, single archway gate between two short stone walls, 1 small guard tower with tiled roof on top, wooden gate doors, ${STYLE_ANCHOR}`,
  },
  {
    name: 'spot_tribal',
    prompt: `Small nomadic settlement with 3 round yurts arranged in circle, central totem pole, animal hide drying rack, low wooden stockade fence, ${STYLE_ANCHOR}`,
  },

  // --- 성곽 도시 (Lv5-8) — 점진적으로 커지는 성곽 ---
  {
    name: 'city_small',
    prompt: `Small walled town: low square stone walls (4 sides), 1 simple gate with tiled roof, 3-4 small buildings with dark tiled roofs inside, 1 corner watchtower, ${STYLE_ANCHOR}`,
  },
  {
    name: 'city_medium',
    prompt: `Medium fortified city: taller square stone walls with battlements, main gate with double-roof gatehouse, 2 corner watchtowers, 6-8 buildings inside with courtyards, 1 central government hall with larger roof, ${STYLE_ANCHOR}`,
  },
  {
    name: 'city_large',
    prompt: `Large city: high thick stone walls with battlements, ornate main gate with triple-arch, 4 corner watchtowers, 12+ buildings including 2-story pagoda tower in center, market district, inner wall separating palace area, ${STYLE_ANCHOR}`,
  },
  {
    name: 'city_grand',
    prompt: `Grand imperial capital: massive double-layer stone walls with moat, 4 elaborate gate towers, 4 tall corner watchtowers, central 3-tier pagoda castle keep (tallest structure), inner palace compound, many buildings, most impressive and largest structure in the set, ${STYLE_ANCHOR}`,
  },
];

const headers = {
  Authorization: `Bearer ${API_KEY}`,
  'Content-Type': 'application/json',
};

async function createTask(model) {
  const res = await fetch(API_BASE, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      mode: 'preview',
      prompt: model.prompt,
      ai_model: 'meshy-5',
      target_polycount: 50000,
      topology: 'triangle',
    }),
  });
  const data = await res.json();
  if (!res.ok) {
    console.error(`[${model.name}] Failed: ${JSON.stringify(data)}`);
    return null;
  }
  console.log(`[${model.name}] Created: ${data.result}`);
  return { ...model, taskId: data.result };
}

async function pollTask(taskId, label) {
  while (true) {
    const res = await fetch(`${API_BASE}/${taskId}`, { headers });
    const data = await res.json();
    if (data.status === 'SUCCEEDED') return data;
    if (data.status === 'FAILED') throw new Error(`${label} failed`);
    process.stdout.write(`\r  [${label}] ${data.status} ${data.progress}%   `);
    await new Promise((r) => setTimeout(r, 5000));
  }
}

async function downloadGlb(url, name) {
  const { mkdirSync, writeFileSync } = await import('node:fs');
  mkdirSync(OUTPUT_DIR, { recursive: true });
  const res = await fetch(url);
  const buf = Buffer.from(await res.arrayBuffer());
  const path = `${OUTPUT_DIR}/${name}.glb`;
  writeFileSync(path, buf);
  console.log(`  Saved: ${path} (${(buf.length / 1024 / 1024).toFixed(1)}MB)`);
}

async function refineTask(previewTaskId) {
  const res = await fetch(API_BASE, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      mode: 'refine',
      preview_task_id: previewTaskId,
      enable_pbr: true,
      target_formats: ['glb'],
    }),
  });
  const data = await res.json();
  if (!res.ok) return null;
  return data.result;
}

async function main() {
  console.log('=== Meshy.ai 성곽 v2 — 일관된 스타일 재생성 (8종) ===');
  console.log(`Style anchor: ${STYLE_ANCHOR.slice(0, 80)}...\n`);

  // Preview 제출
  console.log('--- Step 1: Preview ---');
  const tasks = [];
  for (const model of MODELS) {
    const task = await createTask(model);
    if (task) tasks.push(task);
    await new Promise((r) => setTimeout(r, 1000));
  }

  // Preview 완료 + Refine
  console.log('\n--- Step 2: Preview 완료 + Refine ---');
  const refineTasks = [];
  for (const task of tasks) {
    console.log(`\n[${task.name}] Waiting preview...`);
    await pollTask(task.taskId, task.name);
    console.log(`\n[${task.name}] Preview done`);
    const refineId = await refineTask(task.taskId);
    if (refineId) {
      console.log(`[${task.name}] Refine: ${refineId}`);
      refineTasks.push({ ...task, refineId });
    }
    await new Promise((r) => setTimeout(r, 500));
  }

  // Refine 완료 + 다운로드
  console.log('\n--- Step 3: Refine + 다운로드 ---');
  for (const task of refineTasks) {
    console.log(`\n[${task.name}] Waiting refine...`);
    const result = await pollTask(task.refineId, task.name);
    console.log(`\n[${task.name}] Done`);
    if (result.model_urls?.glb) {
      await downloadGlb(result.model_urls.glb, task.name);
    }
  }

  console.log('\n=== v2 완료! ===');
}

main().catch(console.error);
