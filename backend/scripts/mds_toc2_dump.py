#!/usr/bin/env python3
"""Hex dump TOC[2] 28B records from MDS/MDX files for reverse engineering."""
import struct, sys

def main():
    path = sys.argv[1] if len(sys.argv) > 1 else '/tmp/warship_mdx/e_brunhild_h.mdx'
    with open(path, 'rb') as f:
        data = f.read()

    print("File: %s (%d bytes)" % (path, len(data)))
    print()

    # Parse TOC: 12 entries x 8B = 96B
    print("=== TOC (12 entries x 8B) ===")
    toc = []
    for i in range(12):
        off = i * 8
        offset, magic, count, extra = struct.unpack_from('<4H', data, off)
        toc.append((offset, magic, count, extra))
        print("  TOC[%2d]: offset=0x%04X(%5d)  magic=0x%04X  count=%3d  extra=0x%04X(%d)" %
              (i, offset, offset, magic, count, extra, extra))

    print()

    # TOC[2] details
    toc2_off, toc2_magic, toc2_count, toc2_extra = toc[2]
    print("=== TOC[2]: offset=0x%04X, count=%d, record_size=28B ===" % (toc2_off, toc2_count))
    print("    Total bytes: %d" % (toc2_count * 28))
    print()

    # Hex dump each 28B record
    for i in range(toc2_count):
        rec_off = toc2_off + i * 28
        rec = data[rec_off:rec_off+28]

        # Raw hex
        hex_str = ' '.join('%02X' % b for b in rec)
        print("  Record[%2d] @0x%04X: %s" % (i, rec_off, hex_str))

        # As 7 x u32
        u32s = struct.unpack_from('<7I', data, rec_off)
        print("    u32: %s" % "  ".join("%10d" % v for v in u32s))
        print("    hex: %s" % "  ".join("0x%08X" % v for v in u32s))

        # As 14 x u16
        u16s = struct.unpack_from('<14H', data, rec_off)
        print("    u16: %s" % "  ".join("%5d" % v for v in u16s))

        # As 7 x f32
        f32s = struct.unpack_from('<7f', data, rec_off)
        print("    f32: %s" % "  ".join("%12.4f" % v for v in f32s))

        # As mixed: try u16 pairs, i16 pairs
        i16s = struct.unpack_from('<14h', data, rec_off)
        print("    i16: %s" % "  ".join("%6d" % v for v in i16s))
        print()

    # Also dump surrounding context: what's before TOC[2] data and after
    print("=== Context around TOC[2] ===")
    before_off = max(0, toc2_off - 32)
    print("  Before TOC[2] (32B @0x%04X):" % before_off)
    chunk = data[before_off:toc2_off]
    print("    " + ' '.join('%02X' % b for b in chunk))

    after_off = toc2_off + toc2_count * 28
    print("  After TOC[2] (64B @0x%04X):" % after_off)
    chunk = data[after_off:after_off+64]
    print("    " + ' '.join('%02X' % b for b in chunk))

    # Cross-reference: what do TOC[0], TOC[1], TOC[6], TOC[7] contain?
    print()
    print("=== TOC[0] data (first 64B) @0x%04X ===" % toc[0][0])
    t0_off = toc[0][0]
    chunk = data[t0_off:t0_off+min(64, toc[0][2]*8 if toc[0][2] else 64)]
    print("    " + ' '.join('%02X' % b for b in chunk))
    # Try as floats
    nf = min(16, len(chunk)//4)
    if nf:
        fs = struct.unpack_from('<%df' % nf, data, t0_off)
        print("    f32: %s" % "  ".join("%10.4f" % v for v in fs))

    print()
    print("=== TOC[1] data (filename) @0x%04X ===" % toc[1][0])
    t1_off = toc[1][0]
    t1_end = t1_off + 256
    chunk = data[t1_off:t1_end]
    # Find null-terminated strings
    strings = []
    s = b''
    for b in chunk:
        if b == 0:
            if s:
                strings.append(s.decode('ascii', errors='replace'))
                s = b''
        else:
            s += bytes([b])
    print("    Strings: %s" % strings[:5])

    print()
    print("=== TOC[6] data (first 128B) @0x%04X, count=%d ===" % (toc[6][0], toc[6][2]))
    t6_off = toc[6][0]
    chunk = data[t6_off:t6_off+128]
    print("    hex: " + ' '.join('%02X' % b for b in chunk))
    nf = min(32, len(chunk)//4)
    if nf:
        fs = struct.unpack_from('<%df' % nf, data, t6_off)
        print("    f32: %s" % "  ".join("%8.3f" % v for v in fs))

    print()
    print("=== TOC[7] data (textures) @0x%04X ===" % toc[7][0])
    t7_off = toc[7][0]
    chunk = data[t7_off:t7_off+512]
    strings = []
    s = b''
    for b in chunk:
        if b == 0:
            if s:
                strings.append(s.decode('ascii', errors='replace'))
                s = b''
        else:
            s += bytes([b])
    print("    Texture paths: %s" % strings[:10])

if __name__ == '__main__':
    main()
