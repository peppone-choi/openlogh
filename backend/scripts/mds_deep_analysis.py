#!/usr/bin/env python3
"""Deep analysis of MDS TOC structure — focus on TOC[4] indices and cross-references."""
import struct, sys, os, glob

def u16(d, o): return struct.unpack_from('<H', d, o)[0]
def u32(d, o): return struct.unpack_from('<I', d, o)[0]
def f32(d, o): return struct.unpack_from('<f', d, o)[0]

MARKER_STRIDE = {18: 24, 65810: 36, 74002: 72, 336402: 84}

def parse_toc(data):
    entries = []
    for i in range(12):
        off = i * 8
        offset, magic, count, extra = struct.unpack_from('<4H', data, off)
        entries.append({'idx': i, 'off': offset, 'magic': magic, 'count': count, 'extra': extra})
    return entries

def safe_str(data, off, maxlen=256):
    result = []
    for i in range(maxlen):
        if off + i >= len(data):
            break
        b = data[off + i]
        if b == 0:
            break
        if 32 <= b < 127:
            result.append(chr(b))
        else:
            result.append('.')
    return ''.join(result)

def find_descriptors(data, start_off=0):
    descs = []
    for off in range(start_off, len(data) - 36, 4):
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


def analyze_brunhild():
    import tempfile
    mdx_dir = os.path.join(tempfile.gettempdir(), 'warship_mdx')
    path = os.path.join(mdx_dir, 'e_brunhild_h.mdx')
    with open(path, 'rb') as f:
        data = f.read()

    toc = parse_toc(data)
    print("=" * 80)
    print("BRUNHILD DEEP ANALYSIS (%d bytes)" % len(data))
    print("=" * 80)

    # ── TOC[4]: Sequential indices — THE KEY ──
    t4 = toc[4]
    print("\n=== TOC[4]: offset=0x%04X, count=%d ===" % (t4['off'], t4['count']))
    # Dump the full section (up to next TOC section)
    next_sections = sorted(set(t['off'] for t in toc if t['off'] > t4['off']))
    t4_end = next_sections[0] if next_sections else t4['off'] + 256
    t4_data = data[t4['off']:t4_end]
    t4_size = t4_end - t4['off']
    print("  Section size: %d bytes (0x%04X to 0x%04X)" % (t4_size, t4['off'], t4_end))

    # First: it starts with a string (same as TOC[3])
    s = safe_str(data, t4['off'])
    print("  String: '%s'" % s)
    str_end = t4['off'] + len(s) + 1
    # Align to 4 bytes
    if str_end % 4:
        str_end += 4 - (str_end % 4)
    print("  String ends at: 0x%04X (aligned)" % str_end)

    # After string: u32 values
    print("  Values after string:")
    for i in range(0, t4_size - (str_end - t4['off']), 4):
        off = str_end + i
        if off + 4 > len(data):
            break
        v = u32(data, off)
        if v == 0 and i > 60:  # stop after lots of zeros
            print("    ... (zeros follow)")
            break
        print("    @0x%04X: %5d (0x%08X)" % (off, v, v))

    # ── TOC[3]: Bounding box / transforms ──
    t3 = toc[3]
    print("\n=== TOC[3]: offset=0x%04X, count=%d, extra=0x%04X ===" % (t3['off'], t3['count'], t3['extra']))
    # Full dump
    t3_next = sorted(t['off'] for t in toc if t['off'] > t3['off'])[0]
    t3_data = data[t3['off']:t3_next]
    print("  Section size: %d bytes" % len(t3_data))
    s = safe_str(data, t3['off'])
    print("  String: '%s'" % s)
    str_end3 = t3['off'] + len(s) + 1
    if str_end3 % 4:
        str_end3 += 4 - (str_end3 % 4)

    # After string: try as float triples (xyz)
    print("  Float data after string:")
    for i in range(0, min(192, t3_next - str_end3), 12):
        off = str_end3 + i
        if off + 12 > len(data):
            break
        x, y, z = struct.unpack_from('<3f', data, off)
        print("    @0x%04X: (%.3f, %.3f, %.3f)" % (off, x, y, z))

    # ── TOC[0]: Transforms? ──
    t0 = toc[0]
    print("\n=== TOC[0]: offset=0x%04X, count=%d ===" % (t0['off'], t0['count']))
    t0_next = sorted(t['off'] for t in toc if t['off'] > t0['off'])[0]
    t0_size = t0_next - t0['off']
    print("  Section size: %d bytes" % t0_size)
    if t0['count'] > 0:
        rec_size = t0_size // t0['count'] if t0['count'] > 0 else t0_size
        print("  Estimated record size: %d bytes" % rec_size)
    # Dump as hex
    for i in range(0, min(t0_size, 256), 16):
        off = t0['off'] + i
        chunk = data[off:off+16]
        hex_part = ' '.join('%02X' % b for b in chunk)
        print("    %04X: %s" % (i, hex_part))

    # ── Look between TOC[2] end and TOC sections: what's in 0x0514 area ──
    # TOC[5] starts at 0x0514
    t5 = toc[5]
    t5_next = sorted(t['off'] for t in toc if t['off'] > t5['off'])[0]
    t5_size = t5_next - t5['off']
    print("\n=== TOC[5]: offset=0x%04X, count=%d, size=%d ===" % (t5['off'], t5['count'], t5_size))
    if t5['count'] > 0:
        rec_size5 = t5_size // t5['count']
        print("  Record size: %d bytes" % rec_size5)
    # First record as hex + mixed interpretation
    for ri in range(t5['count']):
        if t5['count'] == 0:
            break
        roff = t5['off'] + ri * rec_size5
        print("  Record[%d] @0x%04X:" % (ri, roff))
        for j in range(0, min(rec_size5, 128), 16):
            off = roff + j
            chunk = data[off:off+16]
            hex_part = ' '.join('%02X' % b for b in chunk)
            ascii_part = ''.join(chr(b) if 32 <= b < 127 else '.' for b in chunk)
            print("    %04X: %-48s  %s" % (j, hex_part, ascii_part))
        # Named fields try
        print("    As u32 pairs:")
        for j in range(0, min(rec_size5, 128), 8):
            off = roff + j
            a, b = u32(data, off), u32(data, off + 4)
            print("      +0x%02X: %10d  %10d    (0x%08X  0x%08X)" % (j, a, b, a, b))

    # ── TOC[6]: Material data ──
    t6 = toc[6]
    t6_next = sorted(t['off'] for t in toc if t['off'] > t6['off'])[0]
    t6_size = t6_next - t6['off']
    print("\n=== TOC[6]: offset=0x%04X, count=%d, size=%d ===" % (t6['off'], t6['count'], t6_size))
    if t6['count'] > 0:
        rec_size6 = t6_size // t6['count']
        print("  Record size: %d bytes" % rec_size6)
        for ri in range(min(t6['count'], 3)):
            roff = t6['off'] + ri * rec_size6
            print("  Record[%d] @0x%04X:" % (ri, roff))
            # Find first non-zero
            first_nz = -1
            for j in range(rec_size6):
                if roff + j < len(data) and data[roff + j] != 0:
                    first_nz = j
                    break
            if first_nz >= 0:
                print("    First non-zero at offset +0x%02X" % first_nz)
                # Dump from there
                start = max(0, first_nz - 4)
                for j in range(start, min(rec_size6, first_nz + 80), 16):
                    off = roff + j
                    if off + 16 > len(data):
                        break
                    chunk = data[off:min(off+16, len(data))]
                    hex_part = ' '.join('%02X' % b for b in chunk)
                    print("    %04X: %s" % (j, hex_part))
                # Try as floats from first non-zero area
                print("    Floats from +0x%02X:" % first_nz)
                for j in range(0, min(60, rec_size6 - first_nz), 4):
                    off = roff + first_nz + j
                    if off + 4 > len(data):
                        break
                    v = f32(data, off)
                    vi = u32(data, off)
                    print("      +0x%02X: f=%.4f  u=%d  (0x%08X)" % (first_nz + j, v, vi, vi))
            else:
                print("    ALL ZEROS")

    # ── Descriptors and their data section ──
    descs = find_descriptors(data)
    print("\n=== DESCRIPTORS (found %d) ===" % len(descs))
    for i, d in enumerate(descs):
        print("  [%d] @0x%04X: prim=%d stride=%2d vc=%5d ic=%5d" %
              (i, d['off'], d['prim'], d['stride'], d['vc'], d['ic']))

    # ── Map VB/IB locations ──
    if descs:
        first_desc = descs[0]['off']
        desc_block_end = descs[-1]['off'] + 36
        print("\n  Descriptor block: 0x%04X - 0x%04X (%d bytes)" %
              (first_desc, desc_block_end, desc_block_end - first_desc))

        # After descriptors, scan for data
        print("\n  Data after descriptors:")
        scan_off = desc_block_end
        # There might be a count field or padding
        for j in range(0, 32, 4):
            off = scan_off + j
            if off + 4 > len(data):
                break
            v = u32(data, off)
            print("    @0x%04X: %d (0x%08X)" % (off, v, v))

    # ── Cross-reference: TOC[4] indices vs descriptor count ──
    print("\n=== CROSS-REFERENCE ===")
    # TOC[4] indices
    t4_indices = []
    for i in range(0, t4_size - (str_end - t4['off']), 4):
        off = str_end + i
        if off + 4 > len(data):
            break
        v = u32(data, off)
        if v > 0 and v < 1000:
            t4_indices.append(v)
        elif v == 0 and len(t4_indices) > 0:
            break
    print("  TOC[4] indices: %s" % t4_indices)
    print("  TOC[4] index range: %d - %d (%d values)" %
          (min(t4_indices) if t4_indices else 0,
           max(t4_indices) if t4_indices else 0,
           len(t4_indices)))
    print("  TOC[2] valid records: 12")
    print("  Descriptors found: %d" % len(descs))
    print("  TOC[6] count (materials?): %d" % toc[6]['count'])

    # ── Compare with a simpler ship ──
    print("\n" + "=" * 80)
    print("COMPARISON: e_bship1_h.mdx (simple battleship)")
    print("=" * 80)
    path2 = os.path.join(mdx_dir, 'e_battleship_h.mdx')
    if not os.path.exists(path2):
        # Try alternate names
        candidates = glob.glob(os.path.join(mdx_dir, 'e_battleship*_h.mdx')) + \
                     glob.glob(os.path.join(mdx_dir, 'e_bship*_h.mdx'))
        if candidates:
            path2 = candidates[0]
        else:
            print("  No simple battleship found")
            return

    with open(path2, 'rb') as f:
        data2 = f.read()
    toc2 = parse_toc(data2)
    print("  File: %s (%d bytes)" % (os.path.basename(path2), len(data2)))

    for t in toc2:
        print("  TOC[%2d]: off=0x%04X count=%3d extra=0x%04X" %
              (t['idx'], t['off'], t['count'], t['extra']))

    # TOC[4] indices for comparison ship
    t4b = toc2[4]
    t4b_next = sorted(t['off'] for t in toc2 if t['off'] > t4b['off'])[0]
    s2 = safe_str(data2, t4b['off'])
    str_end2 = t4b['off'] + len(s2) + 1
    if str_end2 % 4:
        str_end2 += 4 - (str_end2 % 4)
    print("  TOC[4] string: '%s'" % s2)
    t4b_indices = []
    for i in range(0, t4b_next - str_end2, 4):
        off = str_end2 + i
        if off + 4 > len(data2):
            break
        v = u32(data2, off)
        if v > 0 and v < 1000:
            t4b_indices.append(v)
        elif v == 0 and len(t4b_indices) > 0:
            break
    print("  TOC[4] indices: %s" % t4b_indices)
    descs2 = find_descriptors(data2)
    print("  Descriptors: %d" % len(descs2))
    for d in descs2:
        print("    @0x%04X: prim=%d stride=%2d vc=%5d ic=%5d" %
              (d['off'], d['prim'], d['stride'], d['vc'], d['ic']))
    print("  TOC[2] count: %d" % toc2[2]['count'])
    print("  TOC[6] count: %d" % toc2[6]['count'])

    # ── Compare with _n file (huge TOC[2] count) ──
    print("\n" + "=" * 80)
    print("COMPARISON: e_brunhild_n.mdx (n-variant, TOC[2] count=476)")
    print("=" * 80)
    path_n = os.path.join(mdx_dir, 'e_brunhild_n.mdx')
    if os.path.exists(path_n):
        with open(path_n, 'rb') as f:
            data_n = f.read()
        toc_n = parse_toc(data_n)
        print("  File: %s (%d bytes)" % (os.path.basename(path_n), len(data_n)))
        for t in toc_n:
            print("  TOC[%2d]: off=0x%04X count=%3d extra=0x%04X" %
                  (t['idx'], t['off'], t['count'], t['extra']))

        # TOC[2] first few records for _n variant
        t2n = toc_n[2]
        print("\n  TOC[2] first 5 records (28B each):")
        for i in range(min(5, t2n['count'])):
            off = t2n['off'] + i * 28
            if off + 28 > len(data_n):
                break
            vals = struct.unpack_from('<7I', data_n, off)
            signed1 = struct.unpack_from('<i', struct.pack('<I', vals[1]))[0]
            print("    [%3d] f0=%5d parent=%d f2=%d f3=%d f4=%5d f5=%d ptr=0x%08X" %
                  (i, vals[0], signed1, vals[2], vals[3], vals[4], vals[5], vals[6]))

        # Check if _n TOC[2] records have varying f0 values
        unique_f0 = set()
        unique_f4 = set()
        for i in range(min(t2n['count'], 500)):
            off = t2n['off'] + i * 28
            if off + 28 > len(data_n):
                break
            vals = struct.unpack_from('<7I', data_n, off)
            unique_f0.add(vals[0])
            unique_f4.add(vals[4])
        print("  Unique f0 values: %s" % sorted(unique_f0))
        print("  Unique f4 values: %s" % sorted(unique_f4))

        descs_n = find_descriptors(data_n)
        print("  Descriptors: %d" % len(descs_n))

    # ── Finally: what's between TOC[7] end and descriptors? ──
    print("\n" + "=" * 80)
    print("GAP ANALYSIS: TOC[7] end to Descriptor start (Brunhild)")
    print("=" * 80)
    t7 = toc[7]
    # Read texture strings to find end
    t7_off = t7['off']
    for i in range(t7['count']):
        s = safe_str(data, t7_off)
        t7_off += len(s) + 1
    # Descriptors start
    if descs:
        desc_start = descs[0]['off']
        gap_start = toc[8]['off']  # empty TOC slots point to data section start
        gap_size = desc_start - gap_start
        print("  Data section starts at: 0x%04X (from TOC[8-11])" % gap_start)
        print("  First descriptor at:    0x%04X" % desc_start)
        print("  Gap: %d bytes" % gap_size)
        print("  Gap contents (first 128B):")
        for j in range(0, min(gap_size, 128), 16):
            off = gap_start + j
            chunk = data[off:off+16]
            hex_part = ' '.join('%02X' % b for b in chunk)
            ascii_part = ''.join(chr(b) if 32 <= b < 127 else '.' for b in chunk)
            print("    %04X: %-48s  %s" % (j, hex_part, ascii_part))

        # Try as u32 pairs (potential sub-mesh descriptors or section headers)
        print("  As u32 values:")
        for j in range(0, min(gap_size, 128), 4):
            off = gap_start + j
            if off + 4 > len(data):
                break
            v = u32(data, off)
            vf = f32(data, off)
            if v != 0:
                print("    @0x%04X (+%03d): u32=%10d  f32=%12.4f  hex=0x%08X" %
                      (off, j, v, vf, v))


if __name__ == '__main__':
    analyze_brunhild()
