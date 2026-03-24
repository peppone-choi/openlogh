---
name: 3D Battle Canvas Migration
description: 2D Konva BattleCanvas replaced with Three.js/R3F BattleCanvas3D for gin7-style 3D space combat visualization
type: project
---

3D battle canvas migration completed 2026-03-24. Replaced 2D Konva rendering with React Three Fiber (Three.js) for gin7-style 3D space fleet combat.

**Why:** gin7 original uses 3D RTS combat. The 2D Konva canvas did not match the visual fidelity expected for space fleet battles.

**How to apply:**

- New 3D components: `BattleCanvas3D.tsx`, `Unit3D.tsx`, `Effects3D.tsx`, `SpaceEnvironment.tsx`
- Original 2D `BattleCanvas.tsx` preserved as fallback (not deleted)
- `battle/page.tsx` dynamic import points to BattleCanvas3D
- To revert to 2D: change import back to `BattleCanvas` in page.tsx
- battleStore unchanged -- 3D components read same store data
- Dependencies added: `three`, `@react-three/fiber`, `@react-three/drei`, `@types/three`
- Camera: 45-degree top-down (gin7 style), OrbitControls for rotation/zoom
- Ship classes: distinct 3D geometries (box=battleship, cone=cruiser, octahedron=destroyer, etc.)
- Effects: beam lines, gun projectiles with arcs, sphere-burst explosions, translucent shield spheres
- Right-click disabled on OrbitControls to preserve attack/move commands
