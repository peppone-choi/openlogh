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
from collections import Counter
from dataclasses import dataclass, field
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
FALLBACK_SUFFIXES = {'h', 'm', 'l'}
ENABLE_GAP_EXTRACTION = False


@dataclass
class ExtractionStats:
    desc_count: int = 0
    desc_faces: int = 0
    heur_count: int = 0
    heur_faces: int = 0
    descriptor_records: int = 0
    inline_records: int = 0


@dataclass
class ExtractionRun:
    data: bytes
    parts: list[dict] = field(default_factory=list)
    covered_ranges: list[tuple[int, int]] = field(default_factory=list)
    descriptor_groups: list[list[dict]] = field(default_factory=list)
    inline_descs: list[dict] = field(default_factory=list)
    heuristic_vbs: list[dict] = field(default_factory=list)
    stats: ExtractionStats = field(default_factory=ExtractionStats)

    def add_part(self, part, *, exact=True, cover_range=None):
        self.parts.append(part)
        if cover_range is None:
            vb_off = part.get('vb_off')
            vb_end = part.get('vb_end')
            if vb_off is not None and vb_end is not None:
                cover_range = (vb_off, vb_end)
        if cover_range is not None:
            self.covered_ranges.append(cover_range)
        faces = len(part['faces'])
        if exact:
            self.stats.desc_count += 1
            self.stats.desc_faces += faces
        else:
            self.stats.heur_count += 1
            self.stats.heur_faces += faces


# ── TOC parsing ─────────────────────────────────────────────

def parse_toc(data):
    entries = []
    for i in range(12):
        off, magic, count = struct.unpack_from('<HHH', data, i * 8)
        extra = struct.unpack_from('<H', data, i * 8 + 6)[0]
        entries.append({'idx': i, 'off': off, 'magic': magic, 'count': count, 'extra': extra})
    return entries


def toc8_data_start(data):
    toc = parse_toc(data)
    if len(toc) <= 8:
        return 0
    start = toc[8]['off']
    if start < 0 or start >= len(data):
        return 0
    return start


def find_loose_descriptor_start(data):
    """Find the earliest strong descriptor-like record after TOC8."""
    start = toc8_data_start(data)
    best = None
    for align in (2, 4):
        for off in range(start, len(data) - 40, align):
            marker = u32(data, off + 24)
            if marker not in MARKER_STRIDE:
                continue
            stride = u32(data, off + 32)
            prim = u32(data, off)
            vc = u32(data, off + 8)
            ic = u32(data, off + 16)
            pad = u32(data, off + 28)
            flags = 0
            if stride == MARKER_STRIDE[marker]:
                flags += 1
            if pad == 0:
                flags += 1
            if prim <= 4:
                flags += 1
            if 1 <= vc <= 200000:
                flags += 1
            if 0 <= ic <= 2000000:
                flags += 1
            if flags < 5:
                continue
            cand = {'off': off, 'score': flags, 'vc': vc, 'ic': ic, 'stride': stride}
            if best is None or cand['off'] < best['off']:
                best = cand
    return best


def suffix_of_name(name):
    if '_' not in name:
        return ''
    return name.rsplit('_', 1)[-1]


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


def filter_inline_descriptor_aliases(descs, inline_descs):
    aliases = {
        (desc['off'] + 4, desc['vc'], desc['ic'], desc['stride'])
        for desc in descs
    }
    return [
        desc for desc in inline_descs
        if (desc['off'], desc['vc'], desc['ic'], desc['stride']) not in aliases
    ]


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


def position_cloud_profile(verts):
    if not verts:
        return None
    xs = [v[0] for v in verts]
    ys = [v[1] for v in verts]
    zs = [v[2] for v in verts]
    dx = max(xs) - min(xs)
    dy = max(ys) - min(ys)
    dz = max(zs) - min(zs)
    dims = sorted([abs(dx), abs(dy), abs(dz)])
    cx = sum(xs) / len(xs)
    cy = sum(ys) / len(ys)
    cz = sum(zs) / len(zs)
    rs = [math.sqrt((x - cx) ** 2 + (y - cy) ** 2 + (z - cz) ** 2) for x, y, z in verts]
    r_mean = sum(rs) / len(rs)
    r_std = math.sqrt(sum((r - r_mean) ** 2 for r in rs) / len(rs))
    return {
        'dx': dx, 'dy': dy, 'dz': dz,
        'ratio': dims[-1] / max(dims[0], 1e-6),
        'center_mag': math.sqrt(cx * cx + cy * cy + cz * cz),
        'r_mean': r_mean,
        'r_std': r_std,
    }


def looks_like_unit_sphere_false_position(verts, stride):
    if stride != 84 or len(verts) < 128:
        return False
    profile = position_cloud_profile(verts)
    if profile is None:
        return False
    return (
        profile['ratio'] < 1.25 and
        profile['center_mag'] < 0.4 and
        1.6 <= profile['dx'] <= 2.4 and
        1.6 <= profile['dy'] <= 2.4 and
        1.6 <= profile['dz'] <= 2.4 and
        0.8 <= profile['r_mean'] <= 1.1 and
        profile['r_std'] < 0.18
    )


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


def pick_best_face_topology(verts, norms, list_faces, strip_faces):
    list_clean = clean_faces(list_faces, verts, norms)
    strip_clean = clean_faces(strip_faces, verts, norms)

    def score(faces):
        if not faces:
            return (-1.0, 0, 0.0)
        coverage = face_vertex_coverage(faces, len(verts))
        return (len(faces) * coverage, len(faces), coverage)

    return (list_clean, 'list') if score(list_clean) > score(strip_clean) else (strip_clean, 'strip')


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
    if looks_like_unit_sphere_false_position(verts, stride):
        return None

    # s84 topology is not reliable from prim alone.
    faces, topo = pick_best_face_topology(
        verts,
        norms,
        decode_list_faces(data, ib_off, ic, vc),
        decode_strip_faces(data, ib_off, ic, vc),
    )

    # Find true strip end (beyond ic, before PV)
    # Gap strip faces are filtered with long-edge check to prevent spaghetti
    strip_end = ib_off + ic * 2
    if ENABLE_GAP_EXTRACTION and topo == 'strip':
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
        'base_faces': list(faces), 'gap_faces': [],
        'vb_off': vb_off, 'vb_end': vb_off + vc * stride,
        'ib_end': strip_end, 'prim': desc['prim'], 'topology': topo,
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


def face_vertex_coverage(faces, vc):
    if vc <= 0 or not faces:
        return 0.0
    used = set()
    for i0, i1, i2 in faces:
        used.add(i0)
        used.add(i1)
        used.add(i2)
    return len(used) / vc


# ── Descriptor-based extraction ─────────────────────────────

def find_vb_for_descriptor(data, desc):
    """Find the best VB matching a descriptor by W=1.0 scanning."""
    vc, stride = desc['vc'], desc['stride']
    fmt = STRIDE_FMT[stride]
    w_off = fmt['w']
    p_off = fmt['p']
    if w_off < 0:
        return None  # stride-24 has no W field

    search_start = max(0, desc['off'] - 1024)
    search_end = min(len(data), desc['off'] + vc * stride * 3)
    pos = search_start
    best_vb = None
    best_score = -1.0

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
            score = 1.0
            ib_off = vb + vc * stride
            ic = desc['ic']
            if ib_off + ic * 2 <= len(data):
                probes_ok = 0
                for probe in [0, ic // 4, ic // 2, ic - 1]:
                    if 0 <= probe < ic and u16(data, ib_off + probe * 2) < vc:
                        probes_ok += 1
                score += probes_ok / 4.0
            if score > best_score:
                best_score = score
                best_vb = vb
        pos = test if count > 0 else w_pos + 4
    return best_vb


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
            'base_faces': list(faces), 'gap_faces': [],
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
    best_score = None

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

        min_score = min(8, max(3, vc // 5))
        if score < min_score:
            continue

        distance = abs(off - idesc['off'])
        after_bonus = 0 if off >= idesc['off'] else 4096
        cand_score = (score, -(distance + after_bonus))
        if best_score is None or cand_score > best_score:
            best = off
            best_score = cand_score

    return best


def find_best_inline_s84_part(data, idesc, covered_ranges):
    vc, stride = idesc['vc'], idesc['stride']
    if stride != 84:
        return None, None
    search_start = max(0, idesc['off'] - 8192)
    search_end = min(len(data), idesc['off'] + 65536)
    pos = search_start
    best_part = None
    best_vb = None
    best_score = None

    while pos < search_end - stride:
        w_pos = data.find(W_ONE, pos, search_end)
        if w_pos == -1:
            break
        vb = w_pos
        pos = w_pos + 4
        overlap = False
        vb_end = vb + vc * stride
        for cs, ce in covered_ranges:
            if vb < ce and vb_end > cs:
                overlap = True
                break
        if overlap:
            continue
        part = decode_inline_part(data, idesc, vb)
        if part is None:
            continue
        coverage = face_vertex_coverage(part['faces'], vc)
        score = (len(part['faces']) * coverage, len(part['faces']), -abs(vb - idesc['off']))
        if best_score is None or score > best_score:
            best_score = score
            best_part = part
            best_vb = vb
    return best_vb, best_part


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
        if vc < 64:
            continue
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
        verts, norms, uvs = read_vertices(data, vb['start'], vc, stride)
        if looks_like_unit_sphere_false_position(verts, stride):
            continue
        if stride == 84:
            faces, topo = pick_best_face_topology(
                verts,
                norms,
                decode_list_faces(data, ib_off, ic, vc),
                decode_strip_faces(data, ib_off, ic, vc),
            )
        else:
            faces = clean_faces(decode_list_faces(data, ib_off, ic, vc), verts, norms)
            topo = 'list'
        coverage = face_vertex_coverage(faces, vc)

        min_faces = max(12, vc // 8)
        if faces and len(faces) >= min_faces and coverage >= 0.2:
            parts.append({
                'vc': vc, 'verts': verts, 'norms': norms, 'uvs': uvs,
                'faces': faces, 'stride': stride, 'source': 'heur',
                'base_faces': list(faces), 'gap_faces': [],
                'vb_off': vb['start'], 'vb_end': vb_end,
                'ib_end': ib_off + ic * 2,
                'topology': topo,
            })

    return parts


# ── Main extraction ─────────────────────────────────────────

def rebase_parts(parts, base_off):
    if base_off == 0:
        return parts
    rebased = []
    for part in parts:
        p = dict(part)
        for key in ('vb_off', 'vb_end', 'ib_end'):
            if key in p:
                p[key] += base_off
        rebased.append(p)
    return rebased


def create_extraction_run(data):
    run = ExtractionRun(data=data)
    descs = find_all_descriptors(data)
    run.descriptor_groups = group_descriptors(descs)
    run.inline_descs = filter_inline_descriptor_aliases(descs, find_inline_descriptors(data))
    run.heuristic_vbs = find_vbs_heuristic(data)
    run.stats.descriptor_records = len(descs)
    run.stats.inline_records = len(run.inline_descs)
    return run


def has_equivalent_exact_part(parts, vc, stride, ic):
    min_faces = max(1, ic // 3 - 2)
    for part in parts:
        if part['source'] == 'heur':
            continue
        if part['vc'] == vc and part['stride'] == stride and len(part.get('base_faces', part['faces'])) >= min_faces:
            return True
    return False


def collect_descriptor_parts(run):
    for group in run.descriptor_groups:
        parts, all_ranges = extract_descriptor_group(run.data, group)
        for part in parts:
            run.parts.append(part)
            run.stats.desc_count += 1
            run.stats.desc_faces += len(part['faces'])
        run.covered_ranges.extend(all_ranges)


def heuristic_vb_ranges(run):
    ranges = []
    for vb in run.heuristic_vbs:
        vs = vb['start']
        ve = vs + vb['count'] * vb['stride']
        ranges.append((vs, ve))
    return ranges


def decode_inline_part(data, idesc, vb_off):
    vc, ic, stride = idesc['vc'], idesc['ic'], idesc['stride']
    ib_off = vb_off + vc * stride
    if ib_off + ic * 2 > len(data):
        return None
    verts, norms, uvs = read_vertices(data, vb_off, vc, stride)
    if looks_like_unit_sphere_false_position(verts, stride):
        return None
    if stride == 84:
        faces, topo = pick_best_face_topology(
            verts,
            norms,
            decode_list_faces(data, ib_off, ic, vc),
            decode_strip_faces(data, ib_off, ic, vc),
        )
    else:
        faces = clean_faces(decode_list_faces(data, ib_off, ic, vc), verts, norms)
        topo = 'list'
    if not faces:
        return None
    return {
        'vc': vc, 'verts': verts, 'norms': norms, 'uvs': uvs,
        'faces': faces, 'stride': stride, 'source': 'idesc',
        'base_faces': list(faces), 'gap_faces': [],
        'vb_off': vb_off, 'vb_end': vb_off + vc * stride,
        'ib_end': ib_off + ic * 2,
        'topology': topo,
    }


def nearby_inline_descriptor(run, vb_start, stride, radius=32768):
    for desc in run.inline_descs:
        if desc['stride'] != stride:
            continue
        off = desc['off']
        if abs(off - vb_start) <= radius:
            return desc
    return None


def collect_inline_exact_parts(run):
    w_ranges = heuristic_vb_ranges(run)
    for idesc in run.inline_descs:
        stride = idesc['stride']
        vc, ic = idesc['vc'], idesc['ic']
        if has_equivalent_exact_part(run.parts, vc, stride, ic):
            continue
        covered = list(run.covered_ranges)
        if stride == 24:
            covered.extend(w_ranges)
        vb_off = find_vb_by_inline_desc(run.data, idesc, covered)
        if vb_off is None:
            vb_off, part = find_best_inline_s84_part(run.data, idesc, covered)
        else:
            part = decode_inline_part(run.data, idesc, vb_off)
            if part is not None and stride == 84:
                coverage = face_vertex_coverage(part['faces'], vc)
                if coverage < 0.4 or len(part['faces']) < max(128, vc // 6):
                    rescue_vb, rescue_part = find_best_inline_s84_part(run.data, idesc, covered)
                    if rescue_part is not None and len(rescue_part['faces']) > len(part['faces']):
                        vb_off, part = rescue_vb, rescue_part
        if part is None:
            continue
        run.add_part(part, exact=True)


def exact_part_score(part):
    source_bonus = 2 if part['source'] == 'desc' else 1
    face_count = len(part.get('base_faces', part['faces']))
    return (face_count, source_bonus, part['vc'])


def exact_overlap_ratio(a, b):
    a0, a1 = a.get('vb_off'), a.get('vb_end')
    b0, b1 = b.get('vb_off'), b.get('vb_end')
    if None in (a0, a1, b0, b1):
        return 0.0
    overlap = max(0, min(a1, b1) - max(a0, b0))
    if overlap <= 0:
        return 0.0
    span = max(min(a1 - a0, b1 - b0), 1)
    return overlap / span


def dedupe_exact_parts(run):
    exact_parts = [part for part in run.parts if part['source'] != 'heur']
    heur_parts = [part for part in run.parts if part['source'] == 'heur']
    removed = set()
    for i, left in enumerate(exact_parts):
        if i in removed:
            continue
        for j in range(i + 1, len(exact_parts)):
            if j in removed:
                continue
            right = exact_parts[j]
            overlap = exact_overlap_ratio(left, right)
            same_span = left.get('vb_off') == right.get('vb_off') and left.get('vb_end') == right.get('vb_end')
            same_stride = left['stride'] == right['stride']
            if overlap < 0.95 and not same_span:
                continue
            if not same_stride and not same_span:
                continue
            left_score = exact_part_score(left)
            right_score = exact_part_score(right)
            if right_score > left_score:
                removed.add(i)
                break
            removed.add(j)
    kept_exact = [part for idx, part in enumerate(exact_parts) if idx not in removed]
    if len(kept_exact) == len(exact_parts):
        return
    run.parts = []
    run.covered_ranges = []
    run.stats.desc_count = 0
    run.stats.desc_faces = 0
    run.stats.heur_count = 0
    run.stats.heur_faces = 0
    for part in kept_exact:
        run.add_part(part, exact=True)
    for part in heur_parts:
        run.add_part(part, exact=False)


def collect_heuristic_parts(run):
    for part in extract_heuristic(run.data, run.covered_ranges, run.inline_descs):
        if part['stride'] == 84:
            nearby = nearby_inline_descriptor(run, part['vb_off'], part['stride'])
            if nearby is not None and nearby['vc'] < part['vc'] * 0.75:
                continue
        run.add_part(part, exact=False)


def apply_gap_pass(run):
    if not ENABLE_GAP_EXTRACTION:
        return
    all_vb_starts = sorted(set(
        [p['vb_off'] for p in run.parts] + [vb['start'] for vb in run.heuristic_vbs]
    ))
    for part in run.parts:
        if part['stride'] == 84:
            continue
        ib_end = part.get('ib_end')
        if ib_end is None:
            continue
        next_vb = len(run.data)
        for vs in all_vb_starts:
            if vs > ib_end + 10:
                next_vb = vs
                break
        if next_vb - ib_end < 20:
            continue
        gap_faces = decode_gap_strip(run.data, ib_end, next_vb, part['vc'])
        gap_faces = clean_faces(gap_faces, part['verts'], part['norms'])
        if not gap_faces:
            continue
        part['gap_faces'] = list(gap_faces)
        part['faces'].extend(gap_faces)
        if part['source'] == 'heur':
            run.stats.heur_faces += len(gap_faces)
        else:
            run.stats.desc_faces += len(gap_faces)


def execute_extraction_passes(data):
    run = create_extraction_run(data)
    collect_descriptor_parts(run)
    collect_inline_exact_parts(run)
    dedupe_exact_parts(run)
    collect_heuristic_parts(run)
    apply_gap_pass(run)
    return run


def extract_mdx_core(data):
    run = execute_extraction_passes(data)
    stats = run.stats
    return (
        run.parts,
        stats.desc_count,
        stats.desc_faces,
        stats.heur_count,
        stats.heur_faces,
        stats.descriptor_records,
        stats.inline_records,
    )


def extract_mdx(data, name):
    parts, dc, df, hc, hf, desc_total, inline_total = extract_mdx_core(data)
    if desc_total > 0:
        return parts, dc, df, hc, hf
    if suffix_of_name(name) not in FALLBACK_SUFFIXES:
        return parts, dc, df, hc, hf

    fallback = find_loose_descriptor_start(data)
    if fallback is None or fallback['off'] <= 0:
        return parts, dc, df, hc, hf

    sliced = data[fallback['off']:]
    fb_parts, fb_dc, fb_df, fb_hc, fb_hf, fb_desc_total, fb_inline_total = extract_mdx_core(sliced)
    if fb_desc_total == 0 and fb_inline_total == 0 and not fb_parts:
        return parts, dc, df, hc, hf

    cur_faces = sum(len(p['faces']) for p in parts)
    fb_faces = sum(len(p['faces']) for p in fb_parts)
    improved_descriptors = fb_desc_total > desc_total
    keeps_enough_faces = cur_faces == 0 or fb_faces >= cur_faces * 0.6
    if not improved_descriptors or not keeps_enough_faces:
        return parts, dc, df, hc, hf

    fb_parts = rebase_parts(fb_parts, fallback['off'])
    return fb_parts, fb_dc, fb_df, fb_hc, fb_hf


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


def face_edge_counts(faces):
    counts = Counter()
    for i0, i1, i2 in faces:
        counts[(min(i0, i1), max(i0, i1))] += 1
        counts[(min(i1, i2), max(i1, i2))] += 1
        counts[(min(i2, i0), max(i2, i0))] += 1
    return counts


def face_used_vertices(faces):
    used = set()
    for i0, i1, i2 in faces:
        used.add(i0)
        used.add(i1)
        used.add(i2)
    return used


def classify_gap_behavior(part):
    base_faces = list(part.get('base_faces', part['faces']))
    gap_faces = list(part.get('gap_faces', []))
    if not base_faces or not gap_faces:
        return None

    base_used = face_used_vertices(base_faces)
    gap_used = face_used_vertices(gap_faces)
    shared_used = base_used & gap_used

    base_edge_counts = face_edge_counts(base_faces)
    gap_edge_counts = face_edge_counts(gap_faces)
    base_edges = set(base_edge_counts)
    gap_edges = set(gap_edge_counts)
    shared_edges = base_edges & gap_edges
    base_boundary_edges = {edge for edge, count in base_edge_counts.items() if count == 1}
    boundary_touch = gap_edges & base_boundary_edges

    gap_vertex_overlap = len(shared_used) / max(len(gap_used), 1)
    gap_edge_overlap = len(shared_edges) / max(len(gap_edges), 1)
    gap_boundary_touch = len(boundary_touch) / max(len(gap_edges), 1)

    label = 'ambiguous'
    if gap_edge_overlap >= 0.45:
        label = 'duplicate_like'
    elif gap_vertex_overlap >= 0.80 and gap_edge_overlap < 0.45:
        label = 'replace_like'
    elif gap_vertex_overlap < 0.80 and gap_boundary_touch >= 0.05:
        label = 'additive_like'
    elif gap_vertex_overlap >= 0.65 and gap_boundary_touch >= 0.05:
        label = 'mixed_like'

    return {
        'label': label,
        'gap_vertex_overlap': gap_vertex_overlap,
        'gap_edge_overlap': gap_edge_overlap,
        'gap_boundary_touch': gap_boundary_touch,
        'shared_vertices': len(shared_used),
        'gap_vertices': len(gap_used),
        'shared_edges': len(shared_edges),
        'gap_edges': len(gap_edges),
    }


def replacement_faces_for_part(part):
    base_faces = list(part.get('base_faces', part['faces']))
    gap_faces = list(part.get('gap_faces', []))
    if not gap_faces:
        return base_faces
    profile = classify_gap_behavior(part)
    if profile is None:
        return base_faces
    label = profile['label']
    if label in {'replace_like', 'duplicate_like'}:
        return gap_faces
    if label == 'additive_like':
        return base_faces + gap_faces
    if label == 'mixed_like':
        return gap_faces if len(gap_faces) >= len(base_faces) * 0.35 else base_faces + gap_faces
    return base_faces


def select_export_parts(parts, mode='full'):
    selected = []
    for part in parts:
        base_faces = list(part.get('base_faces', part['faces']))
        gap_faces = list(part.get('gap_faces', []))
        faces = None
        if mode == 'full':
            faces = list(part['faces'])
        elif mode == 'exact':
            if part['source'] == 'heur':
                continue
            faces = base_faces
        elif mode == 'heur':
            if part['source'] != 'heur':
                continue
            faces = base_faces
        elif mode == 'gap':
            faces = gap_faces
        elif mode == 'replace':
            if part['source'] == 'heur':
                continue
            faces = replacement_faces_for_part(part)
        else:
            raise ValueError(f'unknown export mode: {mode}')
        if not faces:
            continue
        p = dict(part)
        p['faces'] = faces
        selected.append(p)
    return selected


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
