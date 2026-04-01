#!/usr/bin/env python3
"""Smooth normals across part boundaries for gin7 OBJ files.

Merges nearby vertices, recomputes area-weighted normals,
and applies light Laplacian smoothing to normals only.

Usage:
  python mdx_smooth_normals.py <obj_file_or_dir> [--out <dir>] [--iterations N]
"""

import argparse
from pathlib import Path
import numpy as np
import trimesh
from scipy.spatial import cKDTree


def smooth_mesh(obj_path, out_path, iterations=2):
    """Merge vertices, recompute normals, apply Laplacian normal smoothing."""
    scene = trimesh.load(str(obj_path), process=False)

    if isinstance(scene, trimesh.Scene):
        meshes = [g for g in scene.geometry.values() if isinstance(g, trimesh.Trimesh)]
        if not meshes:
            return None
        mesh = trimesh.util.concatenate(meshes)
    else:
        mesh = scene

    v_before = len(mesh.vertices)
    f_before = len(mesh.faces)

    # Step 1: Merge nearby vertices (connects parts at boundaries)
    bbox = mesh.bounding_box.extents
    tol = max(bbox) * 0.001  # 0.1% of model size
    mesh.merge_vertices()

    tree = cKDTree(mesh.vertices)
    pairs = tree.query_pairs(r=tol)
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

    # Remove degenerate/duplicate after merge
    mask = mesh.nondegenerate_faces()
    if mask is not None and not mask.all():
        mesh.update_faces(mask)
    mesh.update_faces(mesh.unique_faces())
    mesh.remove_unreferenced_vertices()

    # Step 2: Fix normals consistency
    trimesh.repair.fix_normals(mesh)

    # Step 3: Laplacian normal smoothing (positions untouched)
    # Build adjacency: for each vertex, collect neighbor vertex indices
    from collections import defaultdict
    adj = defaultdict(set)
    for f in mesh.faces:
        adj[f[0]].update([f[1], f[2]])
        adj[f[1]].update([f[0], f[2]])
        adj[f[2]].update([f[0], f[1]])

    normals = mesh.vertex_normals.copy()
    for _ in range(iterations):
        new_normals = np.zeros_like(normals)
        for vi in range(len(normals)):
            neighbors = adj.get(vi, set())
            if not neighbors:
                new_normals[vi] = normals[vi]
                continue
            # Average: 50% self + 50% neighbors
            neighbor_avg = np.mean(normals[list(neighbors)], axis=0)
            blended = 0.5 * normals[vi] + 0.5 * neighbor_avg
            norm = np.linalg.norm(blended)
            new_normals[vi] = blended / norm if norm > 1e-8 else normals[vi]
        normals = new_normals

    # Step 4: Write OBJ with smoothed normals
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, 'w') as f:
        f.write(f'# Smoothed: {obj_path.parent.name}\n')
        for v in mesh.vertices:
            f.write(f'v {v[0]:.6f} {v[1]:.6f} {v[2]:.6f}\n')
        for n in normals:
            f.write(f'vn {n[0]:.6f} {n[1]:.6f} {n[2]:.6f}\n')
        if mesh.visual and hasattr(mesh.visual, 'uv') and mesh.visual.uv is not None:
            for uv in mesh.visual.uv:
                f.write(f'vt {uv[0]:.6f} {uv[1]:.6f}\n')
            for face in mesh.faces:
                a, b, c = face[0]+1, face[1]+1, face[2]+1
                f.write(f'f {a}/{a}/{a} {b}/{b}/{b} {c}/{c}/{c}\n')
        else:
            for face in mesh.faces:
                a, b, c = face[0]+1, face[1]+1, face[2]+1
                f.write(f'f {a}//{a} {b}//{b} {c}//{c}\n')

    v_after = len(mesh.vertices)
    f_after = len(mesh.faces)
    return {
        'name': obj_path.parent.name,
        'v_before': v_before, 'f_before': f_before,
        'v_after': v_after, 'f_after': f_after,
    }


def main():
    parser = argparse.ArgumentParser(description='Normal Smoothing Post-Processor')
    parser.add_argument('input', type=Path)
    parser.add_argument('--out', type=Path, default=None)
    parser.add_argument('--iterations', type=int, default=2)
    args = parser.parse_args()

    if args.input.is_file():
        objs = [args.input]
    else:
        objs = sorted(args.input.rglob('high.obj'))

    if not objs:
        print('No OBJ files found.')
        return

    out_dir = args.out or args.input

    for obj in objs:
        ship_name = obj.parent.name
        if args.input.is_file():
            out_path = out_dir / obj.name if out_dir.is_dir() else out_dir
        else:
            out_path = out_dir / ship_name / 'high.obj'

        r = smooth_mesh(obj, out_path, args.iterations)
        if r:
            print(f'  {r["name"]:<25} {r["v_before"]:>6}v→{r["v_after"]:>6}v  '
                  f'{r["f_before"]:>7}f→{r["f_after"]:>7}f')

    print(f'\n=== {len(objs)} files processed ===')


if __name__ == '__main__':
    main()
