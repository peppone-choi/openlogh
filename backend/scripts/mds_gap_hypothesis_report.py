#!/usr/bin/env python3
"""Classify gap faces as additive vs patch/replace across all MDX ships."""

from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path

import mdx_parse_mds as parser


def suffix_of(name: str) -> str:
    stem = name[:-4] if name.endswith(".mdx") else name
    return stem.rsplit("_", 1)[-1]


def group_id(name: str) -> str:
    stem = name[:-4] if name.endswith(".mdx") else name
    return stem.rsplit("_", 1)[0]


def classify_gap_part(part: dict[str, object]) -> dict[str, object] | None:
    base_faces = list(part.get("base_faces") or [])
    gap_faces = list(part.get("gap_faces") or [])
    profile = parser.classify_gap_behavior(part)
    if profile is None:
        return None

    gap_face_count = len(gap_faces)
    base_face_count = len(base_faces)
    face_ratio = gap_face_count / max(base_face_count, 1)

    return {
        "label": profile["label"],
        "stride": part.get("stride"),
        "source": part.get("source"),
        "base_faces": base_face_count,
        "gap_faces": gap_face_count,
        "face_ratio": round(face_ratio, 4),
        "gap_vertex_overlap": round(float(profile["gap_vertex_overlap"]), 4),
        "gap_edge_overlap": round(float(profile["gap_edge_overlap"]), 4),
        "gap_boundary_touch": round(float(profile["gap_boundary_touch"]), 4),
        "shared_vertices": int(profile["shared_vertices"]),
        "gap_vertices": int(profile["gap_vertices"]),
        "shared_edges": int(profile["shared_edges"]),
        "gap_edges": int(profile["gap_edges"]),
    }


def dominant_label(items: list[dict[str, object]]) -> str:
    if not items:
        return "no_gap"
    weighted = defaultdict(int)
    for item in items:
        weighted[str(item["label"])] += int(item["gap_faces"])
    return max(weighted.items(), key=lambda entry: (entry[1], entry[0]))[0]


def analyze_file(path: Path) -> dict[str, object]:
    data = path.read_bytes()
    parts, desc_count, desc_faces, heur_count, heur_faces = parser.extract_mdx(data, path.stem)
    part_reports = []
    gap_parts = 0
    gap_faces = 0
    for index, part in enumerate(parts):
        report = classify_gap_part(part)
        if report is None:
            continue
        report["part_index"] = index
        part_reports.append(report)
        gap_parts += 1
        gap_faces += int(report["gap_faces"])

    return {
        "file": path.name,
        "group": group_id(path.name),
        "suffix": suffix_of(path.name),
        "desc_parts": desc_count,
        "desc_faces": desc_faces,
        "heur_parts": heur_count,
        "heur_faces": heur_faces,
        "part_count": len(parts),
        "gap_parts": gap_parts,
        "gap_faces": gap_faces,
        "dominant_label": dominant_label(part_reports),
        "parts": part_reports,
    }


def main() -> None:
    argp = argparse.ArgumentParser(description="Analyze whether gap behaves like add or replace.")
    argp.add_argument("input_dir", type=Path)
    argp.add_argument("--out", type=Path, default=Path("/tmp/mdx_gap_hypothesis_report.json"))
    args = argp.parse_args()

    prev_gap = parser.ENABLE_GAP_EXTRACTION
    parser.ENABLE_GAP_EXTRACTION = True
    try:
        files = [analyze_file(path) for path in sorted(args.input_dir.glob("*.mdx"))]
    finally:
        parser.ENABLE_GAP_EXTRACTION = prev_gap

    with_gap = [item for item in files if item["gap_parts"] > 0]
    by_label = Counter(item["dominant_label"] for item in with_gap)
    by_suffix = defaultdict(Counter)
    by_faction = defaultdict(Counter)
    for item in with_gap:
        by_suffix[str(item["suffix"])][str(item["dominant_label"])] += 1
        faction = "empire" if item["file"].startswith("e_") else "alliance" if item["file"].startswith("f_") else "unknown"
        by_faction[faction][str(item["dominant_label"])] += 1

    report = {
        "summary": {
            "total_files": len(files),
            "files_with_gap_parts": len(with_gap),
            "files_without_gap_parts": len(files) - len(with_gap),
            "dominant_labels": dict(by_label),
            "dominant_by_suffix": {key: dict(value) for key, value in sorted(by_suffix.items())},
            "dominant_by_faction": {key: dict(value) for key, value in sorted(by_faction.items())},
        },
        "files": files,
    }
    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report["summary"], ensure_ascii=False, indent=2))
    print(f"report={args.out}")


if __name__ == "__main__":
    main()
