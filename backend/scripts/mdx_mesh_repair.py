#!/usr/bin/env python3
"""Mesh repair post-processor for gin7 OBJ files.

Loads OBJ, removes degenerate/duplicate faces, fills holes,
fixes normals, and exports a cleaned OBJ.

Usage:
  python mdx_mesh_repair.py <obj_file_or_dir> [--out <dir>]
"""

import argparse
from pathlib import Path
import numpy as np
import trimesh


def repair_mesh(obj_path, out_path):
    """Load, repair, and save a single OBJ file."""
    scene = trimesh.load(str(obj_path), process=False)

    # Handle both single mesh and scene with multiple meshes
    if isinstance(scene, trimesh.Scene):
        meshes = [g for g in scene.geometry.values() if isinstance(g, trimesh.Trimesh)]
        if not meshes:
            print(f'  {obj_path.stem}: NO MESHES')
            return None
        mesh = trimesh.util.concatenate(meshes)
    else:
        mesh = scene

    v_before = len(mesh.vertices)
    f_before = len(mesh.faces)

    # Step 1: Remove degenerate faces
    mask = mesh.nondegenerate_faces()
    if mask is not None and not mask.all():
        mesh.update_faces(mask)

    # Step 2a: Merge exact-duplicate vertices
    mesh.merge_vertices(merge_norm=True, merge_tex=True)

    # Step 2b: Merge coincident vertices (tight tolerance, all vertices)
    from scipy.spatial import cKDTree
    from collections import defaultdict

    tol_tight = 1e-4
    tree = cKDTree(mesh.vertices)
    pairs = tree.query_pairs(r=tol_tight)
    if pairs:
        parent = list(range(len(mesh.vertices)))
        def find(x):
            while parent[x] != x:
                parent[x] = parent[parent[x]]
                x = parent[x]
            return x
        for a, b in pairs:
            ra, rb = find(a), find(b)
            if ra != rb:
                parent[rb] = ra
        remap = np.array([find(i) for i in range(len(mesh.vertices))])
        mesh.faces = remap[mesh.faces]

    mesh.update_faces(mesh.unique_faces())
    mask = mesh.nondegenerate_faces()
    if mask is not None and not mask.all():
        mesh.update_faces(mask)
    mesh.remove_unreferenced_vertices()

    # Step 2c: Boundary-only merge — close gaps between parts without
    # disturbing interior geometry. Only merge boundary vertex pairs.
    edge_face = defaultdict(list)
    for fi, face in enumerate(mesh.faces):
        for i in range(3):
            e = tuple(sorted([face[i], face[(i + 1) % 3]]))
            edge_face[e].append(fi)
    boundary_verts = set()
    for e, fs in edge_face.items():
        if len(fs) == 1:
            boundary_verts.add(e[0])
            boundary_verts.add(e[1])

    if boundary_verts:
        bv_list = sorted(boundary_verts)
        bv_pos = mesh.vertices[bv_list]
        bv_tree = cKDTree(bv_pos)
        tol_boundary = 0.05  # tight boundary merge — only near-coincident
        bv_pairs = bv_tree.query_pairs(r=tol_boundary)
        if bv_pairs:
            parent = list(range(len(mesh.vertices)))
            def find2(x):
                while parent[x] != x:
                    parent[x] = parent[parent[x]]
                    x = parent[x]
                return x
            for ai, bi in bv_pairs:
                a, b = bv_list[ai], bv_list[bi]
                ra, rb = find2(a), find2(b)
                if ra != rb:
                    parent[rb] = ra
            remap = np.array([find2(i) for i in range(len(mesh.vertices))])
            mesh.faces = remap[mesh.faces]

    # Step 3: Remove duplicate/degenerate faces
    mesh.update_faces(mesh.unique_faces())
    mask = mesh.nondegenerate_faces()
    if mask is not None and not mask.all():
        mesh.update_faces(mask)
    mesh.remove_unreferenced_vertices()

    # Step 4: Fix all normals consistently
    trimesh.repair.fix_inversion(mesh)
    trimesh.repair.fix_normals(mesh)

    # Step 5: Fill holes
    try:
        trimesh.repair.fill_holes(mesh)
    except Exception:
        pass
    trimesh.repair.fix_normals(mesh)

    v_after = len(mesh.vertices)
    f_after = len(mesh.faces)

    # Check watertight status
    wt = mesh.is_watertight

    # Export
    out_path.parent.mkdir(parents=True, exist_ok=True)
    mesh.export(str(out_path), file_type='obj')

    return {
        'name': obj_path.parent.name,
        'v_before': v_before, 'f_before': f_before,
        'v_after': v_after, 'f_after': f_after,
        'watertight': wt,
    }


def main():
    parser = argparse.ArgumentParser(description='Mesh Repair Post-Processor')
    parser.add_argument('input', type=Path)
    parser.add_argument('--out', type=Path, default=None)
    args = parser.parse_args()

    if args.input.is_file():
        objs = [args.input]
    else:
        objs = sorted(args.input.rglob('high.obj'))

    if not objs:
        print('No OBJ files found.')
        return

    out_dir = args.out or args.input.parent / 'repaired'

    results = []
    for obj in objs:
        ship_name = obj.parent.name
        if args.input.is_file():
            out_path = out_dir / obj.name
        else:
            out_path = out_dir / ship_name / 'high.obj'

        r = repair_mesh(obj, out_path)
        if r:
            results.append(r)
            delta_f = r['f_after'] - r['f_before']
            wt_str = 'WT' if r['watertight'] else '--'
            print(f'  {r["name"]:<25} {r["v_before"]:>6}v→{r["v_after"]:>6}v  '
                  f'{r["f_before"]:>7}f→{r["f_after"]:>7}f ({delta_f:+d})  [{wt_str}]')

    if results:
        total_fb = sum(r['f_before'] for r in results)
        total_fa = sum(r['f_after'] for r in results)
        wt_count = sum(1 for r in results if r['watertight'])
        print(f'\n=== {len(results)} files: {total_fb:,}f → {total_fa:,}f '
              f'({total_fa - total_fb:+,}), {wt_count} watertight ===')
        print(f'Output: {out_dir}')


if __name__ == '__main__':
    main()
