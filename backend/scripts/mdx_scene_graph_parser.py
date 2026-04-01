#!/usr/bin/env python3
"""
MDX Scene Graph Parser -- descriptor-driven extraction with combined VB pool.

Key improvements over mdx_extract_final.py and mdx_extract_v2.py:
  1. Uses descriptor ic field for EXACT index count (no heuristic IB boundary)
  2. Cross-VB indices resolved via combined vertex pool base offsets
  3. After-descriptor IB discovery for additional mesh parts
  4. Strip vs list auto-detection with winding correction

VB Descriptor layout (40 bytes):
  +0x00: u32 type        (0=stride-72, 1=stride-84)
  +0x04: u32 ptr_vb      (runtime address)
  +0x08: u32 vertex_count (GROUND TRUTH)
  +0x0C: u32 ptr_ib      (runtime address)
  +0x10: u32 index_count  (GROUND TRUTH -- KEY FIX)
  +0x14: u32 ptr_???     (runtime address)
  +0x18: u32 marker      (74002=stride-72, 336402=stride-84)
  +0x1C: u32 padding     (0)
  +0x20: u32 stride      (72 or 84)
  +0x24: u32 type2       (1 or 2 -- possibly strip vs list)

Vertex layouts:
  stride-72: pos@0(3f), nrm@12(3f), uv@24(2f), W=1.0@32
  stride-84: W=1.0@0, nrm@16(3f), pos@52(3f), uv@68(2f)

Usage:
  python mdx_scene_graph_parser.py <mdx_file_or_dir> [--out DIR] [--no-mirror]
                                   [--verbose] [--info] [--compare DIR]
"""

from __future__ import annotations

import argparse
import math
import os
import struct
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional


# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

DESCRIPTOR_SIZE = 40
MARKER_S72 = 74002
MARKER_S84 = 336402
STRIDE_72 = 72
STRIDE_84 = 84
MIN_VERTICES = 3
MIN_INDICES = 3
MAX_VERTICES = 100_000
MAX_INDICES = 500_000
W_ONE = struct.pack("<f", 1.0)
W_MIN_RUN = 10


# ---------------------------------------------------------------------------
# Data classes
# ---------------------------------------------------------------------------

@dataclass
class Descriptor:
    offset: int
    vb_type: int
    vertex_count: int
    index_count: int
    marker: int
    stride: int
    type2: int

    @property
    def w_offset(self) -> int:
        return 0 if self.stride == STRIDE_84 else 32

    @property
    def pos_offset(self) -> int:
        return 52 if self.stride == STRIDE_84 else 0

    @property
    def nrm_offset(self) -> int:
        return 16 if self.stride == STRIDE_84 else 12

    @property
    def uv_offset(self) -> int:
        return 68 if self.stride == STRIDE_84 else 24


@dataclass
class VBRegion:
    """A vertex buffer region matched to a descriptor."""
    vb_start: int
    vertex_count: int
    stride: int
    descriptor: Descriptor
    # Populated after all VBs discovered
    ib_start: int = 0
    index_count: int = 0
    # Combined pool base (sum of all preceding VB vertex counts)
    pool_base: int = 0

    @property
    def vb_end(self) -> int:
        return self.vb_start + self.vertex_count * self.stride

    @property
    def w_offset(self) -> int:
        return self.descriptor.w_offset

    @property
    def pos_offset(self) -> int:
        return self.descriptor.pos_offset

    @property
    def nrm_offset(self) -> int:
        return self.descriptor.nrm_offset

    @property
    def uv_offset(self) -> int:
        return self.descriptor.uv_offset


@dataclass
class MeshPart:
    """A decoded mesh part with vertices and faces."""
    vertices: list[tuple[float, float, float]]
    normals: list[tuple[float, float, float]]
    uvs: list[tuple[float, float]]
    faces: list[tuple[int, int, int]]
    source: str  # descriptor ID or extra-IB label
    pool_base: int = 0  # offset of this part's VB within the combined pool


# ---------------------------------------------------------------------------
# Low-level helpers
# ---------------------------------------------------------------------------

def f32(data: bytes, off: int) -> float:
    return struct.unpack_from("<f", data, off)[0]


def u16(data: bytes, off: int) -> int:
    return struct.unpack_from("<H", data, off)[0]


def u32(data: bytes, off: int) -> int:
    return struct.unpack_from("<I", data, off)[0]


# ---------------------------------------------------------------------------
# Step 1: Descriptor discovery
# ---------------------------------------------------------------------------

def scan_descriptors(data: bytes) -> list[Descriptor]:
    """Scan entire file for 40-byte VB descriptors by matching marker+stride."""
    descriptors: list[Descriptor] = []
    limit = len(data) - DESCRIPTOR_SIZE
    for offset in range(0, limit, 4):
        marker = u32(data, offset + 0x18)
        if marker not in (MARKER_S72, MARKER_S84):
            continue
        stride = u32(data, offset + 0x20)
        if stride not in (STRIDE_72, STRIDE_84):
            continue
        # Validate marker-stride consistency
        if marker == MARKER_S72 and stride != STRIDE_72:
            continue
        if marker == MARKER_S84 and stride != STRIDE_84:
            continue

        vb_type = u32(data, offset)
        vertex_count = u32(data, offset + 0x08)
        index_count = u32(data, offset + 0x10)
        type2 = u32(data, offset + 0x24)

        if vertex_count < MIN_VERTICES or vertex_count > MAX_VERTICES:
            continue
        if index_count < MIN_INDICES or index_count > MAX_INDICES:
            continue

        descriptors.append(Descriptor(
            offset=offset,
            vb_type=vb_type,
            vertex_count=vertex_count,
            index_count=index_count,
            marker=marker,
            stride=stride,
            type2=type2,
        ))
    return descriptors


def deduplicate_descriptors(
    descriptors: list[Descriptor],
    w_runs_by_stride: dict[int, list[tuple[int, int]]],
) -> list[Descriptor]:
    """Deduplicate overlapping descriptors (within 40 bytes)."""
    if not descriptors:
        return []
    sorted_descs = sorted(descriptors, key=lambda d: d.offset)
    result: list[Descriptor] = [sorted_descs[0]]
    for desc in sorted_descs[1:]:
        if desc.offset < result[-1].offset + DESCRIPTOR_SIZE:
            # Overlapping -- keep whichever has a matching W run
            prev = result[-1]
            prev_runs = w_runs_by_stride.get(prev.stride, [])
            curr_runs = w_runs_by_stride.get(desc.stride, [])
            prev_match = any(wc == prev.vertex_count for _, wc in prev_runs)
            curr_match = any(wc == desc.vertex_count for _, wc in curr_runs)
            if curr_match and not prev_match:
                result[-1] = desc
        else:
            result.append(desc)
    return result


# ---------------------------------------------------------------------------
# Step 2: VB location via W=1.0 heuristic
# ---------------------------------------------------------------------------

def find_w_runs(data: bytes, stride: int, min_run: int = W_MIN_RUN) -> list[tuple[int, int]]:
    """Find runs of W=1.0 at the expected offset for a given stride.

    Uses data.find(W_ONE) byte search (from mdx_extract_final.py) to find
    W=1.0 at arbitrary positions, then checks backward to compute VB start
    and forward to count consecutive valid vertices. This handles VBs at
    non-aligned file offsets that stride-stepping would miss.
    """
    if stride == STRIDE_84:
        w_off = 0
        p_off = 52
    else:
        w_off = 32
        p_off = 0

    runs: list[tuple[int, int]] = []
    pos = 0
    while pos < len(data) - stride:
        w_pos = data.find(W_ONE, pos)
        if w_pos == -1:
            break
        # Compute candidate VB start (vertex containing this W marker)
        vb_start = w_pos - w_off
        if vb_start < 0 or vb_start + p_off + 12 > len(data):
            pos = w_pos + 1
            continue
        # Quick validity check on position value
        px = f32(data, vb_start + p_off)
        if math.isnan(px) or abs(px) > 300:
            pos = w_pos + 1
            continue
        # Count consecutive valid vertices forward
        vc = 0
        test = vb_start
        while test + stride <= len(data):
            w = f32(data, test + w_off)
            if abs(w - 1.0) > 0.05:
                break
            tpx = f32(data, test + p_off)
            if math.isnan(tpx) or abs(tpx) > 300:
                break
            vc += 1
            test += stride
        if vc >= min_run:
            runs.append((vb_start, vc))
            pos = test
        else:
            pos = w_pos + 4
    return runs


def vb_looks_valid(
    data: bytes, vb_start: int, vertex_count: int, stride: int,
    sample_count: int = 8,
) -> bool:
    """Quick plausibility check on sampled vertices."""
    if vb_start + vertex_count * stride > len(data):
        return False
    pos_offset = 52 if stride == STRIDE_84 else 0
    w_off = 0 if stride == STRIDE_84 else 32
    w_hits = 0
    step = max(1, vertex_count // sample_count)
    samples = 0
    for i in range(0, vertex_count, step):
        base = vb_start + i * stride
        if base + stride > len(data):
            break
        samples += 1
        px, py, pz = struct.unpack_from("<3f", data, base + pos_offset)
        if any(math.isnan(v) or math.isinf(v) for v in (px, py, pz)):
            return False
        if any(abs(v) > 500 for v in (px, py, pz)):
            return False
        w = f32(data, base + w_off)
        if w == 1.0:
            w_hits += 1
    return samples > 0 and w_hits >= max(1, samples // 4)


def match_descriptors_to_vbs(
    data: bytes,
    descriptors: list[Descriptor],
) -> list[VBRegion]:
    """Match each descriptor to its VB data using W=1.0 heuristic."""
    w_runs_by_stride: dict[int, list[tuple[int, int]]] = {
        STRIDE_72: find_w_runs(data, STRIDE_72),
        STRIDE_84: find_w_runs(data, STRIDE_84),
    }

    deduped = deduplicate_descriptors(descriptors, w_runs_by_stride)

    regions: list[VBRegion] = []
    used_w_runs: set[tuple[int, int, int]] = set()  # (start, count, stride)

    for desc in deduped:
        w_runs = w_runs_by_stride[desc.stride]

        # Try exact match first
        best_match: Optional[tuple[int, int]] = None
        for w_start, w_count in w_runs:
            key = (w_start, w_count, desc.stride)
            if key in used_w_runs:
                continue
            if w_count == desc.vertex_count:
                best_match = (w_start, w_count)
                break

        # Fallback: W run >= descriptor count (use descriptor count)
        if best_match is None:
            for w_start, w_count in w_runs:
                key = (w_start, w_count, desc.stride)
                if key in used_w_runs:
                    continue
                if w_count >= desc.vertex_count:
                    best_match = (w_start, desc.vertex_count)
                    break

        if best_match is not None:
            vb_start, vc = best_match
            regions.append(VBRegion(
                vb_start=vb_start,
                vertex_count=vc,
                stride=desc.stride,
                descriptor=desc,
                index_count=desc.index_count,
            ))
            # Mark W run used
            for ws, wc in w_runs:
                if ws == best_match[0]:
                    used_w_runs.add((ws, wc, desc.stride))
                    break
        else:
            # No W run -- try descriptor offset + 40 as VB start
            vb_start_candidate = desc.offset + DESCRIPTOR_SIZE
            if vb_looks_valid(data, vb_start_candidate, desc.vertex_count, desc.stride):
                regions.append(VBRegion(
                    vb_start=vb_start_candidate,
                    vertex_count=desc.vertex_count,
                    stride=desc.stride,
                    descriptor=desc,
                    index_count=desc.index_count,
                ))

    # Also add unmatched W runs as descriptor-less VBs (heuristic fallback)
    for stride_val, w_runs in w_runs_by_stride.items():
        for w_start, w_count in w_runs:
            key = (w_start, w_count, stride_val)
            if key in used_w_runs:
                continue
            # Check this W run doesn't overlap any existing region
            w_end = w_start + w_count * stride_val
            overlaps = False
            for r in regions:
                r_end = r.vb_start + r.vertex_count * r.stride
                if w_start < r_end and w_end > r.vb_start:
                    overlaps = True
                    break
            if not overlaps and w_count >= MIN_VERTICES:
                # Create a synthetic descriptor for this W run
                synth_desc = Descriptor(
                    offset=-1,
                    vb_type=0 if stride_val == STRIDE_72 else 1,
                    vertex_count=w_count,
                    index_count=0,  # unknown -- will use heuristic IB
                    marker=MARKER_S72 if stride_val == STRIDE_72 else MARKER_S84,
                    stride=stride_val,
                    type2=1,
                )
                regions.append(VBRegion(
                    vb_start=w_start,
                    vertex_count=w_count,
                    stride=stride_val,
                    descriptor=synth_desc,
                    index_count=0,
                ))

    # Sort by file offset
    regions.sort(key=lambda r: r.vb_start)

    # Deduplicate overlapping regions (keep larger)
    deduped_regions: list[VBRegion] = []
    for r in regions:
        r_end = r.vb_start + r.vertex_count * r.stride
        replaced = False
        for i, existing in enumerate(deduped_regions):
            e_end = existing.vb_start + existing.vertex_count * existing.stride
            if r.vb_start < e_end and r_end > existing.vb_start:
                if r.vertex_count > existing.vertex_count:
                    deduped_regions[i] = r
                replaced = True
                break
        if not replaced:
            deduped_regions.append(r)

    # Assign combined pool bases
    pool_base = 0
    for r in deduped_regions:
        r.pool_base = pool_base
        pool_base += r.vertex_count

    # Set IB start positions
    for r in deduped_regions:
        r.ib_start = r.vb_end

    return deduped_regions


# ---------------------------------------------------------------------------
# Vertex reading
# ---------------------------------------------------------------------------

def read_vertices(data: bytes, region: VBRegion) -> tuple[
    list[tuple[float, float, float]],
    list[tuple[float, float, float]],
    list[tuple[float, float]],
]:
    """Read position, normal, UV data from a VB region."""
    verts: list[tuple[float, float, float]] = []
    norms: list[tuple[float, float, float]] = []
    uvs: list[tuple[float, float]] = []
    for i in range(region.vertex_count):
        base = region.vb_start + i * region.stride
        if base + region.stride > len(data):
            break
        po = region.pos_offset
        no = region.nrm_offset
        uo = region.uv_offset
        verts.append((f32(data, base + po), f32(data, base + po + 4), f32(data, base + po + 8)))
        norms.append((f32(data, base + no), f32(data, base + no + 4), f32(data, base + no + 8)))
        uvs.append((f32(data, base + uo), 1.0 - f32(data, base + uo + 4)))
    return verts, norms, uvs


# ---------------------------------------------------------------------------
# Step 3: IB reading using descriptor ic (KEY FIX)
# ---------------------------------------------------------------------------

def read_ib_descriptor(data: bytes, region: VBRegion, all_regions: list[VBRegion]) -> list[int]:
    """Read exactly index_count u16 values from IB start.

    This is the KEY FIX: we do NOT stop on out-of-range indices.
    The descriptor ic gives the exact count.
    """
    ic = region.index_count
    if ic <= 0:
        return []

    ib_start = region.ib_start
    # Validate we have enough data
    if ib_start + ic * 2 > len(data):
        # Truncated -- read what we can
        ic = (len(data) - ib_start) // 2

    indices = []
    for i in range(ic):
        indices.append(u16(data, ib_start + i * 2))

    return indices


def read_ib_heuristic(
    data: bytes, region: VBRegion, all_regions: list[VBRegion],
    max_valid: int,
) -> list[int]:
    """Fallback: read contiguous valid u16 indices (old heuristic method).

    Used when descriptor ic is 0 (synthetic descriptors from W-only runs)
    or when descriptor-based reading produces degenerate geometry.
    """
    vb_end = region.vb_end
    # Find boundary (next VB or EOF)
    gap_end = len(data)
    for other in all_regions:
        if other.vb_start > vb_end:
            gap_end = other.vb_start
            break

    # Scan forward for start of valid indices
    IB_PROBE = 6
    ib_start = None
    scan_limit = min(vb_end + 256, gap_end)
    for off in range(vb_end, scan_limit - IB_PROBE * 2, 2):
        all_valid = True
        for j in range(IB_PROBE):
            if off + (j + 1) * 2 > len(data):
                all_valid = False
                break
            val = u16(data, off + j * 2)
            if val > max_valid:
                all_valid = False
                break
        if all_valid:
            ib_start = off
            break

    if ib_start is None:
        return []

    indices: list[int] = []
    for off in range(ib_start, gap_end - 1, 2):
        val = u16(data, off)
        if val <= max_valid:
            indices.append(val)
        else:
            break

    return indices


# ---------------------------------------------------------------------------
# Step 5: Face decoding with strip/list auto-detection
# ---------------------------------------------------------------------------

def decode_strip(indices: list[int], max_valid: int) -> list[tuple[int, int, int]]:
    """Decode triangle strip with degenerate restart."""
    faces = []
    for i in range(len(indices) - 2):
        i0, i1, i2 = indices[i], indices[i + 1], indices[i + 2]
        if i0 == i1 or i1 == i2 or i0 == i2:
            continue
        if max(i0, i1, i2) > max_valid:
            continue
        if i % 2 == 0:
            faces.append((i0, i1, i2))
        else:
            faces.append((i0, i2, i1))
    return faces


def decode_list(indices: list[int], max_valid: int) -> list[tuple[int, int, int]]:
    """Decode triangle list."""
    faces = []
    for fi in range(len(indices) // 3):
        i0, i1, i2 = indices[fi * 3], indices[fi * 3 + 1], indices[fi * 3 + 2]
        if i0 == i1 or i1 == i2 or i0 == i2:
            continue
        if max(i0, i1, i2) > max_valid:
            continue
        faces.append((i0, i1, i2))
    return faces


def face_aspect(v0, v1, v2) -> float:
    """Compute aspect ratio of a triangle."""
    edges = [
        math.sqrt(sum((a[k] - b[k]) ** 2 for k in range(3)))
        for a, b in [(v0, v1), (v1, v2), (v2, v0)]
    ]
    mn = min(edges)
    return max(edges) / mn if mn > 1e-10 else 999999


def face_area(v0, v1, v2) -> float:
    """Compute area of a triangle."""
    e1 = (v1[0] - v0[0], v1[1] - v0[1], v1[2] - v0[2])
    e2 = (v2[0] - v0[0], v2[1] - v0[1], v2[2] - v0[2])
    cx = e1[1] * e2[2] - e1[2] * e2[1]
    cy = e1[2] * e2[0] - e1[0] * e2[2]
    cz = e1[0] * e2[1] - e1[1] * e2[0]
    return math.sqrt(cx * cx + cy * cy + cz * cz) / 2


def avg_aspect(faces, verts, sample=300) -> float:
    """Average aspect ratio over a sample of faces."""
    if not faces:
        return 999999
    total = 0.0
    n = 0
    for i0, i1, i2 in faces[:sample]:
        if max(i0, i1, i2) >= len(verts):
            continue
        ar = face_aspect(verts[i0], verts[i1], verts[i2])
        if ar < 999999:
            total += ar
            n += 1
    return total / n if n else 999999


def decode_best(
    indices: list[int], max_valid: int, verts: list[tuple[float, float, float]],
) -> list[tuple[int, int, int]]:
    """Auto-detect strip vs list by comparing face count and aspect ratio.

    Gin7 engine predominantly uses D3D9 triangle strips. Strip decoding
    is preferred unless list decoding produces significantly more faces
    with comparable quality. When strip produces 1.5x+ more faces, it
    is strongly favored because the aspect ratio heuristic on a small
    sample can be misleading for list interpretations of strip data.
    """
    sf = decode_strip(indices, max_valid)
    lf = decode_list(indices, max_valid) if len(indices) % 3 == 0 else []

    if not lf:
        return sf
    if not sf:
        return lf

    # If strip produces significantly more faces, prefer it
    # (list interpretation of strip data produces ~1/3 the faces with
    # misleadingly good aspect ratios from random triangle groupings)
    if len(sf) >= len(lf) * 1.5:
        return sf
    if len(lf) >= len(sf) * 1.5:
        return lf

    # Close face counts: use aspect ratio to decide
    s_ar = avg_aspect(sf, verts)
    l_ar = avg_aspect(lf, verts)
    return sf if s_ar <= l_ar else lf


def fix_winding(i0, i1, i2, verts, norms) -> tuple[int, int, int]:
    """Fix winding using vertex normals."""
    p0, p1, p2 = verts[i0], verts[i1], verts[i2]
    e1 = (p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2])
    e2 = (p2[0] - p0[0], p2[1] - p0[1], p2[2] - p0[2])
    fn = (
        e1[1] * e2[2] - e1[2] * e2[1],
        e1[2] * e2[0] - e1[0] * e2[2],
        e1[0] * e2[1] - e1[1] * e2[0],
    )
    an = (
        norms[i0][0] + norms[i1][0] + norms[i2][0],
        norms[i0][1] + norms[i1][1] + norms[i2][1],
        norms[i0][2] + norms[i1][2] + norms[i2][2],
    )
    dot = fn[0] * an[0] + fn[1] * an[1] + fn[2] * an[2]
    if dot < 0:
        return (i0, i2, i1)
    return (i0, i1, i2)


# ---------------------------------------------------------------------------
# Step 7: Combined VB pool index resolution
# ---------------------------------------------------------------------------

def resolve_pool_index(
    index: int, regions: list[VBRegion],
) -> Optional[tuple[int, int]]:
    """Resolve a combined pool index to (region_index, local_vertex_index).

    The combined pool numbers vertices sequentially:
    VB0: 0..vc0-1, VB1: vc0..vc0+vc1-1, etc.
    """
    for ri, r in enumerate(regions):
        if r.pool_base <= index < r.pool_base + r.vertex_count:
            return (ri, index - r.pool_base)
    return None


# ---------------------------------------------------------------------------
# Face filtering helper
# ---------------------------------------------------------------------------

def _filter_faces(
    faces: list[tuple[int, int, int]],
    verts: list[tuple[float, float, float]],
    norms: list[tuple[float, float, float]],
    strict: bool = False,
) -> list[tuple[int, int, int]]:
    """Fix winding and optionally filter degenerate faces.

    Default (strict=False): only fix winding and skip out-of-range indices.
    This matches v2 behavior (no quality filtering) to avoid regressions.

    Strict mode: also removes zero-area and high-aspect-ratio faces.
    Used for extra/cross-VB IBs where garbage is more likely.
    """
    clean: list[tuple[int, int, int]] = []
    for i0, i1, i2 in faces:
        if max(i0, i1, i2) >= len(verts):
            continue
        if strict:
            area = face_area(verts[i0], verts[i1], verts[i2])
            if area < 1e-8:
                continue
            ar = face_aspect(verts[i0], verts[i1], verts[i2])
            if ar > 80:
                continue
        f = fix_winding(i0, i1, i2, verts, norms)
        clean.append(f)
    return clean


# ---------------------------------------------------------------------------
# Main extraction pipeline
# ---------------------------------------------------------------------------

def extract_mdx(
    data: bytes, name: str, verbose: bool = False,
) -> Optional[list[MeshPart]]:
    """Extract all mesh parts from an MDX file using descriptor-driven approach."""

    # Step 1: Discover descriptors
    descriptors = scan_descriptors(data)
    if verbose:
        print(f"  {name}: found {len(descriptors)} raw descriptors")

    # Step 2: Match descriptors to VB data
    regions = match_descriptors_to_vbs(data, descriptors)
    if not regions:
        if verbose:
            print(f"  {name}: no VB regions found")
        return None

    if verbose:
        total_pool = sum(r.vertex_count for r in regions)
        print(f"  {name}: {len(regions)} VB regions, combined pool={total_pool} vertices")
        for i, r in enumerate(regions):
            desc_label = f"desc@0x{r.descriptor.offset:x}" if r.descriptor.offset >= 0 else "W-heuristic"
            print(
                f"    VB[{i}]: 0x{r.vb_start:x}-0x{r.vb_end:x}, "
                f"{r.vertex_count}v, stride={r.stride}, "
                f"ic={r.index_count}, pool_base={r.pool_base}, "
                f"{desc_label}, type2={r.descriptor.type2}"
            )

    # Read all vertex data upfront (for combined pool resolution)
    all_verts: list[list[tuple[float, float, float]]] = []
    all_norms: list[list[tuple[float, float, float]]] = []
    all_uvs: list[list[tuple[float, float]]] = []
    for r in regions:
        v, n, u = read_vertices(data, r)
        all_verts.append(v)
        all_norms.append(n)
        all_uvs.append(u)

    total_pool_size = sum(r.vertex_count for r in regions)

    # Build flat combined pool arrays for face decoding
    pool_verts: list[tuple[float, float, float]] = []
    pool_norms: list[tuple[float, float, float]] = []
    pool_uvs: list[tuple[float, float]] = []
    for ri in range(len(regions)):
        pool_verts.extend(all_verts[ri])
        pool_norms.extend(all_norms[ri])
        pool_uvs.extend(all_uvs[ri])

    parts: list[MeshPart] = []

    # Step 3 + 7: Process each region's IB
    for ri, region in enumerate(regions):
        # Read indices using best available method:
        # 1. Descriptor ic gives a guaranteed count (may be subset)
        # 2. Heuristic reads contiguous valid local indices (may be longer)
        # 3. Use the longer of the two for maximum face capture

        # Always try heuristic for local indices
        heuristic_indices = read_ib_heuristic(
            data, region, regions,
            max_valid=region.vertex_count - 1,
        )

        if region.index_count > 0:
            # Descriptor available -- read descriptor-defined indices
            desc_indices = read_ib_descriptor(data, region, regions)
            desc_max = max(desc_indices) if desc_indices else 0

            # Check if descriptor indices go beyond local VB (cross-VB)
            if desc_max >= region.vertex_count and desc_max < total_pool_size:
                # Cross-VB: use descriptor indices for pool-based decoding
                # AND also use heuristic for local-only decoding
                # Keep both and pick whichever produces more faces
                pool_faces = decode_best(desc_indices, total_pool_size - 1, pool_verts)
                local_faces = decode_best(heuristic_indices, region.vertex_count - 1, all_verts[ri]) if heuristic_indices else []

                if verbose:
                    print(
                        f"    VB[{ri}]: {len(desc_indices)} desc indices (cross-VB, max={desc_max}), "
                        f"{len(heuristic_indices)} heuristic indices, "
                        f"pool_faces={len(pool_faces)}, local_faces={len(local_faces)}"
                    )

                # Use pool faces if they produce more, otherwise local
                if len(pool_faces) >= len(local_faces):
                    clean_faces = _filter_faces(pool_faces, pool_verts, pool_norms, strict=True)
                    if clean_faces:
                        parts.append(MeshPart(
                            vertices=pool_verts, normals=pool_norms,
                            uvs=pool_uvs, faces=clean_faces,
                            source=f"VB[{ri}]-pool",
                            pool_base=0,
                        ))
                    continue
                else:
                    # Local heuristic is better -- fall through to local below
                    indices = heuristic_indices
            else:
                # Descriptor indices are local -- use longer of desc vs heuristic
                indices = desc_indices if len(desc_indices) >= len(heuristic_indices) else heuristic_indices
        else:
            indices = heuristic_indices

        if not indices:
            if verbose:
                print(f"    VB[{ri}]: no indices found")
            continue

        max_idx = max(indices)
        if verbose:
            print(
                f"    VB[{ri}]: {len(indices)} indices, "
                f"range=[{min(indices)}..{max_idx}], "
                f"local_vc={region.vertex_count}, pool_size={total_pool_size}"
            )

        # Local indices: decode against this VB only
        local_max = region.vertex_count - 1
        faces = decode_best(indices, local_max, all_verts[ri])
        if verbose:
            print(f"    VB[{ri}]: local mode, {len(faces)} faces")

        clean_faces = _filter_faces(faces, all_verts[ri], all_norms[ri])

        if clean_faces:
            parts.append(MeshPart(
                vertices=all_verts[ri],
                normals=all_norms[ri],
                uvs=all_uvs[ri],
                faces=clean_faces,
                source=f"VB[{ri}]-local",
                pool_base=region.pool_base,
            ))

    # Step 4: After-VB IB discovery (scan ALL gaps, not just descriptor IBs)
    # gin7 stores IBs after each VB. OOB values (>= vc) are strip restart markers,
    # not data boundaries. Read the entire gap up to the next VB.
    sorted_by_start = sorted(range(len(regions)), key=lambda i: regions[i].vb_start)
    for si, ri in enumerate(sorted_by_start):
        region = regions[ri]
        # Start scanning after VB end (or after descriptor IB if present)
        if region.index_count > 0:
            scan_start = region.ib_start + region.index_count * 2
        else:
            scan_start = region.vb_end

        # Find next VB start
        if si + 1 < len(sorted_by_start):
            next_vb_start = regions[sorted_by_start[si + 1]].vb_start
        else:
            next_vb_start = min(len(data), scan_start + 50000)

        gap_size = next_vb_start - scan_start
        if gap_size < 12:
            continue

        # Read ALL u16 values in gap (OOB = strip restart, not stop)
        extra_indices: list[int] = []
        for off in range(scan_start, next_vb_start - 1, 2):
            val = u16(data, off)
            extra_indices.append(val)

        if len(extra_indices) < 6:
            continue

        # Try both local decode (indices < local vc) and pool decode
        local_vc = region.vertex_count
        local_indices = [v if v < local_vc else -1 for v in extra_indices]
        pool_indices = [v if v < total_pool_size else -1 for v in extra_indices]

        # Decode local: strip with OOB restart
        local_faces = []
        buf: list[int] = []
        for v in local_indices:
            if v < 0:
                buf = []
                continue
            buf.append(v)
            if len(buf) >= 3:
                i0, i1, i2 = buf[-3], buf[-2], buf[-1]
                if i0 != i1 and i1 != i2 and i0 != i2:
                    idx = len(buf) - 3
                    local_faces.append((i0, i1, i2) if idx % 2 == 0 else (i0, i2, i1))

        # Decode pool: strip with OOB restart
        pool_faces = []
        buf = []
        for v in pool_indices:
            if v < 0:
                buf = []
                continue
            buf.append(v)
            if len(buf) >= 3:
                i0, i1, i2 = buf[-3], buf[-2], buf[-1]
                if i0 != i1 and i1 != i2 and i0 != i2:
                    idx = len(buf) - 3
                    pool_faces.append((i0, i1, i2) if idx % 2 == 0 else (i0, i2, i1))

        # Use whichever produces more faces, with aspect ratio quality gate
        if len(pool_faces) >= len(local_faces) and pool_faces:
            ar = avg_aspect(pool_faces, pool_verts)
            if ar < 30:  # reject garbage data with high aspect ratio
                clean_faces = _filter_faces(pool_faces, pool_verts, pool_norms, strict=True)
                if clean_faces:
                    parts.append(MeshPart(
                        vertices=pool_verts, normals=pool_norms,
                        uvs=pool_uvs, faces=clean_faces,
                        source=f"VB[{ri}]-extra-pool",
                        pool_base=0,
                    ))
                    if verbose:
                        print(f"    VB[{ri}]-extra: {len(extra_indices)} gap indices, "
                              f"{len(clean_faces)} pool faces (ar={ar:.1f})")
            elif verbose:
                print(f"    VB[{ri}]-extra: REJECTED pool (ar={ar:.1f} > 30)")
        elif local_faces:
            ar = avg_aspect(local_faces, all_verts[ri])
            if ar < 30:
                clean_faces = _filter_faces(local_faces, all_verts[ri], all_norms[ri])
                if clean_faces:
                    parts.append(MeshPart(
                        vertices=all_verts[ri], normals=all_norms[ri],
                        uvs=all_uvs[ri], faces=clean_faces,
                        source=f"VB[{ri}]-extra-local",
                        pool_base=region.pool_base,
                    ))
                    if verbose:
                        print(f"    VB[{ri}]-extra: {len(extra_indices)} gap indices, "
                              f"{len(clean_faces)} local faces (ar={ar:.1f})")
            elif verbose:
                print(f"    VB[{ri}]-extra: REJECTED local (ar={ar:.1f} > 30)")

    return parts if parts else None


# ---------------------------------------------------------------------------
# OBJ output
# ---------------------------------------------------------------------------

def write_obj(
    parts: list[MeshPart],
    obj_path: Path,
    name: str,
    do_mirror: bool = True,
) -> tuple[int, int]:
    """Write merged OBJ with proper vertex base offsets per part.

    Returns (total_vertices, total_faces).
    """
    obj_path.parent.mkdir(parents=True, exist_ok=True)

    # Merge parts, deduplicating pool-based parts that share vertex arrays
    # Use a vertex offset approach: each part gets its own vertex block
    # Even if two parts share the same pool_verts, we write separate v/vn/vt
    # blocks so face indices are correct per part.

    # However, to avoid massive duplication when multiple parts reference
    # the same pool, we can merge parts that share the same vertex array.
    # We detect sharing by checking if vertices list is the same object (identity).

    # Group parts by vertex array identity
    vert_groups: dict[int, list[MeshPart]] = {}
    for part in parts:
        vid = id(part.vertices)
        if vid not in vert_groups:
            vert_groups[vid] = []
        vert_groups[vid].append(part)

    # Build deduplicated vertex blocks
    blocks: list[tuple[
        list[tuple[float, float, float]],
        list[tuple[float, float, float]],
        list[tuple[float, float]],
        list[tuple[int, int, int]],
    ]] = []

    for vid, group_parts in vert_groups.items():
        verts = group_parts[0].vertices
        norms = group_parts[0].normals
        uvs = group_parts[0].uvs
        # Merge all faces from parts sharing this vertex array
        # Use list without dedup -- winding fix can map distinct strip
        # triangles to the same tuple, and dropping them causes regressions
        merged_faces: list[tuple[int, int, int]] = []
        for p in group_parts:
            merged_faces.extend(p.faces)
        blocks.append((verts, norms, uvs, merged_faces))

    # Apply mirror if requested
    if do_mirror:
        mirrored_blocks = []
        for verts, norms, uvs, faces in blocks:
            n = len(verts)
            m_verts = [(-v[0], v[1], v[2]) for v in verts]
            m_norms = [(-n[0], n[1], n[2]) for n in norms]
            m_uvs = list(uvs)
            m_faces = [(f[0] + n, f[2] + n, f[1] + n) for f in faces]  # reverse winding
            combined_verts = list(verts) + m_verts
            combined_norms = list(norms) + m_norms
            combined_uvs = list(uvs) + m_uvs
            combined_faces = list(faces) + m_faces
            mirrored_blocks.append((combined_verts, combined_norms, combined_uvs, combined_faces))
        blocks = mirrored_blocks

    # ── Post-process: merge blocks → clean → smooth normals ──

    # Flatten all blocks into single arrays
    all_verts = []
    all_uvs = []
    all_faces = []
    vo = 0
    for verts, norms, uvs, faces in blocks:
        all_verts.extend(verts)
        all_uvs.extend(uvs)
        for i0, i1, i2 in faces:
            all_faces.append((i0 + vo, i1 + vo, i2 + vo))
        vo += len(verts)

    import numpy as np
    verts_arr = np.array(all_verts, dtype=np.float64)
    faces_arr = list(all_faces)
    nv = len(verts_arr)

    # Step 1: Merge nearby vertices (position + UV must both be close)
    # Preserves UV seams: vertices at same position but different UVs stay separate
    if nv > 0:
        from scipy.spatial import cKDTree
        bbox_range = verts_arr.max(axis=0) - verts_arr.min(axis=0)
        tol = max(bbox_range) * 0.0005
        uv_tol = 0.01  # UV tolerance (1% of texture space)
        tree = cKDTree(verts_arr)
        pairs = tree.query_pairs(r=tol)
        if pairs:
            # Filter: only merge if UVs are also close
            uv_arr = np.array(all_uvs, dtype=np.float64)
            parent = list(range(nv))
            def _find(x):
                while parent[x] != x:
                    parent[x] = parent[parent[x]]
                    x = parent[x]
                return x
            for a, b in pairs:
                # Check UV distance before merging
                uv_dist = np.sqrt(np.sum((uv_arr[a] - uv_arr[b]) ** 2))
                if uv_dist > uv_tol:
                    continue  # UV seam — don't merge
                ra, rb = _find(a), _find(b)
                if ra != rb:
                    parent[rb] = ra
            remap = [_find(i) for i in range(nv)]
            # Compact: assign new sequential indices
            unique_map = {}
            new_idx = 0
            compact = [0] * nv
            for i in range(nv):
                root = remap[i]
                if root not in unique_map:
                    unique_map[root] = new_idx
                    new_idx += 1
                compact[i] = unique_map[root]
            # Build new vertex/uv arrays (use representative vertex)
            new_verts = [None] * new_idx
            new_uvs = [None] * new_idx
            for i in range(nv):
                ci = compact[i]
                if new_verts[ci] is None:
                    new_verts[ci] = verts_arr[i]
                    new_uvs[ci] = all_uvs[i]
            verts_arr = np.array(new_verts, dtype=np.float64)
            all_uvs = new_uvs
            # Remap faces
            new_faces = []
            for i0, i1, i2 in faces_arr:
                ni0, ni1, ni2 = compact[i0], compact[i1], compact[i2]
                if ni0 != ni1 and ni1 != ni2 and ni0 != ni2:
                    new_faces.append((ni0, ni1, ni2))
            faces_arr = new_faces
            nv = len(verts_arr)

    # Remove duplicate faces
    faces_set = set()
    unique_faces = []
    for f in faces_arr:
        key = tuple(sorted(f))
        if key not in faces_set:
            faces_set.add(key)
            unique_faces.append(f)
    faces_arr = unique_faces

    # Step 2: Spaghetti filter (long-edge faces)
    if nv > 10 and faces_arr:
        bbox_range = verts_arr.max(axis=0) - verts_arr.min(axis=0)
        diag_sq = float(np.sum(bbox_range ** 2))
        long_sq = diag_sq * 0.09  # 30% of diagonal
        clean_faces = []
        for i0, i1, i2 in faces_arr:
            d01 = float(np.sum((verts_arr[i0] - verts_arr[i1]) ** 2))
            d12 = float(np.sum((verts_arr[i1] - verts_arr[i2]) ** 2))
            d20 = float(np.sum((verts_arr[i2] - verts_arr[i0]) ** 2))
            if max(d01, d12, d20) <= long_sq:
                clean_faces.append((i0, i1, i2))
        faces_arr = clean_faces

    # Step 3: Recompute area-weighted vertex normals (smooths part boundaries)
    normals_arr = np.zeros((nv, 3), dtype=np.float64)
    for i0, i1, i2 in faces_arr:
        v0, v1, v2 = verts_arr[i0], verts_arr[i1], verts_arr[i2]
        e1 = v1 - v0
        e2 = v2 - v0
        fn = np.cross(e1, e2)  # area-weighted (magnitude = 2 * area)
        normals_arr[i0] += fn
        normals_arr[i1] += fn
        normals_arr[i2] += fn
    # Normalize
    lengths = np.linalg.norm(normals_arr, axis=1, keepdims=True)
    lengths = np.where(lengths < 1e-8, 1.0, lengths)
    normals_arr = normals_arr / lengths

    # Step 4: Fix face winding — align with vertex normals
    fixed_faces = []
    for i0, i1, i2 in faces_arr:
        v0, v1, v2 = verts_arr[i0], verts_arr[i1], verts_arr[i2]
        e1 = v1 - v0
        e2 = v2 - v0
        fn = np.cross(e1, e2)
        avg_vn = normals_arr[i0] + normals_arr[i1] + normals_arr[i2]
        if np.dot(fn, avg_vn) < 0:
            fixed_faces.append((i0, i2, i1))  # flip winding
        else:
            fixed_faces.append((i0, i1, i2))
    faces_arr = fixed_faces

    # ── Write OBJ ──
    total_v = len(verts_arr)
    total_f = len(faces_arr)

    with obj_path.open("w", encoding="utf-8") as f:
        f.write(f"# {name}\n")
        f.write(f"g {name}\n")
        for v in verts_arr:
            f.write(f"v {v[0]:.6f} {v[1]:.6f} {v[2]:.6f}\n")
        for n in normals_arr:
            f.write(f"vn {n[0]:.6f} {n[1]:.6f} {n[2]:.6f}\n")
        for uv in all_uvs:
            f.write(f"vt {uv[0]:.6f} {uv[1]:.6f}\n")
        for i0, i1, i2 in faces_arr:
            a, b, c = i0 + 1, i1 + 1, i2 + 1
            f.write(f"f {a}/{a}/{a} {b}/{b}/{b} {c}/{c}/{c}\n")

    return total_v, total_f


# ---------------------------------------------------------------------------
# Comparison with baseline
# ---------------------------------------------------------------------------

def count_obj_stats(obj_path: Path) -> tuple[int, int]:
    """Count vertices and faces in an OBJ file."""
    verts = 0
    faces = 0
    if not obj_path.exists():
        return 0, 0
    with obj_path.open("r") as f:
        for line in f:
            if line.startswith("v "):
                verts += 1
            elif line.startswith("f "):
                faces += 1
    return verts, faces


def compare_with_baseline(
    new_dir: Path, baseline_dir: Path,
) -> None:
    """Print comparison table between new parser and baseline."""
    print("\n" + "=" * 70)
    print("COMPARISON: new (scene graph) vs baseline (v2)")
    print("=" * 70)
    print(f"{'Model':<30} {'New V':>8} {'New F':>8} {'Old V':>8} {'Old F':>8} {'Delta F':>8}")
    print("-" * 70)

    new_total_v = new_total_f = old_total_v = old_total_f = 0
    regressions = 0
    improvements = 0

    # Find all model directories in new output
    if not new_dir.exists():
        print("New output directory does not exist")
        return

    models = sorted(d.name for d in new_dir.iterdir() if d.is_dir())
    for model_name in models:
        new_obj = new_dir / model_name / "high.obj"
        # Try both exact name and _h suffix (v2 extractor keeps _h)
        old_obj = baseline_dir / model_name / "high.obj"
        if not old_obj.exists():
            old_obj = baseline_dir / (model_name + "_h") / "high.obj"

        nv, nf = count_obj_stats(new_obj)
        ov, of_ = count_obj_stats(old_obj)

        delta_f = nf - of_
        marker = ""
        if delta_f < 0:
            marker = " REGRESSION"
            regressions += 1
        elif delta_f > 0:
            improvements += 1

        print(f"{model_name:<30} {nv:>8} {nf:>8} {ov:>8} {of_:>8} {delta_f:>+8}{marker}")

        new_total_v += nv
        new_total_f += nf
        old_total_v += ov
        old_total_f += of_

    print("-" * 70)
    delta_total = new_total_f - old_total_f
    print(
        f"{'TOTAL':<30} {new_total_v:>8} {new_total_f:>8} "
        f"{old_total_v:>8} {old_total_f:>8} {delta_total:>+8}"
    )
    print(f"\nImprovements: {improvements}, Regressions: {regressions}")
    if regressions == 0:
        print("No regressions -- new parser is >= baseline for all models.")
    else:
        print(f"WARNING: {regressions} models have fewer faces in new parser!")


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="MDX Scene Graph Parser -- descriptor-driven extraction "
                    "with combined VB pool support.",
    )
    parser.add_argument(
        "input", type=Path,
        help="Path to .mdx file or directory of .mdx files",
    )
    parser.add_argument(
        "--out", type=Path, default=Path("/tmp/mdx_scene_out"),
        help="Output directory (default: /tmp/mdx_scene_out)",
    )
    parser.add_argument(
        "--no-mirror", action="store_true",
        help="Disable X-axis mirroring",
    )
    parser.add_argument(
        "--verbose", "-v", action="store_true",
        help="Show detailed per-VB/IB diagnostics",
    )
    parser.add_argument(
        "--info", action="store_true",
        help="Print descriptor/VB/IB info without writing OBJ",
    )
    parser.add_argument(
        "--compare", type=Path, default=None,
        help="Baseline OBJ directory for comparison report",
    )
    return parser.parse_args()


def process_file(
    mdx_path: Path,
    out_dir: Path,
    do_mirror: bool,
    info_only: bool,
    verbose: bool,
) -> tuple[bool, int, int]:
    """Process one MDX file. Returns (success, vertex_count, face_count)."""
    data = mdx_path.read_bytes()
    name = mdx_path.stem.replace("_h", "")

    parts = extract_mdx(data, name, verbose)

    if not parts:
        print(f"  {name}: FAIL (no mesh parts)")
        return False, 0, 0

    if info_only:
        total_faces = sum(len(p.faces) for p in parts)
        # Count unique vertices across all parts
        seen_vids = set()
        for p in parts:
            seen_vids.add(id(p.vertices))
        total_verts = sum(len(parts[0].vertices) for vid in seen_vids
                          for p in parts if id(p.vertices) == vid and p is parts[[
                              j for j, pp in enumerate(parts) if id(pp.vertices) == vid
                          ][0]])
        print(f"  {name}: {len(parts)} parts, ~{total_faces}f (info only)")
        return True, 0, 0

    model_dir = out_dir / name
    tv, tf = write_obj(parts, model_dir / "high.obj", name, do_mirror)
    print(f"  {name}: {tv}v {tf}f ({len(parts)} parts)")
    return True, tv, tf


def main() -> int:
    args = parse_args()
    do_mirror = not args.no_mirror

    if args.input.is_dir():
        mdx_files = sorted(args.input.glob("*_h.mdx"))
        if not mdx_files:
            # Also try *.mdx
            mdx_files = sorted(args.input.glob("*.mdx"))
        if not mdx_files:
            print(f"No MDX files found in {args.input}")
            return 1
    else:
        mdx_files = [args.input]

    args.out.mkdir(parents=True, exist_ok=True)
    ok = fail = total_v = total_f = 0

    for mdx_path in mdx_files:
        success, nv, nf = process_file(
            mdx_path, args.out, do_mirror, args.info, args.verbose,
        )
        if success:
            ok += 1
            total_v += nv
            total_f += nf
        else:
            fail += 1

    mirror_label = " (mirrored)" if do_mirror else ""
    print(f"\n=== {ok}/{ok + fail} OK, {total_v:,}v {total_f:,}f{mirror_label} ===")

    # Run comparison if requested
    if args.compare:
        compare_with_baseline(args.out, args.compare)

    return 0 if fail == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
