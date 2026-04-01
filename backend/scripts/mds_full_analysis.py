#!/usr/bin/env python3
"""Comprehensive MDS structure analysis across multiple ships."""
import struct, sys, os, glob

def u16(d, o): return struct.unpack_from('<H', d, o)[0]
def u32(d, o): return struct.unpack_from('<I', d, o)[0]
def f32(d, o): return struct.unpack_from('<f', d, o)[0]
def i32(d, o): return struct.unpack_from('<i', d, o)[0]

MARKER_STRIDE = {18: 24, 65810: 36, 74002: 72, 336402: 84}

def parse_toc(data):
    entries = []
    for i in range(12):
        off = i * 8
        offset, magic, count, extra = struct.unpack_from('<4H', data, off)
        entries.append({'idx': i, 'off': offset, 'magic': magic, 'count': count, 'extra': extra})
    return entries

def safe_str(data, off, maxlen=256):
    """Extract null-terminated ASCII string safely."""
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
            result.append('?')
    return ''.join(result)

def find_descriptors(data, start_off=0):
    """Find all 36B descriptors by marker/stride pattern."""
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
            'ptr_vb': u32(data, off + 4),
            'ptr_ib': u32(data, off + 12),
            'ptr_extra': u32(data, off + 20),
        })
    return descs

def analyze_one(path, verbose=False):
    with open(path, 'rb') as f:
        data = f.read()

    name = os.path.basename(path)
    toc = parse_toc(data)

    result = {'name': name, 'size': len(data), 'toc': toc}

    # TOC[2] records
    t2 = toc[2]
    records = []
    for i in range(t2['count']):
        off = t2['off'] + i * 28
        if off + 28 > len(data):
            break
        vals = struct.unpack_from('<7I', data, off)
        records.append({'off': off, 'u32': vals})
    result['toc2_records'] = records

    # TOC[1] - filenames
    t1 = toc[1]
    result['filenames'] = []
    off = t1['off']
    for i in range(t1['count']):
        s = safe_str(data, off)
        result['filenames'].append(s)
        off += len(s) + 1

    # TOC[7] - textures
    t7 = toc[7]
    result['textures'] = []
    off = t7['off']
    for i in range(t7['count']):
        s = safe_str(data, off)
        result['textures'].append(s)
        off += len(s) + 1

    # TOC[3] - what is this? dump raw
    t3 = toc[3]
    if t3['count'] > 0:
        result['toc3_data'] = data[t3['off']:t3['off'] + min(128, t3['count'] * 64)]

    # TOC[4] - dump
    t4 = toc[4]
    if t4['count'] > 0:
        result['toc4_data'] = data[t4['off']:t4['off'] + min(256, t4['count'] * 64)]

    # TOC[5] - dump
    t5 = toc[5]
    if t5['count'] > 0:
        result['toc5_data'] = data[t5['off']:t5['off'] + min(512, t5['count'] * 64)]

    # TOC[6] - dump
    t6 = toc[6]
    if t6['count'] > 0:
        result['toc6_data'] = data[t6['off']:t6['off'] + min(512, t6['count'] * 128)]

    # Find descriptors
    result['descriptors'] = find_descriptors(data)

    return result

def print_hex_block(data, label="", width=16):
    for i in range(0, len(data), width):
        chunk = data[i:i+width]
        hex_part = ' '.join('%02X' % b for b in chunk)
        ascii_part = ''.join(chr(b) if 32 <= b < 127 else '.' for b in chunk)
        print("    %04X: %-48s  %s" % (i, hex_part, ascii_part))

def main():
    mdx_dir = sys.argv[1] if len(sys.argv) > 1 else '/tmp/warship_mdx'

    # Analyze multiple ships for comparison
    targets = [
        'e_brunhild_h.mdx',   # Brunhild (flagship)
        'e_bship1_h.mdx',     # Empire battleship
        'f_bship1_h.mdx',     # Alliance battleship
        'e_dship1_h.mdx',     # Empire destroyer
    ]

    # Find what files actually exist
    all_mdx = sorted(glob.glob(os.path.join(mdx_dir, '*.mdx')))

    print("=" * 80)
    print("MDS STRUCTURE ANALYSIS")
    print("=" * 80)
    print("Total MDX files: %d" % len(all_mdx))
    print()

    # Quick survey: TOC[2] count distribution
    print("=== TOC[2] count distribution across all ships ===")
    count_dist = {}
    for path in all_mdx:
        with open(path, 'rb') as f:
            d = f.read(96)  # just TOC
        t2_count = struct.unpack_from('<H', d, 2*8+4)[0]
        count_dist.setdefault(t2_count, []).append(os.path.basename(path))
    for count in sorted(count_dist.keys()):
        files = count_dist[count]
        print("  count=%2d: %3d files  (e.g. %s)" % (count, len(files), ', '.join(files[:3])))
    print()

    # Quick survey: TOC[6] count distribution
    print("=== TOC[6] count distribution ===")
    count_dist6 = {}
    for path in all_mdx:
        with open(path, 'rb') as f:
            d = f.read(96)
        t6_count = struct.unpack_from('<H', d, 6*8+4)[0]
        count_dist6.setdefault(t6_count, []).append(os.path.basename(path))
    for count in sorted(count_dist6.keys()):
        files = count_dist6[count]
        print("  count=%2d: %3d files  (e.g. %s)" % (count, len(files), ', '.join(files[:3])))
    print()

    # Detailed analysis of target ships
    for tgt in targets:
        path = os.path.join(mdx_dir, tgt)
        if not os.path.exists(path):
            print("SKIP: %s not found" % tgt)
            continue

        r = analyze_one(path, verbose=True)
        print("=" * 80)
        print("SHIP: %s (%d bytes)" % (r['name'], r['size']))
        print("=" * 80)

        # TOC overview
        print("\n--- TOC ---")
        for t in r['toc']:
            print("  [%2d] off=0x%04X  magic=0x%04X  count=%3d  extra=0x%04X" %
                  (t['idx'], t['off'], t['magic'], t['count'], t['extra']))

        # Filenames
        print("\n--- Filenames (TOC[1]) ---")
        for s in r['filenames']:
            print("  %s" % s)

        # Textures
        print("\n--- Textures (TOC[7]) ---")
        for s in r['textures']:
            print("  %s" % s)

        # TOC[2] records
        print("\n--- TOC[2] records (28B each) ---")
        recs = r['toc2_records']
        # Analyze pointer deltas
        ptrs = [rec['u32'][6] for rec in recs]
        for i, rec in enumerate(recs):
            v = rec['u32']
            delta_str = ""
            if i > 0 and ptrs[i] > ptrs[i-1] and ptrs[i] < 0x10000000:
                delta_str = "  delta=%d(0x%X)" % (ptrs[i] - ptrs[i-1], ptrs[i] - ptrs[i-1])
            # Check if last field looks like ASCII
            last4 = struct.pack('<I', v[6])
            if all(32 <= b < 127 for b in last4 if b != 0):
                ascii_note = " ASCII='%s'" % last4.decode('ascii', errors='replace').rstrip('\x00')
            else:
                ascii_note = ""
            print("  [%2d] %3d  %10d  %3d  %3d  %3d  %3d  0x%08X%s%s" %
                  (i, v[0], i32(r['toc2_records'][i]['off'] + 4 if False else 0, 0) if False else v[1],
                   v[2], v[3], v[4], v[5], v[6], delta_str, ascii_note))

        # Simpler TOC[2] print
        print("\n  Simplified TOC[2]:")
        for i, rec in enumerate(recs):
            v = rec['u32']
            signed1 = struct.unpack_from('<i', struct.pack('<I', v[1]))[0]
            print("  [%2d] field0=%3d  parent=%d  f2=%d  f3=%d  f4=%3d  f5=%d  ptr=0x%08X" %
                  (i, v[0], signed1, v[2], v[3], v[4], v[5], v[6]))

        # Pointer analysis
        valid_ptrs = [p for p in ptrs if 0x01000000 < p < 0x10000000]
        if len(valid_ptrs) >= 2:
            deltas = [valid_ptrs[i+1] - valid_ptrs[i] for i in range(len(valid_ptrs)-1)]
            print("\n  Pointer deltas: %s" % ', '.join('%d(0x%X)' % (d, d) for d in deltas))
            if len(set(deltas)) == 1:
                print("  ** UNIFORM delta: %d (0x%X) **" % (deltas[0], deltas[0]))

        # TOC[3] data
        if 'toc3_data' in r:
            print("\n--- TOC[3] data (count=%d, extra=0x%04X) ---" % (r['toc'][3]['count'], r['toc'][3]['extra']))
            print_hex_block(r['toc3_data'])

        # TOC[4] data
        if 'toc4_data' in r:
            print("\n--- TOC[4] data (count=%d) ---" % r['toc'][4]['count'])
            print_hex_block(r['toc4_data'])

        # TOC[5] data
        if 'toc5_data' in r:
            print("\n--- TOC[5] data (count=%d) ---" % r['toc'][5]['count'])
            print_hex_block(r['toc5_data'])
            # Try as floats
            nf = min(32, len(r['toc5_data']) // 4)
            if nf:
                print("  as f32:", end="")
                for j in range(nf):
                    v = struct.unpack_from('<f', r['toc5_data'], j*4)[0]
                    print(" %8.3f" % v, end="")
                print()

        # TOC[6] data
        if 'toc6_data' in r:
            print("\n--- TOC[6] data (count=%d) ---" % r['toc'][6]['count'])
            print_hex_block(r['toc6_data'])
            # Try each record as floats (guess record size from data span)
            t6 = r['toc'][6]
            # Next TOC section offset
            next_off = min(t['off'] for t in r['toc'] if t['off'] > t6['off'] and t['count'] >= 0)
            rec_total = next_off - t6['off']
            if t6['count'] > 0:
                rec_size = rec_total // t6['count']
                print("  Estimated record size: %d bytes (total=%d / count=%d)" %
                      (rec_size, rec_total, t6['count']))
                for i in range(t6['count']):
                    off = i * rec_size
                    nf = min(rec_size // 4, 32)
                    vals = struct.unpack_from('<%df' % nf, r['toc6_data'], off)
                    print("  rec[%d] f32: %s" % (i, '  '.join('%8.3f' % v for v in vals)))
                    vals_u32 = struct.unpack_from('<%dI' % nf, r['toc6_data'], off)
                    print("         u32: %s" % '  '.join('%8d' % v for v in vals_u32))

        # Descriptors
        print("\n--- Descriptors ---")
        for d in r['descriptors']:
            print("  @0x%04X: prim=%d  vc=%5d  ic=%5d  stride=%2d  marker=%d" %
                  (d['off'], d['prim'], d['vc'], d['ic'], d['stride'], d['marker']))
            print("           ptr_vb=0x%08X  ptr_ib=0x%08X  ptr_extra=0x%08X" %
                  (d['ptr_vb'], d['ptr_ib'], d['ptr_extra']))

        # Relationship: TOC[2] count vs descriptor count vs TOC[6] count
        print("\n--- Counts summary ---")
        print("  TOC[2] records: %d (excl string tail)" % len([r for r in recs if r['u32'][1] == 0xFFFFFFFF and r['u32'][6] > 0x01000000]))
        print("  TOC[6] count:   %d" % r['toc'][6]['count'])
        print("  Descriptors:    %d" % len(r['descriptors']))
        print("  Textures:       %d" % len(r['textures']))
        print()

    # Cross-ship comparison: TOC[2] field[0] values
    print("\n" + "=" * 80)
    print("CROSS-SHIP TOC[2] FIELD COMPARISON")
    print("=" * 80)

    sample_ships = all_mdx[:20]  # first 20 ships
    for path in sample_ships:
        with open(path, 'rb') as f:
            data = f.read()
        toc = parse_toc(data)
        t2 = toc[2]
        name = os.path.basename(path)

        fields0 = []
        fields4 = []
        n_valid = 0
        for i in range(t2['count']):
            off = t2['off'] + i * 28
            if off + 28 > len(data):
                break
            vals = struct.unpack_from('<7I', data, off)
            if vals[1] == 0xFFFFFFFF and vals[6] > 0x01000000:
                n_valid += 1
                fields0.append(vals[0])
                fields4.append(vals[4])

        descs = find_descriptors(data)
        total_vc = sum(d['vc'] for d in descs)
        total_ic = sum(d['ic'] for d in descs)

        print("  %-25s  toc2=%2d(valid=%2d)  f0=%s  f4=%s  descs=%d  vc=%5d  ic=%5d  t6=%d" %
              (name, t2['count'], n_valid,
               ','.join(str(v) for v in set(fields0)),
               ','.join(str(v) for v in set(fields4)),
               len(descs), total_vc, total_ic, toc[6]['count']))

if __name__ == '__main__':
    main()
