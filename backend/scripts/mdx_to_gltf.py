#!/usr/bin/env python3
"""Convert MDX ships to glTF with per-part meshes (game-like rendering).

Each VB/IB pair becomes a separate mesh node in the glTF scene.
Three.js renders each part independently with z-buffer composition,
matching gin7's multi-draw-call rendering and hiding part boundaries.

Usage:
  python mdx_to_gltf.py <mdx_file_or_dir> --tex <tex_dir> [--out <dir>]
"""

import argparse, os, struct, math
from pathlib import Path
import numpy as np
import trimesh
from PIL import Image


def f32(d, o): return struct.unpack_from('<f', d, o)[0]
def u16(d, o): return struct.unpack_from('<H', d, o)[0]
def u32(d, o): return struct.unpack_from('<I', d, o)[0]

W_ONE = struct.pack('<f', 1.0)
MARKER_STRIDE = {74002: 72, 336402: 84}
STRIDE_FMT = {
    72: {'p': 0, 'n': 12, 'uv': 24, 'w': 32},
    84: {'p': 52, 'n': 40, 'uv': 68, 'w': 0},
}


def find_w_runs(data, stride):
    """Find VB regions by W=1.0 scanning."""
    fmt = STRIDE_FMT[stride]
    w_off, p_off = fmt['w'], fmt['p']
    runs = []
    pos = 0
    while pos < len(data) - stride:
        w_pos = data.find(W_ONE, pos)
        if w_pos == -1:
            break
        vb = w_pos - w_off
        if vb < 0 or vb + p_off + 12 > len(data):
            pos = w_pos + 1
            continue
        px = f32(data, vb + p_off)
        if math.isnan(px) or abs(px) > 300:
            pos = w_pos + 1
            continue
        vc = 0
        test = vb
        while test + stride <= len(data):
            w = f32(data, test + w_off)
            if abs(w - 1.0) > 0.05:
                break
            tpx = f32(data, test + p_off)
            if math.isnan(tpx) or abs(tpx) > 300:
                break
            vc += 1
            test += stride
        if vc >= 10:
            runs.append((vb, vc, stride))
            pos = test
        else:
            pos = w_pos + 4
    return runs


def read_vb(data, vb_off, vc, stride):
    """Read vertex buffer into numpy arrays."""
    fmt = STRIDE_FMT[stride]
    p, n, uv = fmt['p'], fmt['n'], fmt['uv']
    verts = np.zeros((vc, 3), dtype=np.float64)
    norms = np.zeros((vc, 3), dtype=np.float64)
    uvs = np.zeros((vc, 2), dtype=np.float64)
    for i in range(vc):
        off = vb_off + i * stride
        verts[i] = [f32(data, off+p), f32(data, off+p+4), f32(data, off+p+8)]
        norms[i] = [f32(data, off+n), f32(data, off+n+4), f32(data, off+n+8)]
        uvs[i] = [f32(data, off+uv), 1.0 - f32(data, off+uv+4)]
    return verts, norms, uvs


def find_ib(data, ib_start, vc, max_scan=50000):
    """Find all valid indices after a VB, supporting both list and strip."""
    indices = []
    scan_end = min(len(data), ib_start + max_scan)
    for i in range((scan_end - ib_start) // 2):
        v = u16(data, ib_start + i * 2)
        if v < vc:
            indices.append(v)
        else:
            if len(indices) > 6:
                break
            # Small OOB gap = strip restart, large = end
            indices.append(-1)  # marker
    # Trim trailing markers
    while indices and indices[-1] == -1:
        indices.pop()
    return indices


def decode_faces(indices, vc, is_strip):
    """Decode index buffer to face list."""
    faces = []
    if is_strip:
        buf = []
        for v in indices:
            if v == -1 or v >= vc:
                buf = []
                continue
            buf.append(v)
            if len(buf) >= 3:
                i0, i1, i2 = buf[-3], buf[-2], buf[-1]
                if i0 != i1 and i1 != i2 and i0 != i2:
                    idx = len(buf) - 3
                    if idx % 2 == 0:
                        faces.append([i0, i1, i2])
                    else:
                        faces.append([i0, i2, i1])
    else:
        clean = [v for v in indices if v >= 0 and v < vc]
        for i in range(0, len(clean) - 2, 3):
            i0, i1, i2 = clean[i], clean[i+1], clean[i+2]
            if i0 != i1 and i1 != i2 and i0 != i2:
                faces.append([i0, i1, i2])
    return faces


def extract_parts(data):
    """Extract all mesh parts from MDX binary."""
    parts = []
    all_runs = []
    for stride in [72, 84]:
        all_runs.extend(find_w_runs(data, stride))
    all_runs.sort(key=lambda x: x[0])

    # Deduplicate overlapping runs
    deduped = []
    for run in all_runs:
        if deduped and run[0] < deduped[-1][0] + deduped[-1][1] * deduped[-1][2]:
            prev = deduped[-1]
            if run[1] > prev[1]:
                deduped[-1] = run
        else:
            deduped.append(run)

    for vb_off, vc, stride in deduped:
        vb_end = vb_off + vc * stride
        verts, norms, uvs = read_vb(data, vb_off, vc, stride)
        indices = find_ib(data, vb_end, vc)

        if len(indices) < 6:
            continue

        # Try strip decode first, fall back to list
        is_strip = stride == 84 or any(v == -1 for v in indices)
        faces = decode_faces(indices, vc, is_strip=True)
        if not faces:
            faces = decode_faces(indices, vc, is_strip=False)
        if not faces:
            continue

        faces_arr = np.array(faces, dtype=np.int64)
        parts.append({
            'verts': verts, 'norms': norms, 'uvs': uvs,
            'faces': faces_arr, 'stride': stride, 'vc': vc,
        })

    return parts


def build_gltf_scene(parts, texture_path, name, do_mirror=True):
    """Build a trimesh Scene with per-part meshes."""
    scene = trimesh.Scene()

    # Load texture as material
    material = None
    if texture_path and texture_path.exists():
        img = Image.open(str(texture_path))
        material = trimesh.visual.material.PBRMaterial(
            baseColorTexture=img,
            metallicFactor=0.3,
            roughnessFactor=0.6,
            doubleSided=True,
        )

    def add_part(verts, norms, uvs, faces, suffix):
        if len(faces) == 0:
            return
        mesh = trimesh.Trimesh(
            vertices=verts,
            faces=faces,
            vertex_normals=norms,
            process=False,
        )
        if material and len(uvs) == len(verts):
            mesh.visual = trimesh.visual.TextureVisuals(
                uv=uvs, material=material,
            )
        scene.add_geometry(mesh, node_name=f'{name}_{suffix}')

    for pi, part in enumerate(parts):
        add_part(part['verts'], part['norms'], part['uvs'], part['faces'],
                 f'part{pi}_s{part["stride"]}')

        if do_mirror:
            m_verts = part['verts'].copy()
            m_verts[:, 0] *= -1
            m_norms = part['norms'].copy()
            m_norms[:, 0] *= -1
            m_faces = part['faces'][:, [0, 2, 1]]  # reverse winding
            m_uvs = part['uvs'].copy()
            add_part(m_verts, m_norms, m_uvs, m_faces,
                     f'part{pi}_s{part["stride"]}_mirror')

    return scene


def main():
    parser = argparse.ArgumentParser(description='MDX to glTF converter')
    parser.add_argument('input', type=Path)
    parser.add_argument('--tex', type=Path, required=True)
    parser.add_argument('--out', type=Path, default=Path('/tmp/mdx_gltf_out'))
    args = parser.parse_args()

    files = [args.input] if args.input.is_file() else sorted(args.input.glob('*_h.mdx'))
    args.out.mkdir(parents=True, exist_ok=True)

    ok = 0
    for mdx in files:
        data = mdx.read_bytes()
        name = mdx.stem
        if name.endswith('_h'):
            name = name[:-2]

        parts = extract_parts(data)
        if not parts:
            print(f'  {name}: FAIL (no parts)')
            continue

        tex_path = args.tex / f'{name}_h.dds'
        # Convert DDS to PNG in memory
        png_path = args.out / name / 'texture.png'
        png_path.parent.mkdir(parents=True, exist_ok=True)
        if tex_path.exists():
            try:
                img = Image.open(str(tex_path))
                img.save(str(png_path), 'PNG')
            except Exception:
                tex_path = None

        scene = build_gltf_scene(parts, tex_path if tex_path and tex_path.exists() else None, name)

        gltf_path = args.out / name / f'{name}.glb'
        scene.export(str(gltf_path), file_type='glb')

        tv = sum(p['verts'].shape[0] for p in parts) * 2  # mirrored
        tf = sum(p['faces'].shape[0] for p in parts) * 2
        np_parts = len(parts)
        print(f'  {name}: {tv}v {tf}f ({np_parts} parts x2) → .glb')
        ok += 1

    print(f'\n=== {ok}/{len(files)} exported to {args.out} ===')


if __name__ == '__main__':
    main()
