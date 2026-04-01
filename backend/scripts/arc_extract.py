#!/usr/bin/env python3
"""
ARC1 archive extractor for gin7 (Ginga Eiyuu Densetsu VII, 2004 BOTHTEC).

ARC1 format (reverse-engineered from LoGHTools + gin7 data analysis):
  Header: 128 bytes (magic "ARC1" at +0x00)
  File table: 32 bytes per entry at offset 0x80
  Filename table: null-terminated strings after file table
  Data section: LZSS-compressed file data

File entry (32 bytes, byte-scattered fields):
  Data offset:    bytes [0x04, 0x0B, 0x12, 0x19] → LE u32
  Filename offset: bytes [0x09, 0x0A] → BE u16, added to filename table base

LZSS parameters (from LoGHTools/ArcParser/LZSS.cs):
  Ring buffer: 4096 bytes, cursor starts at 0x01
  Flag byte: MSB first, bit=1 → literal, bit=0 → back-reference
  Reference: 2 bytes, offset = (b1 << 4) | (b2 >> 4), length = (b2 & 0x0F) + 2

Usage:
  python arc_extract.py <arc_file> [--out <dir>] [--raw] [--filter <glob>]
"""

import struct, argparse, fnmatch
from pathlib import Path


def lzss_decompress(data: bytes) -> bytes:
    """LZSS decompression matching LoGHTools LoGHBuffer + LoGHCompressedWord."""
    buf = bytearray(4096)
    cursor = 1
    out = bytearray()
    pos = 0

    while pos < len(data):
        flags = data[pos]; pos += 1
        for bit in range(8):
            if pos >= len(data):
                return bytes(out)
            is_ref = not ((flags >> (7 - bit)) & 1)

            if is_ref:
                if pos + 1 >= len(data):
                    return bytes(out)
                b1 = data[pos]; b2 = data[pos + 1]; pos += 2
                offset = (b1 << 4) | ((b2 & 0xF0) >> 4)
                length = (b2 & 0x0F) + 2
                for j in range(length):
                    b = buf[(offset + j) & 0xFFF]
                    out.append(b)
                    buf[cursor & 0xFFF] = b
                    cursor += 1
            else:
                b = data[pos]; pos += 1
                out.append(b)
                buf[cursor & 0xFFF] = b
                cursor += 1
    return bytes(out)


def parse_arc(data: bytes):
    """Parse ARC1 header, file table, and filename table."""
    if data[:4] != b'ARC1':
        raise ValueError(f"Not an ARC1 file (magic: {data[:4]})")

    # Collect filenames (null-terminated strings after file table)
    # File table entries are 32 bytes each starting at offset 0x80
    # Find where filename table starts by scanning for readable ASCII
    names = []
    # First, determine entry count by finding where filenames start
    # Scan from 0x80 forward until we find ASCII text
    fname_start = None
    for off in range(0x80, min(len(data), 0x10000), 32):
        # Check if this looks like a filename (printable ASCII)
        test = data[off:off + 4]
        if all(32 < b < 127 for b in test) and b'.' in data[off:off + 32]:
            fname_start = off
            break

    if fname_start is None:
        # Try: count entries = (fname_table_start - 0x80) / 32
        # Scan for first null-terminated string after 0x80
        for off in range(0x80, min(len(data), 0x20000)):
            if data[off:off + 2] in (b'e_', b'f_') and b'.mdx' in data[off:off + 40]:
                fname_start = off
                break

    if fname_start is None:
        raise ValueError("Could not find filename table")

    entry_count = (fname_start - 0x80) // 32

    # Collect filenames
    i = fname_start
    while i < len(data):
        end = data.find(b'\x00', i)
        if end < 0 or end - i > 200 or end - i == 0:
            break
        name = data[i:end].decode('ascii', errors='replace')
        if not all(c.isalnum() or c in '_.-/' for c in name):
            break
        names.append(name)
        i = end + 1

    if len(names) != entry_count:
        # Use min to be safe
        count = min(len(names), entry_count)
    else:
        count = len(names)

    # Parse file entries
    entries = []
    for idx in range(count):
        base = 0x80 + idx * 32
        e = data[base:base + 32]

        data_offset = e[0x04] | (e[0x0B] << 8) | (e[0x12] << 16) | (e[0x19] << 24)
        entries.append({
            'name': names[idx],
            'offset': data_offset,
            'index': idx,
        })

    # Calculate compressed sizes from gaps between consecutive offsets
    sorted_by_offset = sorted(enumerate(entries), key=lambda x: x[1]['offset'])
    for i, (orig_idx, entry) in enumerate(sorted_by_offset):
        if i + 1 < len(sorted_by_offset):
            next_offset = sorted_by_offset[i + 1][1]['offset']
            entries[orig_idx]['comp_size'] = next_offset - entry['offset']
        else:
            entries[orig_idx]['comp_size'] = len(data) - entry['offset']

    return entries


def extract_arc(arc_path: Path, out_dir: Path, raw=False, filter_glob=None):
    """Extract all files from an ARC1 archive."""
    data = arc_path.read_bytes()
    entries = parse_arc(data)

    if filter_glob:
        entries = [e for e in entries if fnmatch.fnmatch(e['name'], filter_glob)]

    out_dir.mkdir(parents=True, exist_ok=True)
    ok = fail = 0

    for e in entries:
        name = e['name']
        offset = e['offset']
        comp_size = e['comp_size']

        if offset + comp_size > len(data):
            print(f"  SKIP {name}: offset 0x{offset:x} + size {comp_size} exceeds file")
            fail += 1
            continue

        compressed = data[offset:offset + comp_size]

        if raw:
            result = compressed
        else:
            try:
                result = lzss_decompress(compressed)
            except Exception as ex:
                print(f"  FAIL {name}: {ex}")
                fail += 1
                continue

        out_path = out_dir / name
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_bytes(result)

        ratio = len(result) / comp_size if comp_size > 0 else 0
        print(f"  {name}: {comp_size:,} → {len(result):,} bytes ({ratio:.1f}x)")
        ok += 1

    print(f"\n=== {ok}/{ok + fail} extracted to {out_dir} ===")
    return ok, fail


def main():
    parser = argparse.ArgumentParser(description='ARC1 archive extractor (gin7)')
    parser.add_argument('input', type=Path, help='ARC1 file to extract')
    parser.add_argument('--out', type=Path, default=None, help='Output directory')
    parser.add_argument('--raw', action='store_true', help='Extract without decompression')
    parser.add_argument('--filter', type=str, default=None, help='Filename glob filter (e.g. "*_h.mdx")')
    parser.add_argument('--list', action='store_true', help='List files without extracting')
    args = parser.parse_args()

    if args.out is None:
        args.out = Path('/tmp') / args.input.stem

    data = args.input.read_bytes()
    entries = parse_arc(data)

    if args.list:
        for e in entries:
            print(f"  {e['name']:40s} offset=0x{e['offset']:08x} comp={e['comp_size']:,}")
        print(f"\n{len(entries)} files")
        return

    extract_arc(args.input, args.out, raw=args.raw, filter_glob=args.filter)


if __name__ == '__main__':
    main()
