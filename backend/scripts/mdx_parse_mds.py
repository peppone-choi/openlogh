#!/usr/bin/env python3
"""
MDS-aware MDX parser — proper file format parsing based on ginei.exe reverse engineering.

Structure:
  MDS file = TOC (12×8B) + record sections + data section
  Data section = [descriptor block] [VB/IB pairs per descriptor]

Descriptor: 36 bytes
  +0x00: u32 prim_type (0=list, 1=strip, 2=fan?)
  +0x04: u32 runtime_ptr_vb
  +0x08: u32 vertex_count
  +0x0C: u32 runtime_ptr_ib
  +0x10: u32 index_count
  +0x14: u32 runtime_ptr_extra
  +0x18: u32 marker (18=s24, 65810=s36, 74002=s72, 336402=s84)
  +0x1C: u32 padding (0)
  +0x20: u32 stride (24, 36, 72, 84)

Vertex formats:
  stride-24: pos(xyz) + normal(xyz)
  stride-36: pos(xyz) + normal(xyz) + uv(st) + ???
  stride-72: pos(xyz) + W=1.0@+32 + ... (pos@+0, normal@+12, uv@+24)
  stride-84: W=1.0@+0 + ... + pos(xyz)@+52 (normal@+16, uv@+68)

Data layout per body:
  [desc_list(36B)] [desc_strip(36B)] [optional more descs]
  [u32 count?]
  [VB_s24] [IB_s24]        (if s24 desc exists)
  [gap/metadata]
  [VB_list] [IB_list]       (stride-72 or -84 list)
  [strip_IB]
  [VB_strip] [IB_strip]     (strip version, same mesh)
  ...

Usage:
  python mdx_parse_mds.py <mdx_file_or_dir> [--out <dir>]
"""

import struct, math, argparse
from pathlib import Path

def f32(d, o): return struct.unpack_from('<f', d, o)[0]
def u16(d, o): return struct.unpack_from('<H', d, o)[0]
def u32(d, o): return struct.unpack_from('<I', d, o)[0]

MARKER_STRIDE = {18: 24, 65810: 36, 74002: 72, 336402: 84}
STRIDE_FMT = {
    24:  {'p': 0, 'n': 12, 'uv': -1, 'w': -1},
    36:  {'p': 0, 'n': 12, 'uv': 24, 'w': -1},
    72:  {'p': 0, 'n': 12, 'uv': 24, 'w': 32},
    84:  {'p': 52, 'n': 40, 'uv': 76, 'w': 0},
}
W_ONE = struct.pack('<f', 1.0)


# ── TOC parsing ─────────────────────────────────────────────

def parse_toc(data):
    entries = []
    for i in range(12):
        off, magic, count = struct.unpack_from('<HHH', data, i * 8)
        extra = struct.unpack_from('<H', data, i * 8 + 6)[0]
        entries.append({'idx': i, 'off': off, 'magic': magic, 'count': count, 'extra': extra})
    return entries


# ── Descriptor parsing ──────────────────────────────────────

def find_all_descriptors(data):
    descs = []
    for off in range(0, len(data) - 36, 4):
        mk = u32(data, off + 24)
        if mk not in MARKER_STRIDE:
            continue
        st = u32(data, off + 32)
        if st != MARKER_STRIDE[mk]:
            continue
        pad = u32(data, off + 28)
        if pad != 0:
            continue
        prim = u32(data, off)
        vc = u32(data, off + 8)
        ic = u32(data, off + 16)
        if prim > 2 or vc == 0 or vc > 200000 or ic == 0 or ic > 2000000:
            continue
        if vc * st > len(data):
            continue
        descs.append({
            'off': off, 'prim': prim, 'vc': vc, 'ic': ic,
            'stride': st, 'marker': mk,
        })
    return descs


def find_inline_descriptors(data):
    """Find inline descriptors (2-byte aligned) between VBs.

    Layout (32 bytes meaningful, may have trailing field):
      +0x00: u32 runtime_ptr_vb
      +0x04: u32 vertex_count
      +0x08: u32 runtime_ptr_ib
      +0x0C: u32 index_count
      +0x10: u32 runtime_ptr_extra
      +0x14: u32 marker (74002/336402/etc.)
      +0x18: u32 padding (0)
      +0x1C: u32 stride (72/84/etc.)
    """
    descs = []
    for off in range(0, len(data) - 32, 2):
        mk = u32(data, off + 0x14)
        if mk not in MARKER_STRIDE:
            continue
        st = u32(data, off + 0x1C)
        if st != MARKER_STRIDE[mk]:
            continue
        pad = u32(data, off + 0x18)
        if pad != 0:
            continue
        vc = u32(data, off + 0x04)
        ic = u32(data, off + 0x0C)
        if vc == 0 or vc > 200000 or ic == 0 or ic > 2000000:
            continue
        if ic % 3 != 0:
            continue
        if vc * st > len(data):
            continue
        descs.append({'off': off, 'vc': vc, 'ic': ic, 'stride': st})
    return descs


def group_descriptors(descs):
    """Group descriptors that are adjacent (within 36 bytes of each other)."""
    if not descs:
        return []
    groups = [[descs[0]]]
    for d in descs[1:]:
        prev = groups[-1][-1]
        if d['off'] - prev['off'] == 36:
            groups[-1].append(d)
        else:
            groups.append([d])
    return groups


# ── IB search ───────────────────────────────────────────────

def find_ib_exact(data, ic, vc, search_start, search_end):
    """Find contiguous ic u16 values all < vc with good coverage."""
    for off in range(search_start, min(search_end, len(data) - ic * 2), 2):
        # Quick check: first, mid, last
        ok = True
        for probe in [0, ic // 4, ic // 2, 3 * ic // 4, ic - 1]:
            if u16(data, off + probe * 2) >= vc:
                ok = False
                break
        if not ok:
            continue
        # Full validate
        mx = 0
        valid = True
        for i in range(ic):
            idx = u16(data, off + i * 2)
            if idx >= vc:
                valid = False
                break
            if idx > mx:
                mx = idx
        if valid and mx >= vc * 0.5:
            return off, mx
    return None, 0


# ── VB reading ──────────────────────────────────────────────

def safe_f32(data, off):
    v = f32(data, off)
    if math.isnan(v) or math.isinf(v):
        return 0.0
    return v


def read_vertices(data, vb_off, vc, stride):
    fmt = STRIDE_FMT[stride]
    p, n, uv_off = fmt['p'], fmt['n'], fmt['uv']
    verts, norms, uvs = [], [], []
    for i in range(vc):
        off = vb_off + i * stride
        verts.append((safe_f32(data, off + p), safe_f32(data, off + p + 4), safe_f32(data, off + p + 8)))
        norms.append((safe_f32(data, off + n), safe_f32(data, off + n + 4), safe_f32(data, off + n + 8)))
        if uv_off >= 0:
            uvs.append((safe_f32(data, off + uv_off), 1.0 - safe_f32(data, off + uv_off + 4)))
        else:
            uvs.append((0.0, 0.0))
    return verts, norms, uvs


# ── Face processing ─────────────────────────────────────────

def decode_list_faces(data, ib_off, ic, vc):
    faces = []
    for fi in range(ic // 3):
        i0 = u16(data, ib_off + fi * 6)
        i1 = u16(data, ib_off + fi * 6 + 2)
        i2 = u16(data, ib_off + fi * 6 + 4)
        if i0 == i1 or i1 == i2 or i0 == i2:
            continue
        if max(i0, i1, i2) >= vc:
            continue
        faces.append((i0, i1, i2))
    return faces


def decode_strip_faces(data, ib_off, ic, vc):
    faces = []
    for i in range(ic - 2):
        i0 = u16(data, ib_off + i * 2)
        i1 = u16(data, ib_off + i * 2 + 2)
        i2 = u16(data, ib_off + i * 2 + 4)
        if i0 == i1 or i1 == i2 or i0 == i2:
            continue
        if max(i0, i1, i2) >= vc:
            continue
        faces.append((i0, i1, i2) if i % 2 == 0 else (i0, i2, i1))
    return faces


def decode_gap_strip(data, gap_start, gap_end, vc):
    """Decode a gap region as triangle strip IB with OOB-as-restart.

    gin7 stores [VB][list_IB][strip_IB][next_VB] where the strip IB
    uses indices >= vc as strip restart markers.
    """
    count = (gap_end - gap_start) // 2
    if count < 6:
        return []
    faces = []
    buf = []
    for i in range(count):
        v = u16(data, gap_start + i * 2)
        if v >= vc:
            buf = []
            continue
        buf.append(v)
        if len(buf) >= 3:
            i0, i1, i2 = buf[-3], buf[-2], buf[-1]
            if i0 != i1 and i1 != i2 and i0 != i2:
                idx = len(buf) - 3
                faces.append((i0, i1, i2) if idx % 2 == 0 else (i0, i2, i1))
    return faces


def find_s84_strip_end(data, ib_start, ic, vc):
    """Find the true end of s84 strip IB, beyond descriptor ic.

    s84 layout: [VB][strip_IB(ic)][more_strip_IB][PV or next section]
    The descriptor ic underreports the actual strip length.
    After ic, valid strip indices (with OOB restart markers) continue.
    PV boundary is detected by a long run of consecutive OOB values (>50).
    """
    gap_start = ib_start + ic * 2
    scan_limit = min(len(data), gap_start + 40000)
    consecutive_oob = 0
    end = gap_start
    for i in range((scan_limit - gap_start) // 2):
        v = u16(data, gap_start + i * 2)
        if v >= vc:
            consecutive_oob += 1
            if consecutive_oob > 50:
                end = gap_start + (i - 50) * 2
                return end
        else:
            consecutive_oob = 0
            end = gap_start + (i + 1) * 2
    return end


def decode_s84_mesh(data, desc, vb_off):
    """Dedicated s84 decoder — handles strip IB + gap strip beyond ic.

    s84 descriptors use prim=1 (triangle strip). The descriptor ic value
    underreports the actual strip length. Additional strip IB data follows
    after ic, using indices >= vc as strip restart markers.

    Returns: (faces, verts, norms, uvs) or None if extraction fails.
    """
    vc, ic, stride = desc['vc'], desc['ic'], desc['stride']
    ib_off = vb_off + vc * stride

    if ib_off + ic * 2 > len(data):
        return None

    # Validate IB start
    ib_ok = True
    for probe in [0, ic // 4, ic // 2, ic - 1]:
        if u16(data, ib_off + probe * 2) >= vc:
            ib_ok = False
            break
    if not ib_ok:
        return None

    # Read vertices
    verts, norms, uvs = read_vertices(data, vb_off, vc, stride)

    # Decode strip faces from descriptor ic
    faces = decode_strip_faces(data, ib_off, ic, vc)
    faces = clean_faces(faces, verts, norms)

    # Find true strip end (beyond ic, before PV)
    # Gap strip faces are filtered with long-edge check to prevent spaghetti
    strip_end = find_s84_strip_end(data, ib_off, ic, vc)
    gap_start = ib_off + ic * 2
    if strip_end > gap_start:
        gap_faces = decode_gap_strip(data, gap_start, strip_end, vc)
        gap_faces = clean_faces(gap_faces, verts, norms)
        # Topology-based spaghetti filter: only keep gap faces that
        # share at least 1 edge with main strip faces (grow from seed).
        # Isolated faces with no edge connection = spaghetti.
        if gap_faces:
            edge_set = set()
            for i0, i1, i2 in faces:
                edge_set.add((min(i0,i1), max(i0,i1)))
                edge_set.add((min(i1,i2), max(i1,i2)))
                edge_set.add((min(i2,i0), max(i2,i0)))
            # Iteratively grow: accept gap faces sharing edges with accepted set
            remaining = list(gap_faces)
            changed = True
            while changed:
                changed = False
                next_remaining = []
                for i0, i1, i2 in remaining:
                    e0 = (min(i0,i1), max(i0,i1))
                    e1 = (min(i1,i2), max(i1,i2))
                    e2 = (min(i2,i0), max(i2,i0))
                    if e0 in edge_set or e1 in edge_set or e2 in edge_set:
                        faces.append((i0, i1, i2))
                        edge_set.add(e0)
                        edge_set.add(e1)
                        edge_set.add(e2)
                        changed = True
                    else:
                        next_remaining.append((i0, i1, i2))
                remaining = next_remaining
    if not faces:
        return None

    return {
        'vc': vc, 'verts': verts, 'norms': norms, 'uvs': uvs,
        'faces': faces, 'stride': stride, 'source': 'desc',
        'vb_off': vb_off, 'vb_end': vb_off + vc * stride,
        'ib_end': strip_end, 'prim': desc['prim'],
    }


def fix_winding(i0, i1, i2, verts, norms):
    p0, p1, p2 = verts[i0], verts[i1], verts[i2]
    e1 = (p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2])
    e2 = (p2[0] - p0[0], p2[1] - p0[1], p2[2] - p0[2])
    fn = (e1[1] * e2[2] - e1[2] * e2[1],
          e1[2] * e2[0] - e1[0] * e2[2],
          e1[0] * e2[1] - e1[1] * e2[0])
    an = (norms[i0][0] + norms[i1][0] + norms[i2][0],
          norms[i0][1] + norms[i1][1] + norms[i2][1],
          norms[i0][2] + norms[i1][2] + norms[i2][2])
    if fn[0] * an[0] + fn[1] * an[1] + fn[2] * an[2] < 0:
        return (i0, i2, i1)
    return (i0, i1, i2)


def face_area(v0, v1, v2):
    e1 = (v1[0] - v0[0], v1[1] - v0[1], v1[2] - v0[2])
    e2 = (v2[0] - v0[0], v2[1] - v0[1], v2[2] - v0[2])
    cx = e1[1] * e2[2] - e1[2] * e2[1]
    cy = e1[2] * e2[0] - e1[0] * e2[2]
    cz = e1[0] * e2[1] - e1[1] * e2[0]
    return math.sqrt(cx * cx + cy * cy + cz * cz) / 2


def is_bad_vertex(v):
    """Zero-padded runtime slot or NaN/Inf garbage."""
    if any(math.isnan(c) or math.isinf(c) for c in v):
        return True
    if abs(v[0]) < 1e-6 and abs(v[1]) < 1e-6 and abs(v[2]) < 1e-6:
        return True
    return False


def edge_len_sq(v0, v1):
    dx = v0[0] - v1[0]
    dy = v0[1] - v1[1]
    dz = v0[2] - v1[2]
    return dx * dx + dy * dy + dz * dz


def clean_faces(raw_faces, verts, norms):
    vc = len(verts)
    if vc < 3:
        return []

    # First pass: basic validity
    valid = []
    for i0, i1, i2 in raw_faces:
        if max(i0, i1, i2) >= vc:
            continue
        if is_bad_vertex(verts[i0]) or is_bad_vertex(verts[i1]) or is_bad_vertex(verts[i2]):
            continue
        area = face_area(verts[i0], verts[i1], verts[i2])
        if area < 1e-8:
            continue
        valid.append((i0, i1, i2))

    if len(valid) < 4:
        return [fix_winding(i0, i1, i2, verts, norms) for i0, i1, i2 in valid]

    out = []
    for i0, i1, i2 in valid:
        f = fix_winding(i0, i1, i2, verts, norms)
        out.append(f)
    return out


# ── Descriptor-based extraction ─────────────────────────────

def find_vb_for_descriptor(data, desc):
    """Find VB matching descriptor by W=1.0 scanning for exact vertex count."""
    vc, stride = desc['vc'], desc['stride']
    fmt = STRIDE_FMT[stride]
    w_off = fmt['w']
    p_off = fmt['p']
    if w_off < 0:
        return None  # stride-24 has no W field

    search_start = max(0, desc['off'] - 1024)
    search_end = min(len(data), desc['off'] + vc * stride * 3)
    pos = search_start

    while pos < search_end:
        w_pos = data.find(W_ONE, pos, search_end)
        if w_pos == -1:
            break
        vb = w_pos - w_off
        if vb < 0 or vb + stride > len(data):
            pos = w_pos + 1
            continue
        x = f32(data, vb + p_off)
        if math.isnan(x) or abs(x) > 300:
            pos = w_pos + 1
            continue
        count = 0
        test = vb
        while test + stride <= len(data):
            w = f32(data, test + w_off)
            if abs(w - 1.0) > 0.05:
                break
            tpx = f32(data, test + p_off)
            if math.isnan(tpx) or abs(tpx) > 300:
                break
            count += 1
            test += stride
        if count == vc:
            return vb
        pos = test if count > 0 else w_pos + 4
    return None


def extract_descriptor_group(data, group):
    """Extract mesh parts from a descriptor group.

    Extracts both list (prim=0) and strip (prim=1) VBs.
    For paired list/strip covering the same region, keeps the one with more faces.
    """
    list_parts = []
    strip_parts = []

    for desc in group:
        vc, ic, stride = desc['vc'], desc['ic'], desc['stride']

        # Find VB by W=1.0 scanning (exact vc match), then IB is at VB end
        vb_off = find_vb_for_descriptor(data, desc)
        if vb_off is None:
            # Fallback: find IB first, back-calculate VB
            search_end = min(desc['off'] + vc * stride + ic * 2 + 200000, len(data))
            ib_off, mx = find_ib_exact(data, ic, vc, desc['off'] + 36, search_end)
            if ib_off is None:
                continue
            vb_off = ib_off - vc * stride
            if vb_off < 0:
                continue

        # s84: use dedicated decoder (handles gap strip beyond ic + PV boundary)
        if stride == 84:
            result = decode_s84_mesh(data, desc, vb_off)
            if result is not None:
                if desc['prim'] == 1:
                    strip_parts.append(result)
                else:
                    list_parts.append(result)
            continue

        ib_off = vb_off + vc * stride

        # Validate IB: check a few indices are < vc
        if ib_off + ic * 2 > len(data):
            continue
        ib_ok = True
        for probe in [0, ic // 4, ic // 2, ic - 1]:
            if u16(data, ib_off + probe * 2) >= vc:
                ib_ok = False
                break
        if not ib_ok:
            continue

        fmt = STRIDE_FMT[stride]
        x = f32(data, vb_off + fmt['p'])
        if math.isnan(x) or abs(x) > 300:
            continue

        verts, norms, uvs = read_vertices(data, vb_off, vc, stride)

        # Stride-72/36/24 IBs use triangle list (quad→tri pattern).
        faces = decode_list_faces(data, ib_off, ic, vc)

        faces = clean_faces(faces, verts, norms)
        if not faces:
            continue

        part = {
            'vc': vc, 'verts': verts, 'norms': norms, 'uvs': uvs,
            'faces': faces, 'stride': stride, 'source': 'desc',
            'vb_off': vb_off, 'vb_end': vb_off + vc * stride,
            'ib_end': ib_off + ic * 2, 'prim': desc['prim'],
        }

        if desc['prim'] == 1:
            strip_parts.append(part)
        else:
            list_parts.append(part)

    # Collect ALL VB ranges (list + strip) for covered tracking,
    # even if we only return the best one per overlapping pair.
    all_ranges = [(p['vb_off'], p['vb_end']) for p in list_parts + strip_parts]

    # For each strip part, check if a SAME-STRIDE list part covers the same Z-range.
    # Only dedup within the same stride (different strides = different mesh parts).
    parts = list(list_parts)
    for sp in strip_parts:
        sz = [v[2] for v in sp['verts']]
        sp_zmin, sp_zmax = min(sz), max(sz)
        replaced = False
        for i, lp in enumerate(parts):
            if lp['stride'] != sp['stride']:
                continue  # different stride = different mesh, don't dedup
            lz = [v[2] for v in lp['verts']]
            lp_zmin, lp_zmax = min(lz), max(lz)
            overlap = max(0, min(sp_zmax, lp_zmax) - max(sp_zmin, lp_zmin))
            span = max(sp_zmax - sp_zmin, lp_zmax - lp_zmin, 0.001)
            if overlap / span > 0.5:
                if len(sp['faces']) > len(lp['faces']):
                    parts[i] = sp
                replaced = True
                break
        if not replaced:
            parts.append(sp)

    return parts, all_ranges


# ── W=1.0 heuristic fallback (same-stride only) ────────────

def find_vbs_heuristic(data):
    """Find VBs via W=1.0 heuristic for stride-72 and stride-84."""
    vbs = []
    for stride, w_off, p_off in [(72, 32, 0), (84, 0, 52)]:
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
            if vc >= 30:
                vbs.append({'start': vb, 'count': vc, 'stride': stride})
                pos = test
            else:
                pos = w_pos + 4
    vbs.sort(key=lambda x: x['start'])
    return vbs


def _pick_best_ic(data, ib_off, ic_full, ic_sub, vc, stride, vb_start):
    """Pick the ic that avoids PV contamination (오연결).

    Uses two signals:
    1. Max-index coverage shift: if the IB part uses much fewer vertices than the PV part
    2. Long-edge face ratio: if full IC produces more artifact faces
    """
    # Signal 1: check max index in IB part vs PV part
    # If IB only uses a small fraction of vertices, PV is almost certainly included
    ib_sample = min(ic_sub, 2000)
    pv_sample = min(vc, 2000)
    ib_max = 0
    for i in range(ib_sample):
        v = u16(data, ib_off + i * 2)
        if v > ib_max:
            ib_max = v
    pv_max = 0
    for i in range(pv_sample):
        v = u16(data, ib_off + (ic_sub + i) * 2)
        if v > pv_max:
            pv_max = v

    # If IB part covers < 50% of vertices but PV part covers much more, it's PV
    if ib_max < vc * 0.5 and pv_max > ib_max * 1.5:
        return ic_sub

    # Signal 2: long-edge face comparison
    fmt = STRIDE_FMT[stride]
    p_off = fmt['p']
    sample = min(vc, 200)
    xs = [safe_f32(data, vb_start + i * stride + p_off) for i in range(sample)]
    ys = [safe_f32(data, vb_start + i * stride + p_off + 4) for i in range(sample)]
    zs = [safe_f32(data, vb_start + i * stride + p_off + 8) for i in range(sample)]
    diag = math.sqrt((max(xs)-min(xs))**2 + (max(ys)-min(ys))**2 + (max(zs)-min(zs))**2)
    if diag < 0.01:
        return ic_sub
    threshold = diag * 0.4

    def count_bad(ic):
        bad = 0
        total = 0
        for fi in range(ic // 3):
            i0 = u16(data, ib_off + fi * 6)
            i1 = u16(data, ib_off + fi * 6 + 2)
            i2 = u16(data, ib_off + fi * 6 + 4)
            if max(i0, i1, i2) >= vc or i0 == i1 or i1 == i2 or i0 == i2:
                continue
            total += 1
            for a, b in [(i0,i1),(i1,i2),(i2,i0)]:
                oa = vb_start + a * stride
                ob = vb_start + b * stride
                dx = safe_f32(data, oa+p_off) - safe_f32(data, ob+p_off)
                dy = safe_f32(data, oa+p_off+4) - safe_f32(data, ob+p_off+4)
                dz = safe_f32(data, oa+p_off+8) - safe_f32(data, ob+p_off+8)
                if dx*dx + dy*dy + dz*dz > threshold * threshold:
                    bad += 1
                    break
        return bad, max(total, 1)

    bad_full, n_full = count_bad(ic_full)
    bad_sub, n_sub = count_bad(ic_sub)

    if bad_full / n_full > bad_sub / n_sub + 0.02:
        return ic_sub
    return ic_full


def find_vb_by_inline_desc(data, idesc, covered_ranges):
    """Find a VB matching an inline descriptor (for s24/s36 without W=1.0 marker)."""
    vc, ic, stride = idesc['vc'], idesc['ic'], idesc['stride']
    fmt = STRIDE_FMT.get(stride)
    if not fmt:
        return None
    p_off = fmt['p']
    n_off = fmt['n']
    vb_size = vc * stride
    ib_size = ic * 2

    best = None
    best_score = -1

    for off in range(0, len(data) - vb_size - ib_size, 2):
        # Quick check: first vertex position
        px = f32(data, off + p_off)
        if math.isnan(px) or abs(px) > 300:
            continue
        # First vertex normal must be unit-ish
        nx = f32(data, off + n_off)
        ny = f32(data, off + n_off + 4)
        nz = f32(data, off + n_off + 8)
        nmag = nx*nx + ny*ny + nz*nz
        if nmag < 0.8 or nmag > 1.2:
            continue

        # Skip if overlapping with covered ranges
        overlap = False
        for cs, ce in covered_ranges:
            if off < ce and off + vb_size > cs:
                overlap = True
                break
        if overlap:
            continue

        # Verify IB immediately follows VB
        ib_off = off + vb_size
        if ib_off + ib_size > len(data):
            continue
        ib_ok = True
        for probe in [0, ic // 4, ic // 2, 3 * ic // 4, ic - 1]:
            if u16(data, ib_off + probe * 2) >= vc:
                ib_ok = False
                break
        if not ib_ok:
            continue

        # Score: check multiple vertices (sample 10) for position + normal validity
        score = 0
        step = max(1, vc // 10)
        for vi in range(0, vc, step):
            v_off = off + vi * stride
            vpx = f32(data, v_off + p_off)
            if math.isnan(vpx) or abs(vpx) > 300:
                break
            vnx = f32(data, v_off + n_off)
            vny = f32(data, v_off + n_off + 4)
            vnz = f32(data, v_off + n_off + 8)
            vm = vnx*vnx + vny*vny + vnz*vnz
            if vm < 0.8 or vm > 1.2:
                break
            score += 1

        min_score = min(8, max(3, vc // 5))  # lower threshold for small VBs
        if score >= min_score and score > best_score:
            best = off
            best_score = score
            if score >= 10:
                return best

    return best


def extract_heuristic(data, covered_ranges, inline_descs):
    """Extract VBs not covered by descriptors. Uses inline descriptors for exact ic."""
    vbs = find_vbs_heuristic(data)

    # Build lookup: vc → ic from inline descriptors
    ic_lookup = {}
    for d in inline_descs:
        key = (d['vc'], d['stride'])
        ic_lookup[key] = d['ic']

    # Filter out VBs overlapping with descriptor-covered ranges
    uncovered = []
    for vb in vbs:
        vb_start = vb['start']
        vb_end = vb_start + vb['count'] * vb['stride']
        overlap = False
        for cs, ce in covered_ranges:
            if vb_start < ce and vb_end > cs:
                overlap = True
                break
        if not overlap:
            uncovered.append(vb)

    parts = []
    for vb in uncovered:
        stride = vb['stride']
        vc = vb['count']
        vb_end = vb['start'] + vc * stride
        ib_off = vb_end

        # Use inline descriptor ic if available (exact, no PV contamination)
        ic = ic_lookup.get((vc, stride))
        if ic is not None:
            # Verify IB is valid at this position
            if ib_off + ic * 2 <= len(data):
                ok = True
                for probe in [0, ic // 4, ic // 2, ic - 1]:
                    if probe < ic and u16(data, ib_off + probe * 2) >= vc:
                        ok = False
                        break
                if not ok:
                    ic = None

        if ic is None:
            # Fallback: greedy scan
            total_ib = 0
            mx = 0
            while ib_off + total_ib * 2 + 2 <= len(data):
                idx = u16(data, ib_off + total_ib * 2)
                if idx >= vc:
                    break
                mx = max(mx, idx)
                total_ib += 1

            if total_ib < 12 or mx < vc * 0.3:
                continue

            ic = total_ib
            if total_ib % 3 != 0:
                sub = total_ib - vc if total_ib > vc else 0
                if sub >= 12 and sub % 3 == 0:
                    ic = sub
                else:
                    ic = (total_ib // 3) * 3

        # Decode: s72/s36/s24 use triangle list, s84 uses triangle strip
        if stride == 84:
            faces = decode_strip_faces(data, ib_off, ic, vc)
        else:
            faces = decode_list_faces(data, ib_off, ic, vc)

        verts, norms, uvs = read_vertices(data, vb['start'], vc, stride)
        faces = clean_faces(faces, verts, norms)

        if faces:
            parts.append({
                'vc': vc, 'verts': verts, 'norms': norms, 'uvs': uvs,
                'faces': faces, 'stride': stride, 'source': 'heur',
                'vb_off': vb['start'], 'vb_end': vb_end,
                'ib_end': ib_off + ic * 2,
            })

    return parts


# ── Main extraction ─────────────────────────────────────────

def extract_mdx(data, name):
    descs = find_all_descriptors(data)
    groups = group_descriptors(descs)
    inline_descs = find_inline_descriptors(data)

    all_parts = []
    covered = []

    # Phase 1: Descriptor-based extraction
    for group in groups:
        parts, all_ranges = extract_descriptor_group(data, group)
        for p in parts:
            all_parts.append(p)
        covered.extend(all_ranges)

    desc_count = len(all_parts)

    # Pre-scan W=1.0 VBs (ranges only, for Phase 2 false-positive prevention)
    w_vbs = find_vbs_heuristic(data)
    w_vb_ranges = []
    for vb in w_vbs:
        vs = vb['start']
        ve = vs + vb['count'] * vb['stride']
        w_vb_ranges.append((vs, ve))

    # Phase 2: Extract s24/s36 VBs from inline descriptors
    # s24: use w_vb_ranges to prevent false positives inside s72/s84 data
    # s36: don't use w_vb_ranges (s72 scanner misreads s36 data as s72)
    for idesc in inline_descs:
        stride = idesc['stride']
        if stride in (72, 84):
            continue
        vc, ic = idesc['vc'], idesc['ic']
        already = any(p['vc'] == vc and p['stride'] == stride for p in all_parts)
        if already:
            continue
        # s24: use w_vb_ranges to prevent false positives inside s72/s84 data
        # s36: don't block (s72 scanner misreads s36 data)
        phase2_covered = covered + (w_vb_ranges if stride == 24 else [])
        vb_off = find_vb_by_inline_desc(data, idesc, phase2_covered)
        if vb_off is None:
            continue
        ib_off = vb_off + vc * stride
        if ib_off + ic * 2 > len(data):
            continue
        verts, norms, uvs = read_vertices(data, vb_off, vc, stride)
        if stride == 84:
            faces = decode_strip_faces(data, ib_off, ic, vc)
        else:
            faces = decode_list_faces(data, ib_off, ic, vc)
        faces = clean_faces(faces, verts, norms)
        if faces:
            part = {
                'vc': vc, 'verts': verts, 'norms': norms, 'uvs': uvs,
                'faces': faces, 'stride': stride, 'source': 'idesc',
                'vb_off': vb_off, 'vb_end': vb_off + vc * stride,
            }
            all_parts.append(part)
            covered.append((part['vb_off'], part['vb_end']))

    # Phase 3: W=1.0 heuristic with inline descriptor ic lookup
    heur_parts = extract_heuristic(data, covered, inline_descs)
    heur_count = len(heur_parts)
    all_parts.extend(heur_parts)

    # Phase 4: Gap strip extraction — supplementary strip IB after list IB
    # gin7 layout: [VB][list_IB][strip_IB][next_VB]
    # The strip IB uses indices >= vc as strip restart markers.
    # Applies to all list-type parts (stride != 84) from any phase.
    all_vb_starts = sorted(set(
        [p['vb_off'] for p in all_parts] + [vb['start'] for vb in w_vbs]
    ))
    for part in all_parts:
        if part['stride'] == 84:
            continue  # s84 gap strip handled by decode_s84_mesh() with PV boundary
        ib_end = part.get('ib_end')
        if ib_end is None:
            continue
        # Find next VB start after this IB
        next_vb = len(data)
        for vs in all_vb_starts:
            if vs > ib_end + 10:
                next_vb = vs
                break
        gap = next_vb - ib_end
        if gap < 20:
            continue
        gap_faces = decode_gap_strip(data, ib_end, next_vb, part['vc'])
        gap_faces = clean_faces(gap_faces, part['verts'], part['norms'])
        if gap_faces:
            part['faces'].extend(gap_faces)

    desc_faces = sum(len(p['faces']) for p in all_parts[:desc_count])
    heur_faces = sum(len(p['faces']) for p in all_parts[desc_count:])

    return all_parts, desc_count, desc_faces, heur_count, heur_faces


# ── OBJ output ──────────────────────────────────────────────

def write_obj(parts, obj_path, name):
    with open(obj_path, 'w') as f:
        f.write(f'# {name}\n')
        f.write(f'# Parts: {len(parts)}\n')
        for p in parts:
            for v in p['verts']:
                f.write(f'v {v[0]:.6f} {v[1]:.6f} {v[2]:.6f}\n')
        for p in parts:
            for n in p['norms']:
                f.write(f'vn {n[0]:.6f} {n[1]:.6f} {n[2]:.6f}\n')
        for p in parts:
            for uv in p['uvs']:
                f.write(f'vt {uv[0]:.6f} {uv[1]:.6f}\n')
        vo = 0
        for pi, p in enumerate(parts):
            f.write(f'g part_{pi}_s{p["stride"]}_{p["source"]}\n')
            for i0, i1, i2 in p['faces']:
                a, b, c = i0 + vo + 1, i1 + vo + 1, i2 + vo + 1
                f.write(f'f {a}/{a}/{a} {b}/{b}/{b} {c}/{c}/{c}\n')
            vo += p['vc']
    tv = sum(p['vc'] for p in parts)
    tf = sum(len(p['faces']) for p in parts)
    return tv, tf


# ── CLI ─────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description='MDS-aware MDX Parser')
    parser.add_argument('input', type=Path)
    parser.add_argument('--out', type=Path, default=Path('/tmp/mdx_mds_out'))
    args = parser.parse_args()

    files = [args.input] if args.input.is_file() else sorted(args.input.glob('*_h.mdx'))
    args.out.mkdir(parents=True, exist_ok=True)

    ok = fail = total_v = total_f = 0
    for mdx in files:
        data = mdx.read_bytes()
        # Strip only the trailing _h_h or _h suffix, not _h within the name
        name = mdx.stem
        if name.endswith('_h_h'):
            name = name[:-4]
        elif name.endswith('_h'):
            name = name[:-2]
        parts, dc, df, hc, hf = extract_mdx(data, name)
        if not parts:
            print(f'  {name}: FAIL')
            fail += 1
            continue
        out_dir = args.out / name
        out_dir.mkdir(parents=True, exist_ok=True)
        tv, tf = write_obj(parts, out_dir / 'high.obj', name)
        src = []
        if dc: src.append(f'd:{dc}p/{df}f')
        if hc: src.append(f'h:{hc}p/{hf}f')
        print(f'  {name}: {tv}v {tf}f  [{", ".join(src)}]')
        ok += 1
        total_v += tv
        total_f += tf

    print(f'\n=== {ok}/{ok + fail} OK, {total_v:,}v {total_f:,}f ===')


if __name__ == '__main__':
    main()
