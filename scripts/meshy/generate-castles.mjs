#!/usr/bin/env node
// Meshy.ai API로 성곽/거점 GLB 모델 일괄 생성
// Usage: node scripts/meshy/generate-castles.mjs

const API_KEY = 'msy_RFPRi7C0B9DvHlDYSk5RCeICIe8fzxvyOriX';
const API_BASE = 'https://api.meshy.ai/openapi/v2/text-to-3d';
const OUTPUT_DIR = 'scripts/meshy/output';

const MODELS = [
  // 특수 거점 (Lv1-4)
  {
    name: 'spot_naval',
    prompt: 'Ancient Chinese Three Kingdoms era riverside naval base, wooden docks with moored war boats, simple wooden watchtower, boat repair area, wooden palisade walls, thatched roof barracks, no city walls, isometric game asset, realistic style, detailed',
  },
  {
    name: 'spot_camp',
    prompt: 'Ancient Chinese Three Kingdoms military encampment, rows of military tents, wooden palisade fence perimeter, campfire in center, weapon racks and horse hitching posts, command tent with battle banner, no city walls, isometric game asset, realistic style, detailed',
  },
  {
    name: 'spot_gate',
    prompt: 'Ancient Chinese fortified mountain pass gate, narrow stone gateway between rocky cliffs, single guard tower with tiled roof, heavy wooden gate doors, small garrison guardhouse, not a city, isometric game asset, realistic style, detailed',
  },
  {
    name: 'spot_tribal',
    prompt: 'Ancient nomadic barbarian tribal settlement, circular arrangement of yurts and animal hide tents, carved wooden totem pole in center, campfire pit with cooking area, wooden stockade perimeter, not a city, isometric game asset, realistic style, detailed',
  },
  // 성곽 도시 (Lv5-8)
  {
    name: 'city_small',
    prompt: 'Small ancient Chinese walled town, low stone walls with single wooden gate, few tiled roof buildings inside, small market area, guard post, Three Kingdoms era small fortress, isometric game asset, realistic style, detailed',
  },
  {
    name: 'city_medium',
    prompt: 'Medium ancient Chinese fortified city, stone walls with battlements, two corner watchtowers, tiled roof government building, residential houses, main gate with arch, Three Kingdoms era city, isometric game asset, realistic style, detailed',
  },
  {
    name: 'city_large',
    prompt: 'Large ancient Chinese city, high stone walls with four corner watchtowers, tall pagoda tower in center, multiple buildings with courtyards, busy market district, ornate main gate, Three Kingdoms era major city, isometric game asset, realistic style, detailed',
  },
  {
    name: 'city_grand',
    prompt: 'Grand ancient Chinese imperial capital city, massive high stone walls surrounded by water moat, ornate multi-tiered pagoda castle keep in center, four decorated gate towers, inner palace complex with courtyards, Three Kingdoms era capital, isometric game asset, realistic detailed',
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
    console.error(`[${model.name}] Failed to create: ${JSON.stringify(data)}`);
    return null;
  }
  console.log(`[${model.name}] Created task: ${data.result}`);
  return { ...model, taskId: data.result };
}

async function pollTask(taskId) {
  while (true) {
    const res = await fetch(`${API_BASE}/${taskId}`, { headers });
    const data = await res.json();
    const { status, progress } = data;

    if (status === 'SUCCEEDED') return data;
    if (status === 'FAILED') throw new Error(`Task ${taskId} failed`);

    process.stdout.write(`\r  status: ${status} progress: ${progress}%   `);
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
  return path;
}

async function refineTask(previewTaskId, model) {
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
  if (!res.ok) {
    console.error(`[${model.name}] Refine failed: ${JSON.stringify(data)}`);
    return null;
  }
  console.log(`[${model.name}] Refine task: ${data.result}`);
  return data.result;
}

async function main() {
  console.log('=== Meshy.ai 성곽/거점 모델 생성 (8종) ===\n');

  // Step 1: Preview 생성 (모두 한번에 제출)
  console.log('--- Step 1: Preview 생성 ---');
  const tasks = [];
  for (const model of MODELS) {
    const task = await createTask(model);
    if (task) tasks.push(task);
    await new Promise((r) => setTimeout(r, 1000)); // rate limit
  }

  // Step 2: Preview 완료 대기 + Refine 제출
  console.log('\n--- Step 2: Preview 완료 대기 + Refine ---');
  const refineTasks = [];
  for (const task of tasks) {
    console.log(`\n[${task.name}] Waiting for preview...`);
    const result = await pollTask(task.taskId);
    console.log(`\n[${task.name}] Preview done!`);

    const refineId = await refineTask(task.taskId, task);
    if (refineId) {
      refineTasks.push({ ...task, refineId });
    }
    await new Promise((r) => setTimeout(r, 1000));
  }

  // Step 3: Refine 완료 대기 + GLB 다운로드
  console.log('\n--- Step 3: Refine 완료 대기 + 다운로드 ---');
  for (const task of refineTasks) {
    console.log(`\n[${task.name}] Waiting for refine...`);
    const result = await pollTask(task.refineId);
    console.log(`\n[${task.name}] Refine done!`);

    if (result.model_urls?.glb) {
      await downloadGlb(result.model_urls.glb, task.name);
    } else {
      console.error(`[${task.name}] No GLB URL found`);
    }
  }

  console.log('\n=== 완료! ===');
  console.log(`Output: ${OUTPUT_DIR}/`);
}

main().catch(console.error);
