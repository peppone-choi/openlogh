#!/usr/bin/env python3
"""Classify *_n.mdx files into node-only vs sparse-mesh variants."""

from __future__ import annotations

import argparse
import json
import struct
from collections import Counter
from pathlib import Path

from mdx_parse_mds import extract_mdx_core, find_loose_descriptor_start, parse_toc
from mds_none_probe import find_w_runs


def u16(data: bytes, off: int) -> int:
    return struct.unpack_from("<H", data, off)[0]


def classify_n_file(path: Path) -> dict[str, object]:
    data = path.read_bytes()
    toc = parse_toc(data)
    toc8 = toc[8]["off"] if len(toc) > 8 else 0
    valid_after4 = [entry["off"] for entry in toc[5:] if entry["off"] < len(data)]

    parts, desc_parts, _, heur_parts, _, desc_total, inline_total = extract_mdx_core(data)
    face_total = sum(len(part["faces"]) for part in parts)

    fallback = find_loose_descriptor_start(data)
    w72 = find_w_runs(data, 72, 0)
    w84 = find_w_runs(data, 84, 0)

    tail = data[toc8:] if 0 <= toc8 < len(data) else b""
    tail_nonzero = sum(1 for b in tail if b != 0)
    first_after_sentinel = None
    if 0 <= toc8 + 64 < len(data):
        pos = toc8 + 64
        while pos < len(data) and data[pos] == 0:
            pos += 1
        if pos < len(data):
            first_after_sentinel = pos - toc8

    has_mesh_signals = bool(desc_total or inline_total or w72 or w84 or fallback or face_total)
    has_valid_tail = bool(valid_after4)

    if face_total > 0:
        kind = "mesh_n"
    elif has_valid_tail and has_mesh_signals:
        kind = "sparse_mesh_n"
    elif has_valid_tail:
        kind = "sentinel_tail_n"
    else:
        kind = "node_only_n"

    return {
        "file": path.name,
        "size": len(data),
        "kind": kind,
        "toc8": toc8,
        "valid_after4": valid_after4,
        "desc_parts": desc_parts,
        "heur_parts": heur_parts,
        "desc_total": desc_total,
        "inline_total": inline_total,
        "face_total": face_total,
        "fallback": fallback,
        "w72_runs": len(w72),
        "w84_runs": len(w84),
        "tail_nonzero": tail_nonzero,
        "first_after_sentinel": first_after_sentinel,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Classify *_n.mdx variants")
    parser.add_argument("input_dir", type=Path)
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args()

    rows = []
    kinds = Counter()
    for path in sorted(args.input_dir.glob("*_n.mdx")):
        row = classify_n_file(path)
        rows.append(row)
        kinds[row["kind"]] += 1

    summary = {
        "file_count": len(rows),
        "kind_counts": kinds,
        "files": rows,
    }

    text = json.dumps(summary, ensure_ascii=False, indent=2)
    if args.out is not None:
        args.out.write_text(text, encoding="utf-8")
    else:
        print(text)


if __name__ == "__main__":
    main()
