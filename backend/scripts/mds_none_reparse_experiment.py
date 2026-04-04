#!/usr/bin/env python3
"""Reparse none-group files from loose-candidate offsets and measure recovery."""

from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path

from mds_none_probe import suffix_of
from mdx_parse_mds import find_all_descriptors, find_inline_descriptors


def sig36(descs: list[dict[str, int]]) -> str:
    if not descs:
        return "none"
    return ",".join(f"p{d['prim']}:s{d['stride']}" for d in descs)


def sig_inline(descs: list[dict[str, int]]) -> str:
    if not descs:
        return "none"
    return ",".join(f"i:s{d['stride']}" for d in descs)


def reparse_from_offset(data: bytes, start: int) -> dict[str, object]:
    std = find_all_descriptors(data[start:])
    inl = find_inline_descriptors(data[start:])

    # rebase offsets so results remain comparable
    std = [{**d, "off": d["off"] + start} for d in std]
    inl = [{**d, "off": d["off"] + start} for d in inl]
    return {
        "standard_count": len(std),
        "inline_count": len(inl),
        "standard_signature": sig36(std),
        "inline_signature": sig_inline(inl),
        "first_standard_offset": std[0]["off"] if std else None,
        "first_inline_offset": inl[0]["off"] if inl else None,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Reparse none-group files from loose candidate offsets")
    parser.add_argument("input_dir", type=Path, help="Directory with .mdx files")
    parser.add_argument("--probe", type=Path, required=True, help="Path to mdx_none_probe.json")
    parser.add_argument("--out", type=Path, default=None, help="Write JSON summary to file")
    args = parser.parse_args()

    probe = json.loads(args.probe.read_text(encoding="utf-8"))
    probe_rows = {row["file"]: row for row in probe["files"]}

    recovered = []
    suffix_recovery = defaultdict(lambda: {"files": 0, "recovered": 0, "std_sig": Counter(), "inline_sig": Counter()})
    recovered_any = 0
    std_sig_counter = Counter()
    inline_sig_counter = Counter()

    for path in sorted(args.input_dir.glob("*.mdx")):
        row = probe_rows.get(path.name)
        if row is None:
            continue
        suffix = suffix_of(path.name)
        suffix_recovery[suffix]["files"] += 1
        if not row["loose_candidates_top"]:
            recovered.append(
                {
                    "file": path.name,
                    "suffix": suffix,
                    "candidate_offset": None,
                    "result": None,
                }
            )
            continue

        start = int(row["loose_candidates_top"][0]["offset"])
        result = reparse_from_offset(path.read_bytes(), start)
        ok = result["standard_count"] > 0 or result["inline_count"] > 0
        if ok:
            recovered_any += 1
            suffix_recovery[suffix]["recovered"] += 1
            std_sig_counter[result["standard_signature"]] += 1
            inline_sig_counter[result["inline_signature"]] += 1
            suffix_recovery[suffix]["std_sig"][result["standard_signature"]] += 1
            suffix_recovery[suffix]["inline_sig"][result["inline_signature"]] += 1

        recovered.append(
            {
                "file": path.name,
                "suffix": suffix,
                "candidate_offset": start,
                "candidate_marker": row["loose_candidates_top"][0]["marker"],
                "candidate_stride": row["loose_candidates_top"][0]["stride"],
                "result": result,
            }
        )

    summary = {
        "file_count": len(recovered),
        "recovered_any": recovered_any,
        "recovery_rate": recovered_any / len(recovered) if recovered else 0.0,
        "standard_signatures": std_sig_counter.most_common(),
        "inline_signatures": inline_sig_counter.most_common(),
        "suffix_recovery": {
            suffix: {
                "files": info["files"],
                "recovered": info["recovered"],
                "recovery_rate": info["recovered"] / info["files"] if info["files"] else 0.0,
                "standard_signatures": info["std_sig"].most_common(),
                "inline_signatures": info["inline_sig"].most_common(),
            }
            for suffix, info in sorted(suffix_recovery.items())
        },
        "files": recovered,
    }

    text = json.dumps(summary, ensure_ascii=False, indent=2)
    if args.out is not None:
        args.out.write_text(text, encoding="utf-8")
    else:
        print(text)


if __name__ == "__main__":
    main()
