#!/usr/bin/env python3
"""Dump MDS/MDX structure into JSON for repeatable reverse engineering.

Focus:
- TOC entries and raw section boundaries
- body_a/body_b string occurrences
- TOC[2] 28-byte windows
- TOC[4] mixed string/u32 payload
- standard 36-byte and 40-byte descriptor candidates

This script intentionally avoids strong semantic claims. It records what is
present in the file so later analysis can compare multiple ships consistently.
"""

from __future__ import annotations

import argparse
import json
import struct
from pathlib import Path


MARKER_STRIDE = {18: 24, 65810: 36, 74002: 72, 336402: 84}
BODY_NAMES = (b"body_a", b"body_b")


def u16(data: bytes, off: int) -> int:
    return struct.unpack_from("<H", data, off)[0]


def u32(data: bytes, off: int) -> int:
    return struct.unpack_from("<I", data, off)[0]


def parse_toc(data: bytes) -> list[dict[str, int]]:
    entries = []
    for i in range(12):
        off = i * 8
        offset, magic, count, extra = struct.unpack_from("<4H", data, off)
        entries.append(
            {
                "idx": i,
                "offset": offset,
                "magic": magic,
                "count": count,
                "extra": extra,
            }
        )
    return entries


def section_bounds(toc: list[dict[str, int]], data_len: int) -> list[dict[str, int]]:
    offsets = sorted({entry["offset"] for entry in toc if 0 < entry["offset"] < data_len})
    out = []
    for entry in toc:
        start = entry["offset"]
        next_offsets = [off for off in offsets if off > start]
        end = next_offsets[0] if next_offsets else data_len
        out.append(
            {
                "idx": entry["idx"],
                "start": start,
                "end": end,
                "size": max(0, end - start),
            }
        )
    return out


def read_c_string(data: bytes, off: int, max_len: int = 256) -> str:
    buf = []
    for i in range(max_len):
        if off + i >= len(data):
            break
        b = data[off + i]
        if b == 0:
            break
        if 32 <= b < 127:
            buf.append(chr(b))
        else:
            buf.append(".")
    return "".join(buf)


def find_body_strings(data: bytes) -> list[dict[str, object]]:
    out = []
    for name in BODY_NAMES:
        pos = 0
        while True:
            pos = data.find(name, pos)
            if pos == -1:
                break
            pre_start = max(0, pos - 32)
            post_end = min(len(data), pos + 96)
            out.append(
                {
                    "name": name.decode("ascii"),
                    "offset": pos,
                    "pre_u32": [u32(data, off) for off in range(pre_start, pos, 4)],
                    "post_u32": [u32(data, off) for off in range(pos + 32, post_end, 4)],
                }
            )
            pos += 1
    out.sort(key=lambda item: int(item["offset"]))
    return out


def dump_toc2_windows(data: bytes, toc: list[dict[str, int]]) -> list[dict[str, object]]:
    t2 = toc[2]
    base = t2["offset"]
    windows = []
    for i in range(t2["count"]):
        off = base + i * 28
        raw = data[off : off + 28]
        if len(raw) < 28:
            break
        ascii_text = "".join(chr(b) if 32 <= b < 127 else "." for b in raw)
        windows.append(
            {
                "index": i,
                "offset": off,
                "u32": list(struct.unpack_from("<7I", raw)),
                "ascii": ascii_text,
            }
        )
    return windows


def dump_toc4(data: bytes, toc: list[dict[str, int]], bounds: list[dict[str, int]]) -> dict[str, object]:
    t4 = toc[4]
    t4_bounds = next(bound for bound in bounds if bound["idx"] == 4)
    start = t4["offset"]
    end = t4_bounds["end"]
    name = read_c_string(data, start)
    str_end = start + len(name) + 1
    if str_end % 4:
        str_end += 4 - (str_end % 4)
    u32_values = []
    for off in range(str_end, end, 4):
        u32_values.append({"offset": off, "value": u32(data, off)})
    return {
        "offset": start,
        "end": end,
        "size": end - start,
        "name": name,
        "aligned_u32_start": str_end,
        "u32_values": u32_values,
    }


def find_standard_descriptors(data: bytes) -> list[dict[str, int]]:
    out = []
    for off in range(0, len(data) - 36, 4):
        marker = u32(data, off + 24)
        if marker not in MARKER_STRIDE:
            continue
        stride = u32(data, off + 32)
        if stride != MARKER_STRIDE[marker]:
            continue
        if u32(data, off + 28) != 0:
            continue
        prim = u32(data, off)
        vc = u32(data, off + 8)
        ic = u32(data, off + 16)
        if prim > 2 or not (1 <= vc <= 200000 and 1 <= ic <= 2000000):
            continue
        out.append(
            {
                "offset": off,
                "prim": prim,
                "runtime_ptr_vb": u32(data, off + 4),
                "vertex_count": vc,
                "runtime_ptr_ib": u32(data, off + 12),
                "index_count": ic,
                "runtime_ptr_extra": u32(data, off + 20),
                "marker": marker,
                "stride": stride,
            }
        )
    return out


def find_40b_descriptors(data: bytes) -> list[dict[str, int]]:
    out = []
    for off in range(0, len(data) - 40, 4):
        marker = u32(data, off + 0x18)
        if marker not in (74002, 336402):
            continue
        stride = u32(data, off + 0x20)
        if stride != MARKER_STRIDE.get(marker):
            continue
        if u32(data, off + 0x1C) != 0:
            continue
        vb_type = u32(data, off)
        vc = u32(data, off + 0x08)
        ic = u32(data, off + 0x10)
        if vb_type > 2 or not (1 <= vc <= 100000 and 1 <= ic <= 500000):
            continue
        out.append(
            {
                "offset": off,
                "type": vb_type,
                "runtime_ptr_vb": u32(data, off + 0x04),
                "vertex_count": vc,
                "runtime_ptr_ib": u32(data, off + 0x0C),
                "index_count": ic,
                "runtime_ptr_extra": u32(data, off + 0x14),
                "marker": marker,
                "stride": stride,
                "type2": u32(data, off + 0x24),
            }
        )
    return out


def summarize_descriptor_regions(
    data: bytes, descriptors: list[dict[str, int]]
) -> list[dict[str, object]]:
    out = []
    for desc in descriptors:
        vb_off = None
        for stride, w_off, pos_off in ((72, 32, 0), (84, 0, 52)):
            if desc["stride"] != stride:
                continue
            search = 0
            while True:
                search = data.find(struct.pack("<f", 1.0), search)
                if search == -1:
                    break
                start = search - w_off
                if start < 0 or start + pos_off + 12 > len(data):
                    search += 1
                    continue
                px = struct.unpack_from("<f", data, start + pos_off)[0]
                if px != px or abs(px) > 300:
                    search += 1
                    continue
                run = 0
                t = start
                while t + stride <= len(data):
                    w = struct.unpack_from("<f", data, t + w_off)[0]
                    if abs(w - 1.0) > 0.05:
                        break
                    run += 1
                    t += stride
                if run >= desc["vertex_count"]:
                    vb_off = start
                    break
                search += 4
        summary = {
            "descriptor_offset": desc["offset"],
            "stride": desc["stride"],
            "vertex_count": desc["vertex_count"],
            "index_count": desc["index_count"],
            "vb_offset_guess": vb_off,
        }
        if vb_off is not None:
            vb_end = vb_off + desc["vertex_count"] * desc["stride"]
            ib_off = vb_end
            ib_preview = [
                u16(data, ib_off + i * 2)
                for i in range(min(16, desc["index_count"]))
                if ib_off + i * 2 + 2 <= len(data)
            ]
            summary["vb_end_guess"] = vb_end
            summary["ib_offset_guess"] = ib_off
            summary["ib_preview_u16"] = ib_preview
        out.append(summary)
    return out


def build_dump(path: Path) -> dict[str, object]:
    data = path.read_bytes()
    toc = parse_toc(data)
    bounds = section_bounds(toc, len(data))
    std_descs = find_standard_descriptors(data)
    desc40 = find_40b_descriptors(data)
    return {
        "file": str(path),
        "size": len(data),
        "toc": toc,
        "section_bounds": bounds,
        "body_strings": find_body_strings(data),
        "toc2_windows": dump_toc2_windows(data, toc),
        "toc4": dump_toc4(data, toc, bounds),
        "descriptors_36b": std_descs,
        "descriptors_40b": desc40,
        "descriptor_regions": summarize_descriptor_regions(data, std_descs),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Dump MDS/MDX structure to JSON")
    parser.add_argument("input", type=Path, help="MDX file to inspect")
    parser.add_argument("--out", type=Path, default=None, help="Write JSON to file")
    args = parser.parse_args()

    dump = build_dump(args.input)
    text = json.dumps(dump, ensure_ascii=False, indent=2)
    if args.out is not None:
        args.out.write_text(text, encoding="utf-8")
    else:
        print(text)


if __name__ == "__main__":
    main()
