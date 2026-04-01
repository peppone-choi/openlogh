#!/usr/bin/env python3
"""
MDS Parser v2 - TOC-aware combined-pool parser for gin7 warship models.

Key insights from reverse engineering:
  - VBs form a combined pool: s72 vertices at index 0, s84 at offset s72.vc
  - Declared IBs use LOCAL indices (per-VB, 0-based)
  - Gap/extra IBs between VBs use COMBINED POOL indices
  - Gap IBs contain cross-VB faces (hull side connections)
  - s24 is a separate wireframe/collision mesh (excluded from pool)
  - Mirroring: X-axis flip + winding reversal for full hull

Data layout per MDS file:
  [TOC 12x8B] [TOC sections] [Descriptors Nx36B] [u32 body_count?]
  [s24_VB] [s24_IB_list]
  [gap_strip_IB (pool indices)]
  [s72_VB] [s72_IB_list]
  [gap_strip_IB (pool indices, cross-VB)]
  [s84_VB] [s84_IB_strip (local)] [extra_strip_IB (pool indices)]
  [non-mesh tail data]

Usage:
  python mds_parser_v2.py <mdx_file_or_dir> [--out <dir>] [--format obj|glb]
"""

import struct, math, argparse, os, tempfile
from pathlib import Path

# ── Primitives ─────────────────────────────────────────────

def u16(d, o): return struct.unpack_from('<H', d, o)[0]
def u32(d, o): return struct.unpack_from('<I', d, o)[0]
def f32(d, o):
    v = struct.unpack_from('<f', d, o)[0]
    if math.isnan(v) or math.isinf(v):
        return 0.0
    return v

MARKER_STRIDE = {18: 24, 65810: 36, 74002: 72, 336402: 84}
STRIDE_POS    = {24: 0,  36: 0,  72: 0,  84: 52}
STRIDE_NORM   = {24: 12, 36: 12, 72: 12, 84: 40}
STRIDE_UV     = {24: -1, 36: 24, 72: 24, 84: 68}
W_ONE = struct.pack('<f', 1.0)


# ── TOC ────────────────────────────────────────────────────

def parse_toc(data):
    entries = []
    for i in range(12):
        off = i * 8
        offset, magic, count, extra = struct.unpack_from('<4H', data, off)
        entries.append({'idx': i, 'off': offset, 'magic': magic,
                        'count': count, 'extra': extra})
    return entries


def parse_textures(data, toc):
    """Extract DDS texture paths from TOC[7]."""
    t7 = toc[7]
    paths = []
    off = t7['off']
    for _ in range(t7['count']):
        s = []
        while off < len(data) and data[off] != 0:
            s.append(chr(data[off]) if 32 <= data[off] < 127 else '?')
            off += 1
        off += 1  # skip null
        if s:
            paths.append(''.join(s))
    return paths


def parse_materials(data, toc):
    """Extract material properties from TOC[6]."""
    t6 = toc[6]
    if t6['count'] == 0:
        return []
    # Find next section offset for record size calculation
    next_off = min(t['off'] for t in toc if t['off'] > t6['off'])
    total = next_off - t6['off']
    if t6['count'] == 0:
        return []
    rec_size = total // t6['count']

    mats = []
    for i in range(t6['count']):
        roff = t6['off'] + i * rec_size
        # Material data starts at +0x64 offset within record
        base = roff + 0x64
        if base + 0x58 > len(data):
            mats.append({'diffuse': (0.8, 0.8, 0.8), 'specular': 0.5})
            continue
        # +0x64: u32 type (4 = standard)
        # +0x78: diffuse RGB (3 floats)
        # +0x8C: specular intensity
        dr = f32(data, roff + 0x78)
        dg = f32(data, roff + 0x7C)
        db = f32(data, roff + 0x80)
        spec = f32(data, roff + 0x8C)
        mats.append({'diffuse': (dr, dg, db), 'specular': spec})
    return mats


# ── Descriptor finding ─────────────────────────────────────

def find_standard_descriptors(data):
    """Find 36-byte descriptors: [prim][ptr_vb][vc][ptr_ib][ic][ptr_x][marker][pad=0][stride]."""
    descs = []
    for off in range(0, len(data) - 36, 4):
        mk = u32(data, off + 24)
        if mk not in MARKER_STRIDE:
            continue
        st = u32(data, off + 32)
        if st != MARKER_STRIDE[mk]:
            continue
        if u32(data, off + 28) != 0:
            continue
        prim = u32(data, off)
        vc = u32(data, off + 8)
        ic = u32(data, off + 16)
        if prim > 2 or vc == 0 or vc > 200000 or ic == 0 or ic > 2000000:
            continue
        if vc * st > len(data):
            continue
        descs.append({
            'off': off, 'prim': prim, 'vc': vc, 'ic': ic, 'stride': st,
            'type': 'std',
        })
    return descs


def find_inline_descriptors(data):
    """Find 32-byte inline descriptors: [ptr_vb][vc][ptr_ib][ic][ptr_x][marker][pad=0][stride].
    No prim field; all observed cases use triangle list (prim=0)."""
    descs = []
    for off in range(0, len(data) - 32, 2):
        mk = u32(data, off + 0x14)
        if mk not in MARKER_STRIDE:
            continue
        st = u32(data, off + 0x1C)
        if st != MARKER_STRIDE[mk]:
            continue
        if u32(data, off + 0x18) != 0:
            continue
        vc = u32(data, off + 0x04)
        ic = u32(data, off + 0x0C)
        if vc == 0 or vc > 200000 or ic == 0 or ic > 2000000:
            continue
        if vc * st > len(data):
            continue
        descs.append({
            'off': off, 'prim': 0, 'vc': vc, 'ic': ic, 'stride': st,
            'type': 'inline',
        })
    return descs


def _descs_are_contiguous(descs, size):
    """Check if descriptors form a contiguous block (adjacent at `size` bytes)."""
    if len(descs) <= 1:
        return True
    for i in range(1, len(descs)):
        if descs[i]['off'] - descs[i-1]['off'] != size:
            return False
    return True


def find_descriptors(data):
    """Find descriptors: prefer contiguous standard 36B; fall back to inline 32B."""
    std = find_standard_descriptors(data)
    inl = find_inline_descriptors(data)

    if not std and not inl:
        return []
    if not inl:
        return std
    if not std:
        return inl

    # Standard descriptors that form a contiguous block are the real ones
    if _descs_are_contiguous(std, 36):
        return std

    # Otherwise use inline (standard ones are likely false positives in VB data)
    return inl


# ── VB location ────────────────────────────────────────────

def find_vb_start(data, stride, vc, search_from, search_to=None):
    """Locate VB start by scanning for vertex format signatures."""
    if search_to is None:
        search_to = len(data)
    expected = vc * stride
    limit = min(search_to, len(data) - expected)

    if stride in (72,):
        # s72: W=1.0 at vertex offset +32 (may be 2-byte aligned)
        for off in range(search_from, limit, 2):
            if data[off+32:off+36] == W_ONE:
                ok = True
                for v in range(1, min(5, vc)):
                    if data[off + v*72 + 32 : off + v*72 + 36] != W_ONE:
                        ok = False
                        break
                if ok:
                    return off
    elif stride in (84,):
        # s84: W=1.0 at vertex offset +0 (may be 2-byte aligned)
        for off in range(search_from, limit, 2):
            if data[off:off+4] == W_ONE:
                ok = True
                for v in range(1, min(5, vc)):
                    if data[off + v*84 : off + v*84 + 4] != W_ONE:
                        ok = False
                        break
                if ok:
                    return off
    elif stride in (24, 36):
        # s24/s36: scan for reasonable position + normal
        for off in range(search_from, limit, 2):
            x, y, z = f32(data, off), f32(data, off+4), f32(data, off+8)
            if not all(abs(v) < 10000 for v in [x, y, z]):
                continue
            nx, ny, nz = f32(data, off+12), f32(data, off+16), f32(data, off+20)
            nlen = math.sqrt(nx*nx + ny*ny + nz*nz)
            if 0.5 < nlen < 1.5:
                return off
    return None


# ── Vertex reading ─────────────────────────────────────────

def read_vertices(data, vb_off, vc, stride):
    """Read vc vertices, return (positions, normals, uvs) lists."""
    p_off = STRIDE_POS[stride]
    n_off = STRIDE_NORM[stride]
    uv_off = STRIDE_UV[stride]
    verts, norms, uvs = [], [], []
    for i in range(vc):
        base = vb_off + i * stride
        verts.append((f32(data, base + p_off),
                       f32(data, base + p_off + 4),
                       f32(data, base + p_off + 8)))
        norms.append((f32(data, base + n_off),
                       f32(data, base + n_off + 4),
                       f32(data, base + n_off + 8)))
        if uv_off >= 0:
            uvs.append((f32(data, base + uv_off),
                         1.0 - f32(data, base + uv_off + 4)))
        else:
            uvs.append((0.0, 0.0))
    return verts, norms, uvs


# ── Index decoding ─────────────────────────────────────────

def decode_list_ib(data, ib_off, ic, vc):
    """Decode triangle list IB. Returns face tuples."""
    faces = []
    max_off = len(data) - 6
    for fi in range(ic // 3):
        base = ib_off + fi * 6
        if base > max_off:
            break
        i0, i1, i2 = u16(data, base), u16(data, base+2), u16(data, base+4)
        if i0 == i1 or i1 == i2 or i0 == i2:
            continue
        if max(i0, i1, i2) >= vc:
            continue
        faces.append((i0, i1, i2))
    return faces


def decode_strip_ib(data, ib_off, count, pool_size):
    """Decode triangle strip IB with OOB restart markers.
    Indices >= pool_size are treated as degenerate/restart."""
    faces = []
    buf = []
    max_off = len(data) - 2
    for i in range(count):
        off = ib_off + i * 2
        if off > max_off:
            break
        idx = u16(data, off)
        if idx >= pool_size:
            buf = []
            continue
        buf.append(idx)
        if len(buf) >= 3:
            i0, i1, i2 = buf[-3], buf[-2], buf[-1]
            if i0 != i1 and i1 != i2 and i0 != i2:
                wind = (len(buf) - 3) % 2
                faces.append((i0, i1, i2) if wind == 0 else (i0, i2, i1))
    return faces


def find_strip_end(data, start, pool_size, max_scan=100000):
    """Scan from start to find where strip IB ends.
    Ends when 30+ consecutive indices are all OOB (>= pool_size)."""
    consecutive_oob = 0
    last_valid = start
    limit = min(len(data), start + max_scan)
    off = start
    while off + 2 <= limit:
        idx = u16(data, off)
        if idx < pool_size:
            consecutive_oob = 0
            last_valid = off + 2
        else:
            consecutive_oob += 1
            if consecutive_oob >= 30:
                return last_valid
        off += 2
    return last_valid


# ── Core extraction ────────────────────────────────────────

def _sequential_walk_stride(data, descs_for_stride, first_vb, pool_base_start,
                            pool_size, verbose=False):
    """Walk interleaved [VB][declared IB][gap strip IB] blocks for one stride group.

    Returns: (verts, norms, uvs, faces, next_file_pos)
    where faces use combined pool indices.
    """
    stride = descs_for_stride[0]['stride']
    verts, norms, uvs = [], [], []
    faces = []
    pos = first_vb
    pool_base = pool_base_start

    for di, d in enumerate(descs_for_stride):
        vc, ic, prim = d['vc'], d['ic'], d['prim']

        # ── Read VB ──
        vb_start = pos
        vb_end = vb_start + vc * stride
        v, n, uv = read_vertices(data, vb_start, vc, stride)
        verts.extend(v)
        norms.extend(n)
        uvs.extend(uv)

        # ── Read declared IB ──
        ib_off = vb_end
        if prim == 0:
            dec_faces = decode_list_ib(data, ib_off, ic, vc)
            dec_faces = [(f[0]+pool_base, f[1]+pool_base, f[2]+pool_base)
                         for f in dec_faces]
        elif prim == 1:
            dec_faces = decode_strip_ib(data, ib_off, ic, vc)
            dec_faces = [(f[0]+pool_base, f[1]+pool_base, f[2]+pool_base)
                         for f in dec_faces]
        else:
            dec_faces = []

        faces.extend(dec_faces)
        ib_end = ib_off + ic * 2

        if verbose:
            print("    [%d] VB@0x%X vc=%d IB@0x%X ic=%d dec=%df pool_base=%d" %
                  (di, vb_start, vc, ib_off, ic, len(dec_faces), pool_base))

        # ── Scan gap strip IB (pool indices) ──
        # Scan u16 values until we hit the next VB or non-index data
        gap_start = ib_end
        gap_faces = []
        buf = []
        scan_limit = min(len(data), gap_start + 200000)
        gap_end = gap_start
        j = 0
        consecutive_oob = 0

        while gap_start + j * 2 + 2 <= scan_limit:
            off = gap_start + j * 2
            idx = u16(data, off)
            j += 1

            if idx < pool_size:
                consecutive_oob = 0
                buf.append(idx)
                if len(buf) >= 3:
                    i0, i1, i2 = buf[-3], buf[-2], buf[-1]
                    if i0 != i1 and i1 != i2 and i0 != i2:
                        wind = (len(buf) - 3) % 2
                        gap_faces.append(
                            (i0, i1, i2) if wind == 0 else (i0, i2, i1))
                gap_end = off + 2
            else:
                consecutive_oob += 1
                buf = []
                # Check if we've hit the next VB (W=1.0 signature)
                if consecutive_oob >= 1 and off + stride <= len(data):
                    if stride in (72,) and data[off+32:off+36] == W_ONE:
                        px = f32(data, off)
                        if not math.isnan(px) and abs(px) < 10000:
                            gap_end = off
                            break
                    elif stride in (84,) and data[off:off+4] == W_ONE:
                        gap_end = off
                        break
                    elif stride in (24, 36) and off + 24 <= len(data):
                        px = f32(data, off)
                        nx = f32(data, off + 12)
                        nl = math.sqrt(sum(f32(data, off+12+k*4)**2 for k in range(3)))
                        if abs(px) < 10000 and 0.5 < nl < 1.5:
                            gap_end = off
                            break
                if consecutive_oob >= 30:
                    break

        if gap_faces:
            faces.extend(gap_faces)
            if verbose:
                print("      gap@0x%X: %d faces" % (gap_start, len(gap_faces)))

        pool_base += vc
        pos = gap_end

    return verts, norms, uvs, faces, pos


def extract_mdx(data, name="", verbose=True):
    """Extract mesh from MDS/MDX data using sequential interleaved walk.

    Layout per stride group: [VB₀][IB₀][gap strip][VB₁][IB₁][gap strip]...
    Returns dict with keys: verts, norms, uvs, faces, materials, textures
    or None on failure.
    """
    toc = parse_toc(data)
    descs = find_descriptors(data)

    if not descs:
        if verbose:
            print("  %s: no descriptors found" % name)
        return None

    if verbose:
        print("  %s: %d descriptors" % (name, len(descs)))
        for i, d in enumerate(descs):
            print("    [%d] stride=%d vc=%d ic=%d prim=%d" %
                  (i, d['stride'], d['vc'], d['ic'], d['prim']))

    # Separate s24 from pool-eligible descriptors (s36/s72/s84)
    pool_descs = [d for d in descs if d['stride'] != 24]

    if not pool_descs:
        if verbose:
            print("  %s: no pool-eligible descriptors" % name)
        return None

    # ── Phase 1: Group descriptors by stride, preserving file order ──
    # Descriptors are in file order; group consecutive same-stride descs
    stride_groups = []  # list of (stride, [descs])
    for d in pool_descs:
        if stride_groups and stride_groups[-1][0] == d['stride']:
            stride_groups[-1][1].append(d)
        else:
            stride_groups.append((d['stride'], [d]))

    # Data section start
    data_section = toc[8]['off'] if toc[8]['count'] == 0 and toc[8]['off'] > 96 else 0
    desc_block_end = descs[-1]['off'] + (36 if descs[-1].get('type') == 'std' else 32)
    search_start = max(data_section, desc_block_end) if descs[0].get('type') == 'std' else data_section

    # ── Phase 2: Find first VB per stride group and walk sequentially ──
    pool_verts = []
    pool_norms = []
    pool_uvs = []
    all_faces = []

    # Calculate total pool size for gap strip validation
    total_pool = sum(d['vc'] for d in pool_descs)

    # Also handle s24 separately (find its VB for completeness)
    s24_descs = [d for d in descs if d['stride'] == 24]
    s24_vb = None
    if s24_descs:
        scan = search_start
        if scan % 4:
            scan += 4 - (scan % 4)
        s24_vb = find_vb_start(data, 24, s24_descs[0]['vc'], scan)
        if verbose and s24_vb is not None:
            print("    s24 VB@0x%X (excluded from pool)" % s24_vb)

    # For each stride group, find first VB then walk
    scan_pos = search_start
    if scan_pos % 4:
        scan_pos += 4 - (scan_pos % 4)

    # Skip past s24 region if it's before pool data
    if s24_vb is not None and s24_vb < scan_pos + 0x2000:
        s24_end = s24_vb + s24_descs[0]['vc'] * 24 + s24_descs[0]['ic'] * 2
        # Scan past any gap after s24
        scan_pos = max(scan_pos, s24_end)

    for stride, grp_descs in stride_groups:
        # Find first VB of this stride group
        total_grp_vc = sum(d['vc'] for d in grp_descs)
        first_vb = find_vb_start(data, stride, grp_descs[0]['vc'], scan_pos)

        if first_vb is None:
            if verbose:
                print("    WARNING: stride=%d first VB not found (scan from 0x%X)" %
                      (stride, scan_pos))
            continue

        pool_base = len(pool_verts)

        if verbose:
            print("    stride=%d group: %d descs, %d verts, first VB@0x%X" %
                  (stride, len(grp_descs), total_grp_vc, first_vb))

        v, n, uv, faces, next_pos = _sequential_walk_stride(
            data, grp_descs, first_vb, pool_base, total_pool, verbose)

        pool_verts.extend(v)
        pool_norms.extend(n)
        pool_uvs.extend(uv)
        all_faces.extend(faces)

        # Update scan position for next stride group
        scan_pos = next_pos

    pool_size = len(pool_verts)
    if pool_size == 0:
        if verbose:
            print("  %s: empty vertex pool" % name)
        return None

    if verbose:
        print("    Combined pool: %d vertices, %d faces" %
              (pool_size, len(all_faces)))

    # ── Extract metadata ──
    textures = parse_textures(data, toc)
    materials = parse_materials(data, toc)

    return {
        'name': name,
        'verts': pool_verts,
        'norms': pool_norms,
        'uvs': pool_uvs,
        'faces': all_faces,
        'pool_size': pool_size,
        'textures': textures,
        'materials': materials,
    }


# ── Output: OBJ ───────────────────────────────────────────

def write_obj(mesh, out_path, do_mirror=True):
    """Write mesh to OBJ file with optional mirroring."""
    verts = mesh['verts']
    norms = mesh['norms']
    uvs = mesh['uvs']
    faces = mesh['faces']

    with open(out_path, 'w') as f:
        f.write("# MDS Parser v2 - %s\n" % mesh['name'])
        f.write("# Vertices: %d  Faces: %d\n\n" % (len(verts), len(faces)))

        nv = len(verts)

        # Original half
        for v in verts:
            f.write("v %.6f %.6f %.6f\n" % v)
        for n in norms:
            f.write("vn %.6f %.6f %.6f\n" % n)
        for uv in uvs:
            f.write("vt %.6f %.6f\n" % uv)

        if do_mirror:
            # Mirror half (X-flip)
            for v in verts:
                f.write("v %.6f %.6f %.6f\n" % (-v[0], v[1], v[2]))
            for n in norms:
                f.write("vn %.6f %.6f %.6f\n" % (-n[0], n[1], n[2]))
            for uv in uvs:
                f.write("vt %.6f %.6f\n" % uv)

        f.write("\ng original\n")
        for face in faces:
            i0, i1, i2 = face[0]+1, face[1]+1, face[2]+1
            f.write("f %d/%d/%d %d/%d/%d %d/%d/%d\n" %
                    (i0,i0,i0, i1,i1,i1, i2,i2,i2))

        if do_mirror:
            f.write("\ng mirror\n")
            for face in faces:
                # Reversed winding for mirror
                i0 = face[0]+1+nv
                i1 = face[2]+1+nv  # swapped
                i2 = face[1]+1+nv
                f.write("f %d/%d/%d %d/%d/%d %d/%d/%d\n" %
                        (i0,i0,i0, i1,i1,i1, i2,i2,i2))

    return len(faces) * (2 if do_mirror else 1)


# ── Output: glTF/GLB ──────────────────────────────────────

def write_glb(mesh, out_path, tex_path=None, do_mirror=True):
    """Write mesh to GLB using trimesh."""
    try:
        import numpy as np
        import trimesh
    except ImportError:
        print("ERROR: trimesh and numpy required for GLB output")
        print("  pip install trimesh numpy")
        return 0

    verts = mesh['verts']
    norms = mesh['norms']
    uvs_list = mesh['uvs']
    faces = mesh['faces']

    scene = trimesh.Scene()

    # Load texture material
    material = None
    if tex_path and os.path.exists(tex_path):
        try:
            from PIL import Image
            img = Image.open(str(tex_path))
            material = trimesh.visual.material.PBRMaterial(
                baseColorTexture=img,
                metallicFactor=0.3,
                roughnessFactor=0.6,
                doubleSided=True,
            )
        except Exception:
            pass

    if not material and mesh['materials']:
        mat = mesh['materials'][0]
        r, g, b = mat['diffuse']
        material = trimesh.visual.material.PBRMaterial(
            baseColorFactor=[int(r*255), int(g*255), int(b*255), 255],
            metallicFactor=0.3,
            roughnessFactor=0.6,
            doubleSided=True,
        )

    def add_half(v_list, n_list, uv_list, f_list, node_name):
        if not f_list:
            return 0
        v = np.array(v_list, dtype=np.float64)
        n = np.array(n_list, dtype=np.float64)
        f = np.array(f_list, dtype=np.int64)
        m = trimesh.Trimesh(vertices=v, faces=f, vertex_normals=n, process=False)
        if material and len(uv_list) == len(v_list):
            uv = np.array(uv_list, dtype=np.float64)
            m.visual = trimesh.visual.TextureVisuals(uv=uv, material=material)
        scene.add_geometry(m, node_name=node_name)
        return len(f)

    name = mesh['name']
    total_f = 0

    # Original half
    total_f += add_half(verts, norms, uvs_list, faces, name + '_orig')

    # Mirror half
    if do_mirror:
        m_verts = [(-v[0], v[1], v[2]) for v in verts]
        m_norms = [(-n[0], n[1], n[2]) for n in norms]
        m_faces = [(f[0], f[2], f[1]) for f in faces]
        total_f += add_half(m_verts, m_norms, uvs_list, m_faces, name + '_mirror')

    scene.export(str(out_path), file_type='glb')
    return total_f


# ── CLI ────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description='MDS Parser v2 - gin7 warship mesh extractor')
    parser.add_argument('input', type=Path, help='MDX file or directory')
    parser.add_argument('--out', type=Path, default=None, help='Output directory')
    parser.add_argument('--format', choices=['obj', 'glb'], default='obj')
    parser.add_argument('--tex', type=Path, default=None, help='Texture directory (DDS)')
    parser.add_argument('--no-mirror', action='store_true')
    parser.add_argument('--verbose', '-v', action='store_true')
    args = parser.parse_args()

    if args.out is None:
        args.out = Path(tempfile.gettempdir()) / 'mds_v2_out'
    args.out.mkdir(parents=True, exist_ok=True)

    if args.input.is_file():
        files = [args.input]
    else:
        files = sorted(args.input.glob('*_h.mdx'))

    do_mirror = not args.no_mirror
    ok = 0
    fail = 0
    total_v = 0
    total_f = 0

    for mdx_path in files:
        data = mdx_path.read_bytes()
        name = mdx_path.stem
        if name.endswith('_h'):
            name = name[:-2]

        mesh = extract_mdx(data, name, verbose=args.verbose)
        if mesh is None:
            print("  FAIL: %s" % name)
            fail += 1
            continue

        out_dir = args.out / name
        out_dir.mkdir(parents=True, exist_ok=True)

        if args.format == 'obj':
            out_file = out_dir / (name + '.obj')
            nf = write_obj(mesh, out_file, do_mirror)
        else:
            tex_file = None
            if args.tex and mesh['textures']:
                # Try to find DDS texture
                for tname in mesh['textures']:
                    t = args.tex / tname
                    if t.exists():
                        tex_file = t
                        break
                    # Try with _h suffix
                    t = args.tex / (name + '_h.dds')
                    if t.exists():
                        tex_file = t
                        break
            out_file = out_dir / (name + '.glb')
            nf = write_glb(mesh, out_file, tex_file, do_mirror)

        nv = len(mesh['verts'])
        nfaces = len(mesh['faces'])
        print("  OK: %s  %dv %df (x2=%d) -> %s" %
              (name, nv, nfaces, nf, out_file))
        ok += 1
        total_v += nv
        total_f += nfaces

    print("\n=== %d/%d OK, %d FAIL, %d total vertices, %d total faces ===" %
          (ok, ok + fail, fail, total_v, total_f))


if __name__ == '__main__':
    main()
