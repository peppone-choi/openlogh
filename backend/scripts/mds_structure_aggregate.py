#!/usr/bin/env python3
"""Aggregate structural patterns across many MDS/MDX files."""

from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path

from mds_structure_dump import build_dump


def _sig_from_descs(descs: list[dict[str, object]], prim_key: str) -> str:
    if not descs:
        return "none"
    parts = []
    for desc in descs:
        parts.append(f"p{desc[prim_key]}:s{desc['stride']}")
    return ",".join(parts)


def descriptor_signature_36(dump: dict[str, object]) -> str:
    return _sig_from_descs(dump["descriptors_36b"], "prim")


def descriptor_signature_40(dump: dict[str, object]) -> str:
    return _sig_from_descs(dump["descriptors_40b"], "type")


def descriptor_signature_any(dump: dict[str, object]) -> str:
    sig36 = descriptor_signature_36(dump)
    if sig36 != "none":
        return f"36:{sig36}"
    sig40 = descriptor_signature_40(dump)
    if sig40 != "none":
        return f"40:{sig40}"
    return "none"


def toc2_overrun(dump: dict[str, object]) -> bool:
    windows = dump["toc2_windows"]
    return any("body_" in window["ascii"] for window in windows)


def body_signature(dump: dict[str, object]) -> str:
    names = [entry["name"] for entry in dump["body_strings"]]
    if not names:
        return "none"
    return ",".join(f"{name}x{names.count(name)}" for name in sorted(set(names)))


def group_name(path: str) -> str:
    name = Path(path).name
    if "_" not in name:
        return name
    base = name[:-4] if name.endswith(".mdx") else name
    parts = base.split("_")
    if len(parts) < 2:
        return base
    return "_".join(parts[:-1])


def main() -> None:
    parser = argparse.ArgumentParser(description="Aggregate MDS/MDX structure patterns")
    parser.add_argument("input_dir", type=Path, help="Directory containing .mdx files")
    parser.add_argument("--out", type=Path, default=None, help="Write JSON summary to file")
    args = parser.parse_args()

    files = sorted(args.input_dir.glob("*.mdx"))
    sig36_counter: Counter[str] = Counter()
    sig40_counter: Counter[str] = Counter()
    sig_any_counter: Counter[str] = Counter()
    body_counter: Counter[str] = Counter()
    toc2_counter: Counter[str] = Counter()
    by_group: dict[str, dict[str, object]] = defaultdict(
        lambda: {
            "files": 0,
            "descriptor_signatures_36": Counter(),
            "descriptor_signatures_40": Counter(),
            "descriptor_signatures_any": Counter(),
            "body_signatures": Counter(),
            "toc2_overrun": 0,
        }
    )
    interesting = []

    for path in files:
        dump = build_dump(path)
        sig36 = descriptor_signature_36(dump)
        sig40 = descriptor_signature_40(dump)
        sig_any = descriptor_signature_any(dump)
        body_sig = body_signature(dump)
        overrun = toc2_overrun(dump)

        sig36_counter[sig36] += 1
        sig40_counter[sig40] += 1
        sig_any_counter[sig_any] += 1
        body_counter[body_sig] += 1
        toc2_counter["overrun" if overrun else "clean"] += 1

        grp = by_group[group_name(str(path))]
        grp["files"] += 1
        grp["descriptor_signatures_36"][sig36] += 1
        grp["descriptor_signatures_40"][sig40] += 1
        grp["descriptor_signatures_any"][sig_any] += 1
        grp["body_signatures"][body_sig] += 1
        if overrun:
            grp["toc2_overrun"] += 1

        if overrun or sig_any == "none" or body_sig not in ("body_ax2,body_bx2", "none"):
            interesting.append(
                {
                    "file": path.name,
                    "descriptor_signature_36": sig36,
                    "descriptor_signature_40": sig40,
                    "descriptor_signature_any": sig_any,
                    "body_signature": body_sig,
                    "toc2_overrun": overrun,
                    "descriptor_count": len(dump["descriptors_36b"]),
                    "descriptor40_count": len(dump["descriptors_40b"]),
                    "body_offsets": [entry["offset"] for entry in dump["body_strings"]],
                }
            )

    summary = {
        "file_count": len(files),
        "descriptor_signatures_36": sig36_counter.most_common(),
        "descriptor_signatures_40": sig40_counter.most_common(),
        "descriptor_signatures_any": sig_any_counter.most_common(),
        "body_signatures": body_counter.most_common(),
        "toc2_overrun": toc2_counter,
        "groups": {
            key: {
                "files": value["files"],
                "descriptor_signatures_36": value["descriptor_signatures_36"].most_common(),
                "descriptor_signatures_40": value["descriptor_signatures_40"].most_common(),
                "descriptor_signatures_any": value["descriptor_signatures_any"].most_common(),
                "body_signatures": value["body_signatures"].most_common(),
                "toc2_overrun": value["toc2_overrun"],
            }
            for key, value in sorted(by_group.items())
        },
        "interesting_files": interesting,
    }

    text = json.dumps(summary, ensure_ascii=False, indent=2)
    if args.out is not None:
        args.out.write_text(text, encoding="utf-8")
    else:
        print(text)


if __name__ == "__main__":
    main()
