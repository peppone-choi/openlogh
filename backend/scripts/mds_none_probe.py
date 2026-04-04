#!/usr/bin/env python3
"""Probe files with no detected descriptors using looser post-TOC scans."""

from __future__ import annotations

import argparse
import json
import struct
from collections import Counter, defaultdict
from pathlib import Path

from mds_structure_dump import MARKER_STRIDE, build_dump, parse_toc


def u32(data: bytes, off: int) -> int:
    return struct.unpack_from("<I", data, off)[0]


def f32(data: bytes, off: int) -> float:
    return struct.unpack_from("<f", data, off)[0]


def suffix_of(name: str) -> str:
    stem = name[:-4] if name.endswith(".mdx") else name
    return stem.rsplit("_", 1)[-1]


def data_start_from_toc(toc: list[dict[str, int]]) -> int:
    return toc[8]["offset"] if len(toc) > 8 else 0


def loose_descriptor_candidates(data: bytes, start: int) -> list[dict[str, int]]:
    """Collect descriptor-like records with relaxed validation after TOC8."""
    out = []
    seen = set()
    for align in (2, 4):
        for off in range(start, len(data) - 40, align):
            marker = u32(data, off + 24)
            stride = u32(data, off + 32)
            if marker not in MARKER_STRIDE:
                continue
            prim = u32(data, off)
            vc = u32(data, off + 8)
            ic = u32(data, off + 16)
            pad = u32(data, off + 28)
            flags = []
            if stride == MARKER_STRIDE[marker]:
                flags.append("stride_match")
            if pad == 0:
                flags.append("pad_zero")
            if prim <= 4:
                flags.append("prim_small")
            if 1 <= vc <= 200000:
                flags.append("vc_range")
            if 0 <= ic <= 2000000:
                flags.append("ic_range")
            score = len(flags)
            if score < 3:
                continue
            key = (off, marker, stride, vc, ic)
            if key in seen:
                continue
            seen.add(key)
            out.append(
                {
                    "offset": off,
                    "align": align,
                    "marker": marker,
                    "marker_stride": MARKER_STRIDE[marker],
                    "stride": stride,
                    "prim": prim,
                    "vertex_count": vc,
                    "index_count": ic,
                    "pad": pad,
                    "score": score,
                    "flags": flags,
                }
            )
    out.sort(key=lambda row: (-row["score"], row["offset"]))
    return out


def find_w_runs(data: bytes, stride: int, start: int) -> list[dict[str, int]]:
    if stride == 84:
        w_off = 0
        pos_off = 52
    elif stride == 72:
        w_off = 32
        pos_off = 0
    else:
        return []
    target = struct.pack("<f", 1.0)
    out = []
    pos = start
    while pos < len(data) - stride:
        wp = data.find(target, pos)
        if wp == -1:
            break
        vb_start = wp - w_off
        if vb_start < 0 or vb_start + pos_off + 12 > len(data):
            pos = wp + 1
            continue
        px = f32(data, vb_start + pos_off)
        if px != px or abs(px) > 300:
            pos = wp + 1
            continue
        vc = 0
        t = vb_start
        while t + stride <= len(data):
            w = f32(data, t + w_off)
            if abs(w - 1.0) > 0.05:
                break
            tpx = f32(data, t + pos_off)
            if tpx != tpx or abs(tpx) > 300:
                break
            vc += 1
            t += stride
        if vc >= 8:
            out.append({"offset": vb_start, "stride": stride, "vertex_count": vc})
            pos = t
        else:
            pos = wp + 4
    return out


def probe_file(path: Path) -> dict[str, object]:
    data = path.read_bytes()
    toc = parse_toc(data)
    start = data_start_from_toc(toc)
    dump = build_dump(path)
    return {
        "file": path.name,
        "suffix": suffix_of(path.name),
        "size": len(data),
        "data_start": start,
        "body_count": len(dump["body_strings"]),
        "toc2_overrun": any("body_" in w["ascii"] for w in dump["toc2_windows"]),
        "loose_candidates_top": loose_descriptor_candidates(data, start)[:12],
        "w_runs_72_top": find_w_runs(data, 72, start)[:8],
        "w_runs_84_top": find_w_runs(data, 84, start)[:8],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Probe none-group MDS/MDX files")
    parser.add_argument("input_dir", type=Path, help="Directory with .mdx files")
    parser.add_argument("--summary", type=Path, required=True, help="Path to aggregate summary json")
    parser.add_argument("--out", type=Path, default=None, help="Write JSON to file")
    args = parser.parse_args()

    summary = json.loads(args.summary.read_text(encoding="utf-8"))
    none_names = {
        row["file"]
        for row in summary["interesting_files"]
        if row["descriptor_signature_any"] == "none"
    }

    rows = []
    suffix_counts = Counter()
    candidate_markers = Counter()
    candidate_shapes = Counter()
    w_run_presence = defaultdict(int)

    for path in sorted(args.input_dir.glob("*.mdx")):
        if path.name not in none_names:
            continue
        row = probe_file(path)
        rows.append(row)
        suffix_counts[row["suffix"]] += 1
        if row["loose_candidates_top"]:
            top = row["loose_candidates_top"][0]
            candidate_markers[f"m{top['marker']}:s{top['stride']}"] += 1
            candidate_shapes[f"score{top['score']}:{','.join(top['flags'])}"] += 1
        if row["w_runs_72_top"]:
            w_run_presence["s72"] += 1
        if row["w_runs_84_top"]:
            w_run_presence["s84"] += 1

    result = {
        "file_count": len(rows),
        "suffix_counts": suffix_counts,
        "top_loose_marker_shapes": candidate_markers.most_common(),
        "top_candidate_scores": candidate_shapes.most_common(20),
        "w_run_presence": dict(w_run_presence),
        "files": rows,
    }

    text = json.dumps(result, ensure_ascii=False, indent=2)
    if args.out is not None:
        args.out.write_text(text, encoding="utf-8")
    else:
        print(text)


if __name__ == "__main__":
    main()
