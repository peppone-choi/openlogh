#!/usr/bin/env node
// Meshy.ai API로 유닛(병종) GLB 모델 일괄 생성
// 고대 한나라/삼국지 시대 스타일
// Usage: node scripts/meshy/generate-units.mjs

const API_KEY = 'msy_RFPRi7C0B9DvHlDYSk5RCeICIe8fzxvyOriX';
const API_BASE = 'https://api.meshy.ai/openapi/v2/text-to-3d';
const OUTPUT_DIR = 'scripts/meshy/output';

const ERA_STYLE = 'ancient Han dynasty Three Kingdoms era China, historically accurate armor and weapons, realistic style, isometric game asset, detailed';

const MODELS = [
  // ===== 보병 계열 (1100) =====
  {
    name: 'unit_infantry',
    code: '1100',
    prompt: `Han dynasty infantry soldier standing at attention, iron lamellar armor over cloth robes, round wooden shield with red lacquer, bronze dagger-axe (ge) weapon, conical iron helmet, ${ERA_STYLE}`,
  },
  {
    name: 'unit_qingzhou',
    code: '1101',
    prompt: `Qingzhou elite infantry soldier (청주병), heavy iron scale armor, long halberd (ji) weapon, tall rectangular shield, distinctive blue cloth headband, veteran warrior, ${ERA_STYLE}`,
  },
  {
    name: 'unit_marine',
    code: '1102',
    prompt: `Han dynasty marine soldier (수병) for river warfare, light leather armor suitable for swimming, short sword and buckler shield, rope and grappling hook at belt, barefoot, ${ERA_STYLE}`,
  },
  {
    name: 'unit_assassin',
    code: '1103',
    prompt: `Han dynasty assassin warrior (자객병), dark cloth garments with minimal leather armor, dual wielding short swords, face mask covering lower face, agile pose, stealthy appearance, ${ERA_STYLE}`,
  },
  {
    name: 'unit_guard',
    code: '1104',
    prompt: `Imperial palace guard soldier (근위병), ornate gilded lamellar armor, ceremonial halberd with red tassel, tall helmet with plume, elite warrior appearance, ${ERA_STYLE}`,
  },
  {
    name: 'unit_rattan',
    code: '1105',
    prompt: `Southern barbarian rattan armor soldier (등갑병), woven rattan vine armor covering torso, tribal face paint, machete weapon, wooden shield with animal hide, jungle warrior, ${ERA_STYLE}`,
  },
  {
    name: 'unit_baekyi',
    code: '1106',
    prompt: `White-clad elite infantry (백이병), distinctive all-white cloth armor and robes, iron spear with white tassel, round shield, white headband, disciplined formation soldier, ${ERA_STYLE}`,
  },

  // ===== 궁병 계열 (1200) =====
  {
    name: 'unit_archer',
    code: '1200',
    prompt: `Han dynasty archer soldier, light leather armor, composite recurve bow drawn, quiver of arrows on back, conical leather cap, standing shooting pose, ${ERA_STYLE}`,
  },
  {
    name: 'unit_mounted_archer',
    code: '1201',
    prompt: `Han dynasty mounted archer (궁기병) on horseback, light cavalry armor, shooting composite bow while mounted on armored horse, quiver at hip, ${ERA_STYLE}`,
  },
  {
    name: 'unit_repeating_crossbow',
    code: '1202',
    prompt: `Han dynasty repeating crossbow soldier (연노병), medium armor, holding Zhuge Liang repeating crossbow (제갈노), bolt magazine on top, mechanical weapon, ${ERA_STYLE}`,
  },
  {
    name: 'unit_longbow',
    code: '1203',
    prompt: `Han dynasty strong bow archer (강궁병), muscular build, extra-large composite longbow, heavy draw, leather arm guards, elite marksman appearance, ${ERA_STYLE}`,
  },
  {
    name: 'unit_crossbow',
    code: '1204',
    prompt: `Han dynasty crossbow soldier (석궁병), medium iron armor, heavy bronze crossbow with stirrup for loading, bolt quiver at waist, kneeling shooting pose, ${ERA_STYLE}`,
  },

  // ===== 기병 계열 (1300) =====
  {
    name: 'unit_cavalry',
    code: '1300',
    prompt: `Han dynasty cavalry soldier on horseback, iron lamellar armor, long spear (槍) with red tassel, horse with leather saddle and bridle, mounted warrior, ${ERA_STYLE}`,
  },
  {
    name: 'unit_white_horse',
    code: '1301',
    prompt: `White Horse cavalry (백마병) of Gongsun Zan, warrior on white horse, light cavalry armor, long lance, white horse with flowing mane, elite light cavalry, ${ERA_STYLE}`,
  },
  {
    name: 'unit_heavy_cavalry',
    code: '1302',
    prompt: `Han dynasty heavy armored cavalry (중장기병), fully armored horse with iron barding, rider in heavy lamellar plate armor, long heavy lance, cataphract style, ${ERA_STYLE}`,
  },
  {
    name: 'unit_shock_cavalry',
    code: '1303',
    prompt: `Shock cavalry (돌격기병) in charging pose, medium armor, lowered lance for charge impact, horse at full gallop, aggressive forward-leaning posture, ${ERA_STYLE}`,
  },
  {
    name: 'unit_iron_cavalry',
    code: '1304',
    prompt: `Elite iron cavalry (철기병), full iron scale armor on both rider and horse, mace weapon, heavy war horse with iron face plate, unstoppable heavy unit, ${ERA_STYLE}`,
  },
  {
    name: 'unit_hunter_cavalry',
    code: '1305',
    prompt: `Nomadic hunter cavalry (수렵기병), light fur and leather armor, composite bow and lasso, fast steppe horse without heavy armor, frontier scout warrior, ${ERA_STYLE}`,
  },
  {
    name: 'unit_beast_cavalry',
    code: '1306',
    prompt: `Southern beast handler cavalry (맹수병), tribal warrior riding alongside trained war tiger, leather and bone armor, animal pelt cloak, spear weapon, exotic unit, ${ERA_STYLE}`,
  },
  {
    name: 'unit_tiger_cavalry',
    code: '1307',
    prompt: `Tiger and Leopard cavalry (호표기병) of Cao Cao, elite cavalry with tiger pelt cloaks, ornate armor with animal motifs, paired swords, fierce war horse, ${ERA_STYLE}`,
  },

  // ===== 귀병 계열 (1400) =====
  {
    name: 'unit_ghost',
    code: '1400',
    prompt: `Han dynasty ghost soldier (귀병), eerie warrior with pale demon mask, dark tattered robes over armor, dual wielding curved swords, supernatural fearsome appearance, ${ERA_STYLE}`,
  },
  {
    name: 'unit_divine_ghost',
    code: '1401',
    prompt: `Divine ghost warrior (신귀병), golden demon mask with horns, ornate dark armor with divine symbols, glowing ethereal weapon, supreme ghost unit, ${ERA_STYLE}`,
  },
  {
    name: 'unit_white_ghost',
    code: '1402',
    prompt: `White ghost warrior (백귀병), white demon mask, pale white robes and light armor, twin white-bladed swords, ghostly pale appearance, ${ERA_STYLE}`,
  },
  {
    name: 'unit_black_ghost',
    code: '1403',
    prompt: `Black ghost warrior (흑귀병), black demon mask, all-black heavy armor, massive black iron sword, dark menacing appearance, shadow warrior, ${ERA_STYLE}`,
  },
  {
    name: 'unit_evil_ghost',
    code: '1404',
    prompt: `Evil ghost warrior (악귀병), grotesque red demon mask with fangs, spiked black armor, serrated blade weapon, most fearsome ghost unit, ${ERA_STYLE}`,
  },
  {
    name: 'unit_south_ghost',
    code: '1405',
    prompt: `Southern ghost warrior (남귀병), tribal demon mask with feathers, jungle vine and leather armor, poisoned blade and blowpipe, southern barbarian ghost unit, ${ERA_STYLE}`,
  },
  {
    name: 'unit_yellow_ghost',
    code: '1406',
    prompt: `Yellow ghost warrior (황귀병), yellow demon mask, yellow turban and robes over armor, staff weapon with talisman, Yellow Turban rebellion ghost unit, ${ERA_STYLE}`,
  },
  {
    name: 'unit_heaven_ghost',
    code: '1407',
    prompt: `Heavenly ghost warrior (천귀병), celestial blue demon mask, cloud-patterned robes over celestial armor, crescent blade weapon, divine ghost unit, ${ERA_STYLE}`,
  },
  {
    name: 'unit_demon_ghost',
    code: '1408',
    prompt: `Demon ghost warrior (마귀병), horned demon skull mask, bone and iron armor with chains, flame-etched great axe weapon, most terrifying ghost unit, ${ERA_STYLE}`,
  },

  // ===== 공성 무기 계열 (1500) =====
  {
    name: 'unit_ballista',
    code: '1500',
    prompt: `Han dynasty ballista siege weapon (정란), large wooden frame crossbow on wheeled cart, bronze fittings, operated by two soldiers, fires heavy bolts, ${ERA_STYLE}`,
  },
  {
    name: 'unit_battering_ram',
    code: '1501',
    prompt: `Han dynasty battering ram (충차), heavy wooden siege ram on wheeled frame with roof cover, iron-tipped log suspended by chains, pushed by soldiers, ${ERA_STYLE}`,
  },
  {
    name: 'unit_catapult',
    code: '1502',
    prompt: `Han dynasty trebuchet catapult (벽력거), large wooden counterweight siege engine, sling arm with stone projectile, wheeled base, thunder crash catapult, ${ERA_STYLE}`,
  },
  {
    name: 'unit_wooden_ox',
    code: '1503',
    prompt: `Zhuge Liang wooden ox transport (목우유마), mechanical wooden ox-shaped supply cart, ingenious gear mechanism, carrying grain sacks, famous invention, ${ERA_STYLE}`,
  },

  // ===== 특수 병종 =====
  {
    name: 'unit_shaman',
    code: '8',
    prompt: `Han dynasty Taoist shaman warrior (무당병), flowing dark robes with yin-yang symbols, wooden ritual staff with paper talismans, incense burner at belt, mystical appearance, ${ERA_STYLE}`,
  },
  {
    name: 'unit_rattan_base',
    code: '10',
    prompt: `Southern Nanman rattan armor soldier (등갑병), tightly woven rattan vine full body armor, tribal war paint on face, curved machete and rattan shield, ${ERA_STYLE}`,
  },
  {
    name: 'unit_navy',
    code: '11',
    prompt: `Han dynasty small river warship (수군 전선), wooden war junk with single sail, dragon head prow, battle flags, crossbow mounts on deck, oar ports, ${ERA_STYLE}`,
  },

  // ===== 공통 =====
  {
    name: 'unit_flag',
    code: 'flag',
    prompt: `Ancient Chinese military battle banner on tall wooden pole, triangular silk flag with flowing tassels, bronze finial on top, Three Kingdoms era army standard, ${ERA_STYLE}`,
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
      target_polycount: 30000,
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

// 배치 처리: 동시 N개씩
async function processBatch(models, concurrency = 4) {
  const results = [];
  for (let i = 0; i < models.length; i += concurrency) {
    const batch = models.slice(i, i + concurrency);
    console.log(`\n--- Batch ${Math.floor(i / concurrency) + 1}/${Math.ceil(models.length / concurrency)} (${batch.map(m => m.name).join(', ')}) ---`);

    // 제출
    const tasks = [];
    for (const model of batch) {
      const task = await createTask(model);
      if (task) tasks.push(task);
      await new Promise((r) => setTimeout(r, 500));
    }

    // Preview 대기
    for (const task of tasks) {
      console.log(`\n[${task.name}] Waiting preview...`);
      await pollTask(task.taskId, task.name);
      console.log(`\n[${task.name}] Preview done`);

      // Refine 제출
      const refineId = await refineTask(task.taskId);
      if (refineId) {
        console.log(`[${task.name}] Refine: ${refineId}`);
        task.refineId = refineId;
      }
    }

    // Refine 대기 + 다운로드
    for (const task of tasks) {
      if (!task.refineId) continue;
      console.log(`\n[${task.name}] Waiting refine...`);
      const result = await pollTask(task.refineId, task.name);
      console.log(`\n[${task.name}] Refine done`);
      if (result.model_urls?.glb) {
        await downloadGlb(result.model_urls.glb, task.name);
      }
    }

    results.push(...tasks);
  }
  return results;
}

async function main() {
  console.log(`=== Meshy.ai 유닛 모델 생성 (${MODELS.length}종) ===`);
  console.log(`고대 한나라/삼국지 스타일\n`);

  await processBatch(MODELS, 4);

  console.log(`\n=== 완료! ${MODELS.length}종 GLB → ${OUTPUT_DIR}/ ===`);
}

main().catch(console.error);
