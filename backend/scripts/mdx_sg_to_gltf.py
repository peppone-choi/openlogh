#!/usr/bin/env python3
"""Convert MDX to glTF using SG parser's extract_mdx (per-part scene).

Imports the SG parser's extraction logic and outputs a glTF scene where
each MeshPart is a separate mesh node. Three.js renders each part with
z-buffer composition, matching gin7's multi-draw-call rendering.

Usage:
  python mdx_sg_to_gltf.py <mdx_file_or_dir> --tex <tex_dir> [--out <dir>]
"""

import argparse, sys
from pathlib import Path
import numpy as np
import trimesh
from PIL import Image

# Import SG parser
sys.path.insert(0, str(Path(__file__).parent))
from mdx_scene_graph_parser import extract_mdx


def parts_to_gltf(parts, name, tex_path, out_dir, do_mirror=True):
    """Convert MeshPart list to glTF scene with per-part meshes."""
    scene = trimesh.Scene()

    # Load texture
    material = None
    png_path = out_dir / 'texture.png'
    if tex_path and tex_path.exists():
        try:
            img = Image.open(str(tex_path))
            img.save(str(png_path), 'PNG')
            material = trimesh.visual.material.PBRMaterial(
                baseColorTexture=img,
                metallicFactor=0.3,
                roughnessFactor=0.6,
                doubleSided=True,
            )
        except Exception as e:
            print(f'    texture failed: {e}')

    def add_mesh(verts, norms, uvs, faces, node_name):
        if len(faces) == 0:
            return 0
        v = np.array(verts, dtype=np.float64)
        n = np.array(norms, dtype=np.float64)
        f = np.array(faces, dtype=np.int64)
        mesh = trimesh.Trimesh(vertices=v, faces=f, vertex_normals=n, process=False)
        if material and len(uvs) == len(verts):
            uv = np.array(uvs, dtype=np.float64)
            mesh.visual = trimesh.visual.TextureVisuals(uv=uv, material=material)
        scene.add_geometry(mesh, node_name=node_name)
        return len(f)

    total_v = 0
    total_f = 0

    # Find the combined pool (largest vertex array = contains all VBs)
    pool_sizes = {}
    for part in parts:
        vid = id(part.vertices)
        pool_sizes[vid] = len(part.vertices)
    combined_vid = max(pool_sizes, key=lambda vid: pool_sizes[vid])
    combined_part = next(p for p in parts if id(p.vertices) == combined_vid)

    # Use exact pool_base from SG parser (no heuristic matching needed)
    all_faces = []
    for part in parts:
        vid = id(part.vertices)
        if vid == combined_vid:
            base = 0  # already in combined pool coordinates
        else:
            base = part.pool_base  # exact offset from SG parser
        for f in part.faces:
            all_faces.append((f[0] + base, f[1] + base, f[2] + base))

    all_verts = combined_part.vertices
    all_norms = combined_part.normals
    all_uvs = combined_part.uvs

    # Original half — single mesh
    nf = add_mesh(all_verts, all_norms, all_uvs, all_faces, f'{name}_original')
    total_v += len(all_verts)
    total_f += nf

    # Mirror half — single mesh
    if do_mirror:
        m_verts = [(-v[0], v[1], v[2]) for v in all_verts]
        m_norms = [(-n[0], n[1], n[2]) for n in all_norms]
        m_faces = [(f[0], f[2], f[1]) for f in all_faces]
        nf = add_mesh(m_verts, m_norms, all_uvs, m_faces, f'{name}_mirror')
        total_v += len(m_verts)
        total_f += nf

    # Export
    glb_path = out_dir / f'{name}.glb'
    scene.export(str(glb_path), file_type='glb')
    return total_v, total_f


def main():
    parser = argparse.ArgumentParser(description='MDX → glTF via SG parser')
    parser.add_argument('input', type=Path)
    parser.add_argument('--tex', type=Path, required=True)
    parser.add_argument('--out', type=Path, default=Path('/tmp/mdx_gltf_out'))
    parser.add_argument('--no-mirror', action='store_true')
    args = parser.parse_args()

    files = [args.input] if args.input.is_file() else sorted(args.input.glob('*_h.mdx'))
    args.out.mkdir(parents=True, exist_ok=True)
    do_mirror = not args.no_mirror

    ok = 0
    total_v = total_f = 0
    for mdx in files:
        data = mdx.read_bytes()
        name = mdx.stem
        if name.endswith('_h'):
            name = name[:-2]

        parts = extract_mdx(data, name, verbose=False)
        if not parts:
            print(f'  {name}: FAIL')
            continue

        tex_path = args.tex / f'{name}_h.dds'
        out_dir = args.out / name
        out_dir.mkdir(parents=True, exist_ok=True)

        tv, tf = parts_to_gltf(parts, name, tex_path, out_dir, do_mirror)
        np_ = len(set(id(p.vertices) for p in parts))
        print(f'  {name}: {tv}v {tf}f ({np_} groups x2) → .glb')
        ok += 1
        total_v += tv
        total_f += tf

    print(f'\n=== {ok}/{len(files)} OK, {total_v:,}v {total_f:,}f ===')


if __name__ == '__main__':
    main()
