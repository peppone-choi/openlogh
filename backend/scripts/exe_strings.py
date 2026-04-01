#!/usr/bin/env python3
"""Search ginei.exe for MDS format related strings and constants."""
import re, struct, sys

def main():
    path = 'logh-game/installed/ginei.exe'
    with open(path, 'rb') as f:
        data = f.read()

    print("EXE size: %d bytes (%.1f MB)" % (len(data), len(data)/1024/1024))

    # Extract ASCII strings >= 4 chars
    all_strings = re.findall(rb'[\x20-\x7e]{4,}', data)
    print("Total strings: %d" % len(all_strings))

    # Search patterns
    patterns = [
        (rb'(?i)\.mds|\.mdx', 'MDS/MDX files'),
        (rb'(?i)stride|vertex|vbuf|ibuf|vertexbuf|indexbuf', 'Vertex/Index Buffer'),
        (rb'(?i)d3d|direct3d|drawprim|drawindex|SetStream|CreateVertex|CreateIndex', 'D3D API'),
        (rb'(?i)tristrip|trilist|triangle', 'Primitive type'),
        (rb'(?i)body_|_body|mesh|node_|_node|scene|material|part_|_part', 'Scene/Mesh'),
        (rb'(?i)texture|\.dds|\.tga|\.bmp', 'Texture'),
        (rb'(?i)pool|combined|merge|shared', 'Pool/Combine'),
        (rb'(?i)mirror|flip|reflect|symmet', 'Mirror/Symmetry'),
        (rb'(?i)warship|ship|fleet|battle', 'Ship related'),
        (rb'(?i)\.arc|archive|lzss|decomp', 'Archive'),
    ]

    for pat, label in patterns:
        matches = [s.decode('ascii', errors='replace') for s in all_strings if re.search(pat, s)]
        unique = sorted(set(matches))
        if unique:
            print("\n--- %s (%d unique) ---" % (label, len(unique)))
            for s in unique[:25]:
                print("  %s" % s)

    # Search for known marker constants as u32 in CODE section (not data)
    print("\n=== Known MDS marker constants ===")
    markers = {18: 's24_marker', 65810: 's36_marker', 74002: 's72_marker', 336402: 's84_marker'}
    for val, name in sorted(markers.items()):
        packed = struct.pack('<I', val)
        positions = []
        start = 0
        while True:
            pos = data.find(packed, start)
            if pos == -1:
                break
            positions.append(pos)
            start = pos + 1
        print("  %s (%d = 0x%X): %d occurrences" % (name, val, val, len(positions)))
        # Show first few in code section (< 0x400000 typically)
        code_pos = [p for p in positions if p < 0x200000]
        if code_pos:
            for p in code_pos[:3]:
                ctx_before = data[max(0,p-16):p]
                ctx_after = data[p:p+8]
                print("    @0x%06X: ...%s [%s] %s..." % (
                    p,
                    ' '.join('%02X' % b for b in ctx_before[-8:]),
                    ' '.join('%02X' % b for b in ctx_after[:4]),
                    ' '.join('%02X' % b for b in ctx_after[4:8]),
                ))

    # Search for DrawIndexedPrimitive patterns
    # In D3D8/9, the call sequence is:
    #   push PrimitiveCount
    #   push StartIndex
    #   push MinVertexIndex
    #   push NumVertices
    #   push BaseVertexIndex
    #   push PrimitiveType (4=trilist, 5=tristrip)
    #   mov eax, [device_ptr]
    #   call [eax+vtable_offset]
    print("\n=== D3D primitive type push patterns ===")
    # Look for 'push 4' or 'push 5' near DrawIndexedPrimitive-like code
    # push imm8: 6A XX
    # push imm32: 68 XX XX XX XX
    for ptype, pname in [(4, 'TRIANGLELIST'), (5, 'TRIANGLESTRIP')]:
        # push byte 4/5 = 6A 04/05
        pat = bytes([0x6A, ptype])
        count = data.count(pat)
        print("  push %d (%s): %d occurrences of 6A %02X" % (ptype, pname, count, ptype))

    # Look for FVF constants
    # D3DFVF_XYZ = 0x002
    # D3DFVF_NORMAL = 0x010
    # D3DFVF_TEX1 = 0x100
    # D3DFVF_XYZ|D3DFVF_NORMAL = 0x012
    # D3DFVF_XYZ|D3DFVF_NORMAL|D3DFVF_TEX1 = 0x112
    print("\n=== D3D FVF constants ===")
    fvf_vals = {
        0x012: 'XYZ|NORMAL (s24)',
        0x112: 'XYZ|NORMAL|TEX1 (s36)',
        0x312: 'XYZ|NORMAL|TEX2',
        0x012 | 0x100: 'XYZ|NORMAL|TEX1',
    }
    for fvf, name in sorted(fvf_vals.items()):
        packed = struct.pack('<I', fvf)
        positions = []
        start = 0
        while True:
            pos = data.find(packed, start)
            if pos == -1:
                break
            positions.append(pos)
            start = pos + 1
        code_pos = [p for p in positions if p < 0x200000]
        print("  FVF 0x%03X (%s): %d total, %d in code" % (fvf, name, len(positions), len(code_pos)))

    # Search for the number 28 (record size) near MDS-related code
    # And 36 (descriptor size)
    print("\n=== Key size constants ===")
    for val, name in [(28, 'TOC2 record size'), (36, 'Descriptor size'), (232, 'TOC0 record size'), (676, 'TOC6 record size'), (384, 'TOC5 record size'), (656, 'Runtime node size')]:
        packed = struct.pack('<I', val)
        count = data[:0x200000].count(packed)
        print("  %d (0x%X) %s: %d in code section" % (val, val, name, count))

    # Look for "body_" string and its cross-references
    print("\n=== 'body' string search ===")
    body_matches = [s.decode('ascii', errors='replace') for s in all_strings if b'body' in s.lower()]
    for s in sorted(set(body_matches))[:20]:
        print("  '%s'" % s)

    # Raw byte search for "body_"
    pos = 0
    body_positions = []
    while True:
        pos = data.find(b'body_', pos)
        if pos == -1:
            break
        body_positions.append(pos)
        pos += 1
    print("  'body_' found at %d positions" % len(body_positions))
    for p in body_positions[:10]:
        ctx = data[p:p+20]
        s = ctx.split(b'\x00')[0].decode('ascii', errors='replace')
        print("    @0x%06X: '%s'" % (p, s))


if __name__ == '__main__':
    main()
