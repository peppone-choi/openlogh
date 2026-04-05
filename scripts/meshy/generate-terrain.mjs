#!/usr/bin/env node
// Meshy.ai 지형 데코레이션 GLB 생성 (5종)
// Usage: node scripts/meshy/generate-terrain.mjs

const API_KEY = 'msy_RFPRi7C0B9DvHlDYSk5RCeICIe8fzxvyOriX';
const API_BASE = 'https://api.meshy.ai/openapi/v2/text-to-3d';
const OUTPUT_DIR = 'scripts/meshy/output-terrain';

const STYLE = 'ancient China landscape, warm earthy tones, miniature diorama scale, game environment asset, clean topology, no background';

const MODELS = [
  {
    name: 'tree_pine',
    prompt: `Single tall pine tree, dark green conifer needles, brown rough bark trunk, slight curve, ${STYLE}`,
  },
  {
    name: 'tree_broad',
    prompt: `Single broadleaf deciduous tree, lush green rounded canopy, thick brown trunk with visible roots, ${STYLE}`,
  },
  {
    name: 'rock_cluster',
    prompt: `Cluster of 3-4 natural grey boulders with moss patches, rounded weathered stones grouped together, ${STYLE}`,
  },
  {
    name: 'grass_patch',
    prompt: `Small patch of tall wild grass and wildflowers, ground vegetation cluster, green and yellow tones, ${STYLE}`,
  },
  {
    name: 'reed_water',
    prompt: `Cluster of river reeds and cattails growing from shallow water, tall thin green stems with brown seed heads, ${STYLE}`,
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
      target_polycount: 15000,
      topology: 'triangle',
    }),
  });
  const data = await res.json();
  if (!res.ok) { console.error(`[${model.name}] Failed: ${JSON.stringify(data)}`); return null; }
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
    await new Promise(r => setTimeout(r, 5000));
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
    body: JSON.stringify({ mode: 'refine', preview_task_id: previewTaskId, enable_pbr: true, target_formats: ['glb'] }),
  });
  const data = await res.json();
  if (!res.ok) return null;
  return data.result;
}

async function main() {
  console.log(`=== Meshy.ai 지형 데코레이션 (${MODELS.length}종) ===\n`);

  const tasks = [];
  for (const m of MODELS) {
    const t = await createTask(m);
    if (t) tasks.push(t);
    await new Promise(r => setTimeout(r, 1000));
  }

  const refineTasks = [];
  for (const t of tasks) {
    console.log(`\n[${t.name}] Waiting preview...`);
    await pollTask(t.taskId, t.name);
    console.log(`\n[${t.name}] Preview done`);
    const rid = await refineTask(t.taskId);
    if (rid) { console.log(`[${t.name}] Refine: ${rid}`); refineTasks.push({ ...t, refineId: rid }); }
    await new Promise(r => setTimeout(r, 500));
  }

  for (const t of refineTasks) {
    console.log(`\n[${t.name}] Waiting refine...`);
    const result = await pollTask(t.refineId, t.name);
    console.log(`\n[${t.name}] Done`);
    if (result.model_urls?.glb) await downloadGlb(result.model_urls.glb, t.name);
  }

  console.log('\n=== 완료! ===');
}

main().catch(console.error);
