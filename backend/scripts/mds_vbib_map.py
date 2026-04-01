#!/usr/bin/env python3
"""Map exact VB/IB locations and find hidden data between them."""
import struct, sys, os, tempfile

def u16(d, o): return struct.unpack_from('<H', d, o)[0]
def u32(d, o): return struct.unpack_from('<I', d, o)[0]
def f32(d, o): return struct.unpack_from('<f', d, o)[0]

MARKER_STRIDE = {18: 24, 65810: 36, 74002: 72, 336402: 84}

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


def find_vb_start(data, desc, search_start):
    """Find VB by scanning for valid vertex data matching the stride format."""
    stride = desc['stride']
    vc = desc['vc']
    expected_size = vc * stride

    # For s24: first vertex should have reasonable position floats
    # For s72/s84: look for W=1.0 marker
    W_ONE = struct.pack('<f', 1.0)

    if stride == 24:
        # s24: pos(xyz)@0 + normal(xyz)@12
        # Scan for first vertex with reasonable position values
        for off in range(search_start, len(data) - expected_size, 4):
            x = f32(data, off)
            y = f32(data, off + 4)
            z = f32(data, off + 8)
            # Check position is reasonable (not NaN, not huge)
            if all(abs(v) < 10000 and v == v for v in [x, y, z]):
                nx = f32(data, off + 12)
                ny = f32(data, off + 16)
                nz = f32(data, off + 20)
                # Normal should be roughly unit length
                nlen = (nx*nx + ny*ny + nz*nz) ** 0.5
                if 0.5 < nlen < 1.5:
                    return off
        return None

    elif stride == 72:
        # s72: W=1.0 at offset +32 in each vertex
        for off in range(search_start, len(data) - expected_size, 4):
            if data[off+32:off+36] == W_ONE:
                # Verify a few more vertices
                ok = True
                for v in range(1, min(5, vc)):
                    if data[off + v*72 + 32 : off + v*72 + 36] != W_ONE:
                        ok = False
                        break
                if ok:
                    return off
        return None

    elif stride == 84:
        # s84: W=1.0 at offset +0 in each vertex
        for off in range(search_start, len(data) - expected_size, 4):
            if data[off:off+4] == W_ONE:
                ok = True
                for v in range(1, min(5, vc)):
                    if data[off + v*84 : off + v*84 + 4] != W_ONE:
                        ok = False
                        break
                if ok:
                    return off
        return None

    elif stride == 36:
        # s36: pos(xyz)@0 + normal(xyz)@12 + uv(st)@24
        for off in range(search_start, len(data) - expected_size, 4):
            x = f32(data, off)
            y = f32(data, off + 4)
            z = f32(data, off + 8)
            if all(abs(v) < 10000 and v == v for v in [x, y, z]):
                u = f32(data, off + 24)
                v_val = f32(data, off + 28)
                if -2.0 <= u <= 3.0 and -2.0 <= v_val <= 3.0:
                    return off
        return None

    return None


def analyze_ib(data, ib_off, desc, max_extra=65536):
    """Analyze IB data: declared count vs actual usable indices."""
    vc = desc['vc']
    ic = desc['ic']
    prim = desc['prim']
    stride = desc['stride']

    # Read declared indices
    declared_indices = []
    for i in range(ic):
        idx = u16(data, ib_off + i * 2)
        declared_indices.append(idx)

    max_idx = max(declared_indices) if declared_indices else 0
    valid_count = sum(1 for idx in declared_indices if idx < vc)

    print("    Declared IB: %d indices, max_idx=%d, valid(<%d)=%d" %
          (ic, max_idx, vc, valid_count))

    if prim == 0:  # triangle list
        print("    Triangle list: %d triangles" % (ic // 3))
    elif prim == 1:  # triangle strip
        # Count strip restarts (index >= vc)
        restarts = sum(1 for idx in declared_indices if idx >= vc)
        print("    Triangle strip: %d restarts (OOB markers)" % restarts)

    # Scan BEYOND declared IB for additional index data
    declared_end = ib_off + ic * 2
    extra_indices = []
    extra_valid = 0
    for i in range(max_extra):
        off = declared_end + i * 2
        if off + 2 > len(data):
            break
        idx = u16(data, off)
        # Check if this still looks like index data (values < vc or restart marker)
        if idx < vc or (prim == 1 and idx < 65535):
            extra_indices.append(idx)
            if idx < vc:
                extra_valid += 1
        else:
            # Could be start of next VB (check for float-like patterns)
            if off + 4 <= len(data):
                v = f32(data, off)
                # W=1.0 or reasonable float could mean VB start
                if abs(v) < 10000 or v != v:  # nan check
                    break
            break

    # Better approach: scan until we hit a pattern that looks like vertex data
    # For this, look for W=1.0 pattern or reasonable float sequences
    scan_off = declared_end
    actual_ib_end = declared_end
    consecutive_valid = 0
    WINDOW = 20  # look at 20 consecutive u16s

    for i in range(0, max_extra * 2, 2):
        off = declared_end + i
        if off + 2 > len(data):
            break
        idx = u16(data, off)
        if idx < vc:
            consecutive_valid += 1
        elif prim == 1 and idx >= vc:
            # Could be strip restart or garbage
            consecutive_valid = 0
        else:
            consecutive_valid = 0

        # If we see a lot of non-index values, stop
        if consecutive_valid == 0:
            # Check if this 4-byte aligned offset contains W=1.0
            aligned = off & ~3
            if aligned + 4 <= len(data):
                val = struct.unpack_from('<f', data, aligned)[0]
                if val == 1.0:
                    actual_ib_end = off
                    break
            # Check for 3 consecutive float-like values (vertex start)
            if off + 12 <= len(data) and off % 4 == 0:
                f1 = f32(data, off)
                f2 = f32(data, off + 4)
                f3 = f32(data, off + 8)
                if all(abs(v) < 10000 and v == v for v in [f1, f2, f3]):
                    actual_ib_end = off
                    break
        actual_ib_end = off + 2

    extra_bytes = actual_ib_end - declared_end
    extra_count = extra_bytes // 2

    if extra_count > 0:
        print("    *** EXTRA IB DATA: %d bytes (%d indices) after declared end ***" %
              (extra_bytes, extra_count))
        print("    First 20 extra indices: %s" %
              [u16(data, declared_end + i*2) for i in range(min(20, extra_count))])
        print("    Total IB (declared+extra): %d indices" % (ic + extra_count))
    else:
        print("    No extra IB data found")

    return {
        'declared_ic': ic,
        'extra_ic': extra_count,
        'total_ic': ic + extra_count,
        'ib_start': ib_off,
        'ib_end': actual_ib_end,
    }


def main():
    mdx_dir = os.path.join(tempfile.gettempdir(), 'warship_mdx')
    path = os.path.join(mdx_dir, 'e_brunhild_h.mdx')
    with open(path, 'rb') as f:
        data = f.read()

    print("=" * 80)
    print("VB/IB MAPPING: e_brunhild_h.mdx (%d bytes)" % len(data))
    print("=" * 80)

    descs = find_descriptors(data)
    print("\nDescriptors found: %d" % len(descs))
    for i, d in enumerate(descs):
        print("  [%d] @0x%04X prim=%d stride=%2d vc=%5d ic=%5d" %
              (i, d['off'], d['prim'], d['stride'], d['vc'], d['ic']))

    # Map each descriptor's VB and IB
    desc_block_end = descs[-1]['off'] + 36 if descs else 0
    print("\nDescriptor block ends at: 0x%04X" % desc_block_end)

    # Dump what's between desc block end and first VB
    print("\nData between descriptors and first VB:")
    scan_start = desc_block_end
    for j in range(0, 128, 4):
        off = scan_start + j
        if off + 4 > len(data):
            break
        v = u32(data, off)
        vf = f32(data, off)
        print("  @0x%04X: u32=%10d  f32=%12.4f  hex=0x%08X" % (off, v, vf, v))

    # Find VB starts
    print("\n" + "=" * 80)
    print("VB/IB LOCATIONS")
    print("=" * 80)

    search_from = desc_block_end
    regions = []

    for i, d in enumerate(descs):
        print("\n--- Descriptor[%d]: stride=%d, vc=%d, ic=%d, prim=%d ---" %
              (i, d['stride'], d['vc'], d['ic'], d['prim']))

        vb_start = find_vb_start(data, d, search_from)
        if vb_start is None:
            print("  VB NOT FOUND (searched from 0x%04X)" % search_from)
            continue

        vb_size = d['vc'] * d['stride']
        vb_end = vb_start + vb_size
        print("  VB: 0x%04X - 0x%04X (%d bytes, %d vertices x %d stride)" %
              (vb_start, vb_end, vb_size, d['vc'], d['stride']))

        # Gap between search_from and VB start
        gap = vb_start - search_from
        if gap > 0:
            print("  Gap before VB: %d bytes (0x%04X - 0x%04X)" % (gap, search_from, vb_start))
            # Dump first bytes of gap
            print("  Gap contents:")
            for j in range(0, min(gap, 64), 4):
                off = search_from + j
                v = u32(data, off)
                print("    @0x%04X: 0x%08X (%d)" % (off, v, v))

        # IB immediately after VB
        ib_off = vb_end
        print("  IB start: 0x%04X" % ib_off)

        ib_info = analyze_ib(data, ib_off, d)
        regions.append({
            'desc_idx': i,
            'vb_start': vb_start,
            'vb_end': vb_end,
            'ib_start': ib_off,
            'ib_end': ib_info['ib_end'],
            'stride': d['stride'],
        })

        search_from = ib_info['ib_end']
        # Align to 4 bytes
        if search_from % 4:
            search_from += 4 - (search_from % 4)

    # After all known VB/IB: what remains?
    print("\n" + "=" * 80)
    print("REMAINING DATA AFTER LAST IB")
    print("=" * 80)
    remaining = len(data) - search_from
    print("  From 0x%04X to end: %d bytes" % (search_from, remaining))

    if remaining > 0:
        # Check if there's another VB/IB block
        print("  First 128 bytes:")
        for j in range(0, min(remaining, 128), 16):
            off = search_from + j
            chunk = data[off:off+16]
            hex_part = ' '.join('%02X' % b for b in chunk)
            ascii_part = ''.join(chr(b) if 32 <= b < 127 else '.' for b in chunk)
            print("    %04X: %-48s  %s" % (off, hex_part, ascii_part))

        # Check for additional descriptors
        extra_descs = find_descriptors(data, search_from)
        if extra_descs:
            print("\n  *** ADDITIONAL DESCRIPTORS FOUND! ***")
            for d in extra_descs:
                print("    @0x%04X: prim=%d stride=%2d vc=%5d ic=%5d" %
                      (d['off'], d['prim'], d['stride'], d['vc'], d['ic']))

        # Check for W=1.0 pattern (start of s72 or s84 VB)
        W_ONE = struct.pack('<f', 1.0)
        print("\n  W=1.0 occurrences in remaining data:")
        w_count = 0
        for j in range(0, remaining - 4, 4):
            off = search_from + j
            if data[off:off+4] == W_ONE:
                w_count += 1
                if w_count <= 10:
                    print("    @0x%04X (remaining+%d)" % (off, j))
        print("    Total: %d" % w_count)

        # Try to interpret as more IB data for the last descriptor
        last_desc = descs[-1]
        last_vc = last_desc['vc']
        print("\n  Trying remaining data as extra IB (vc=%d):" % last_vc)
        valid_extra_indices = 0
        oob_count = 0
        total_scanned = 0
        for j in range(0, min(remaining, 100000), 2):
            off = search_from + j
            if off + 2 > len(data):
                break
            idx = u16(data, off)
            total_scanned += 1
            if idx < last_vc:
                valid_extra_indices += 1
            elif idx >= last_vc:
                oob_count += 1
        print("    Scanned %d u16 values: %d valid indices (< %d), %d OOB" %
              (total_scanned, valid_extra_indices, last_vc, oob_count))
        if total_scanned > 0:
            pct = valid_extra_indices * 100.0 / total_scanned
            print("    Valid index ratio: %.1f%%" % pct)

    # Summary
    print("\n" + "=" * 80)
    print("BYTE ACCOUNTING")
    print("=" * 80)
    total_mapped = 0
    for r in regions:
        size = r['ib_end'] - r['vb_start']
        total_mapped += size
        print("  Desc[%d] s%d: VB+IB = 0x%04X..0x%04X (%d bytes)" %
              (r['desc_idx'], r['stride'], r['vb_start'], r['ib_end'], size))

    header = regions[0]['vb_start'] if regions else 0
    print("  Header: 0x0000..0x%04X (%d bytes)" % (header, header))
    print("  Mapped data: %d bytes" % total_mapped)
    print("  Total: %d / %d bytes (%.1f%%)" %
          (header + total_mapped, len(data),
           (header + total_mapped) * 100.0 / len(data)))
    print("  Unmapped: %d bytes (%.1f%%)" %
          (len(data) - header - total_mapped,
           (len(data) - header - total_mapped) * 100.0 / len(data)))


if __name__ == '__main__':
    main()
