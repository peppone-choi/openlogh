#!/usr/bin/env python3
"""Validate combined vertex pool theory on Brunhild gap/extra IB data."""
import struct, os, tempfile, math

def u16(d, o): return struct.unpack_from('<H', d, o)[0]
def u32(d, o): return struct.unpack_from('<I', d, o)[0]
def f32(d, o): return struct.unpack_from('<f', d, o)[0]

MARKER_STRIDE = {18: 24, 65810: 36, 74002: 72, 336402: 84}
STRIDE_FMT = {
    24:  {'p': 0, 'n': 12, 'uv': -1},
    36:  {'p': 0, 'n': 12, 'uv': 24},
    72:  {'p': 0, 'n': 12, 'uv': 24},
    84:  {'p': 52, 'n': 40, 'uv': 68},
}

def find_descriptors(data, start_off=0):
    descs = []
    for off in range(start_off, len(data) - 36, 4):
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
        descs.append({'off': off, 'prim': prim, 'vc': vc, 'ic': ic, 'stride': st})
    return descs


def read_vertex(data, off, stride):
    """Read one vertex, return (pos, normal, uv)."""
    fmt = STRIDE_FMT[stride]
    px, py, pz = struct.unpack_from('<3f', data, off + fmt['p'])
    nx, ny, nz = struct.unpack_from('<3f', data, off + fmt['n'])
    if fmt['uv'] >= 0:
        u, v = struct.unpack_from('<2f', data, off + fmt['uv'])
    else:
        u, v = 0.0, 0.0
    return (px, py, pz), (nx, ny, nz), (u, v)


def main():
    mdx_dir = os.path.join(tempfile.gettempdir(), 'warship_mdx')
    path = os.path.join(mdx_dir, 'e_brunhild_h.mdx')
    with open(path, 'rb') as f:
        data = f.read()

    descs = find_descriptors(data)
    print("Descriptors: %d" % len(descs))
    for i, d in enumerate(descs):
        print("  [%d] stride=%d vc=%d ic=%d prim=%d" % (i, d['stride'], d['vc'], d['ic'], d['prim']))

    # Known VB locations (from previous analysis)
    vb_info = [
        {'stride': 24, 'vc': 98,   'vb_start': 0x3904},
        {'stride': 72, 'vc': 2943, 'vb_start': 0x4430},
        {'stride': 84, 'vc': 3104, 'vb_start': 0x3DFA8},
    ]

    # Build combined pool: s72 + s84 (excluding s24 - test both theories)
    print("\n=== Building combined vertex pools ===")

    # Theory A: s72(0) + s84(2943)
    pool_a = []
    # Add s72 vertices
    vb72 = vb_info[1]
    for i in range(vb72['vc']):
        pos, norm, uv = read_vertex(data, vb72['vb_start'] + i * 72, 72)
        pool_a.append((pos, norm, uv))
    # Add s84 vertices
    vb84 = vb_info[2]
    for i in range(vb84['vc']):
        pos, norm, uv = read_vertex(data, vb84['vb_start'] + i * 84, 84)
        pool_a.append((pos, norm, uv))
    pool_a_size = len(pool_a)
    print("Pool A (s72+s84): %d vertices (s72@0, s84@%d)" % (pool_a_size, vb72['vc']))

    # Theory B: s24(0) + s72(98) + s84(3041)
    pool_b = []
    vb24 = vb_info[0]
    for i in range(vb24['vc']):
        pos, norm, uv = read_vertex(data, vb24['vb_start'] + i * 24, 24)
        pool_b.append((pos, norm, uv))
    for i in range(vb72['vc']):
        pos, norm, uv = read_vertex(data, vb72['vb_start'] + i * 72, 72)
        pool_b.append((pos, norm, uv))
    for i in range(vb84['vc']):
        pos, norm, uv = read_vertex(data, vb84['vb_start'] + i * 84, 84)
        pool_b.append((pos, norm, uv))
    pool_b_size = len(pool_b)
    print("Pool B (s24+s72+s84): %d vertices (s24@0, s72@%d, s84@%d)" %
          (pool_b_size, vb24['vc'], vb24['vc'] + vb72['vc']))

    # Test regions with index data
    # Gap 1: between s24 IB end and s72 VB start
    gap1_start = 0x4234 + 156 * 2  # s24 IB end (0x4234 + 312 = 0x436C)
    gap1_end = 0x4430  # s72 VB start
    # Gap 2: between s72 IB end and s84 VB start
    s72_ib_start = vb72['vb_start'] + vb72['vc'] * 72  # 0x37FE8
    gap2_start = s72_ib_start + 9297 * 2  # + 1 extra = 0x3C88A, align to 0x3C88C
    gap2_end = vb84['vb_start']  # 0x3DFA8
    # Extra s84 IB: after declared IB
    s84_ib_start = vb84['vb_start'] + vb84['vc'] * 84  # 0x7DA28
    s84_extra_start = s84_ib_start + 7191 * 2  # 0x7DA28 + 14382 = 0x81236
    # Remaining: from end of all known data
    remaining_start = 0x825BC  # from previous analysis

    test_regions = [
        ("Gap1 (s24_IB..s72_VB)", gap1_start, gap1_end),
        ("Gap2 (s72_IB..s84_VB)", gap2_start, gap2_end),
        ("s84 extra IB", s84_extra_start, remaining_start),
        ("Remaining data", remaining_start, len(data)),
    ]

    for name, start, end in test_regions:
        size = end - start
        n_indices = size // 2
        if n_indices == 0:
            continue

        print("\n--- %s: 0x%04X-0x%04X (%d bytes, %d u16) ---" % (name, start, end, size, n_indices))

        indices = [u16(data, start + i * 2) for i in range(n_indices)]
        max_idx = max(indices)
        min_idx = min(indices)

        # Count valid for each theory
        valid_a = sum(1 for idx in indices if idx < pool_a_size)
        valid_b = sum(1 for idx in indices if idx < pool_b_size)

        pct_a = valid_a * 100.0 / n_indices
        pct_b = valid_b * 100.0 / n_indices

        print("  Index range: %d - %d" % (min_idx, max_idx))
        print("  Pool A (s72+s84, max=%d): %d/%d valid (%.1f%%)" % (pool_a_size, valid_a, n_indices, pct_a))
        print("  Pool B (s24+s72+s84, max=%d): %d/%d valid (%.1f%%)" % (pool_b_size, valid_b, n_indices, pct_b))

        # Which pool do the indices map to?
        in_s24_range = sum(1 for idx in indices if idx < 98)
        in_s72_range_a = sum(1 for idx in indices if idx < 2943)
        in_s84_range_a = sum(1 for idx in indices if 2943 <= idx < 6047)
        in_s72_range_b = sum(1 for idx in indices if 98 <= idx < 3041)
        in_s84_range_b = sum(1 for idx in indices if 3041 <= idx < 6145)
        oob_a = sum(1 for idx in indices if idx >= 6047)
        oob_b = sum(1 for idx in indices if idx >= 6145)

        print("  Pool A breakdown: s72(<2943)=%d, s84(2943-6046)=%d, OOB=%d" %
              (in_s72_range_a, in_s84_range_a, oob_a))
        print("  Pool B breakdown: s24(<98)=%d, s72(98-3040)=%d, s84(3041-6144)=%d, OOB=%d" %
              (in_s24_range, in_s72_range_b, in_s84_range_b, oob_b))

        # Triangle strip decode test with Pool A
        if pct_a > 80:
            tris = 0
            degen = 0
            for j in range(len(indices) - 2):
                i0, i1, i2 = indices[j], indices[j+1], indices[j+2]
                if i0 >= pool_a_size or i1 >= pool_a_size or i2 >= pool_a_size:
                    continue  # strip restart
                if i0 == i1 or i1 == i2 or i0 == i2:
                    degen += 1
                    continue
                tris += 1
            print("  Strip decode (Pool A): %d triangles, %d degenerate" % (tris, degen))

            # Verify spatial coherence: sample some triangles
            valid_tris = []
            for j in range(len(indices) - 2):
                i0, i1, i2 = indices[j], indices[j+1], indices[j+2]
                if i0 >= pool_a_size or i1 >= pool_a_size or i2 >= pool_a_size:
                    continue
                if i0 == i1 or i1 == i2 or i0 == i2:
                    continue
                p0 = pool_a[i0][0]
                p1 = pool_a[i1][0]
                p2 = pool_a[i2][0]
                # Edge lengths
                d01 = math.sqrt(sum((a-b)**2 for a,b in zip(p0, p1)))
                d12 = math.sqrt(sum((a-b)**2 for a,b in zip(p1, p2)))
                d02 = math.sqrt(sum((a-b)**2 for a,b in zip(p0, p2)))
                max_edge = max(d01, d12, d02)
                valid_tris.append(max_edge)

            if valid_tris:
                avg_edge = sum(valid_tris) / len(valid_tris)
                max_edge = max(valid_tris)
                small_edge = sum(1 for e in valid_tris if e < 50)
                big_edge = sum(1 for e in valid_tris if e > 500)
                print("  Spatial check: %d tris, avg_max_edge=%.1f, max=%.1f" %
                      (len(valid_tris), avg_edge, max_edge))
                print("  Edge distribution: small(<50)=%d, big(>500)=%d" % (small_edge, big_edge))
                if small_edge > len(valid_tris) * 0.8:
                    print("  *** SPATIALLY COHERENT - Pool A CONFIRMED ***")
                else:
                    print("  WARNING: Many large edges - possible index remapping needed")

    # Final verdict
    print("\n" + "=" * 80)
    print("COMBINED POOL VERDICT")
    print("=" * 80)
    # Read ALL extra data as one big strip
    all_extra_start = gap1_start  # from end of s24 IB
    all_indices = [u16(data, all_extra_start + i * 2)
                   for i in range((len(data) - all_extra_start) // 2)]
    valid_total = sum(1 for idx in all_indices if idx < pool_a_size)
    print("All data after s24 IB: %d indices, %d valid for Pool A (%.1f%%)" %
          (len(all_indices), valid_total, valid_total * 100.0 / len(all_indices)))

    # But we need to exclude declared VB regions
    # Non-VB regions = gap1 + declared s72 IB + gap2 + declared s84 IB + extra + remaining
    # Let's just count the IB-only regions
    ib_regions = [
        (gap1_start, gap1_end),      # gap1
        (s72_ib_start, gap2_start),  # declared s72 IB
        (gap2_start, gap2_end),      # gap2
        (s84_ib_start, len(data)),   # declared s84 IB + extra + remaining
    ]
    total_ib_indices = 0
    total_valid = 0
    for rs, re in ib_regions:
        n = (re - rs) // 2
        for i in range(n):
            idx = u16(data, rs + i * 2)
            total_ib_indices += 1
            if idx < pool_a_size:
                total_valid += 1

    print("All IB regions: %d indices, %d valid for Pool A (%.1f%%)" %
          (total_ib_indices, total_valid, total_valid * 100.0 / total_ib_indices))


if __name__ == '__main__':
    main()
