#!/usr/bin/env python3
"""Re-extract all MDX files with the current parser and build a local catalog."""

from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path

import mdx_parse_mds as parser
from mdx_parse_mds import extract_mdx, select_export_parts, write_obj


def suffix_of(name: str) -> str:
    stem = name[:-4] if name.endswith(".mdx") else name
    return stem.rsplit("_", 1)[-1]


def faction_of(name: str) -> str:
    if name.startswith("e_"):
        return "empire"
    if name.startswith("f_"):
        return "alliance"
    return "unknown"


def lod_key(suffix: str) -> str:
    return {
        "h": "high",
        "m": "medium",
        "l": "low",
        "n": "n",
    }.get(suffix, suffix)


def model_group_id(stem: str) -> str:
    if "_" not in stem:
        return stem
    return stem.rsplit("_", 1)[0]


def ensure_ship(groups: dict[str, dict[str, object]], stem: str) -> dict[str, object]:
    gid = model_group_id(stem)
    ship = groups.get(gid)
    if ship is None:
        ship = {
            "id": gid,
            "faction": faction_of(gid),
            "class": gid,
            "files": {},
            "variants": {},
        }
        groups[gid] = ship
    return ship


def main() -> None:
    argp = argparse.ArgumentParser(description="Re-extract all MDX files to local OBJ catalog")
    argp.add_argument("input_dir", type=Path)
    argp.add_argument("--out", type=Path, default=Path("frontend/public/research-models"))
    args = argp.parse_args()

    out_dir = args.out
    if out_dir.exists():
        shutil.rmtree(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    grouped: dict[str, dict[str, object]] = {}
    failures: list[dict[str, object]] = []

    total_ok = 0
    total_fail = 0
    total_vertices = 0
    total_faces = 0

    for path in sorted(args.input_dir.glob("*.mdx")):
        data = path.read_bytes()
        stem = path.stem
        suffix = suffix_of(path.name)
        lod = lod_key(suffix)
        parts, dc, df, hc, hf = extract_mdx(data, stem)
        prev_gap = parser.ENABLE_GAP_EXTRACTION
        parser.ENABLE_GAP_EXTRACTION = True
        try:
            gap_parts, _, _, _, _ = extract_mdx(data, stem)
        finally:
            parser.ENABLE_GAP_EXTRACTION = prev_gap

        ship = ensure_ship(grouped, stem)
        ship["variants"][suffix] = {
            "source_file": path.name,
            "desc_parts": dc,
            "heur_parts": hc,
            "desc_faces": df,
            "heur_faces": hf,
        }

        if not parts:
            failures.append(
                {
                    "file": path.name,
                    "id": ship["id"],
                    "suffix": suffix,
                    "lod": lod,
                }
            )
            total_fail += 1
            continue

        model_dir = out_dir / ship["id"]
        model_dir.mkdir(parents=True, exist_ok=True)
        obj_name = f"{lod}.obj"
        tv, tf = write_obj(parts, model_dir / obj_name, stem)
        debug_files: dict[str, dict[str, object]] = {}
        debug_sources = {
            "exact": parts,
            "heur": parts,
            "gap": gap_parts,
            "replace": gap_parts,
        }
        for mode, suffix_name in (("exact", "exact"), ("heur", "heur"), ("gap", "gap"), ("replace", "replace")):
            mode_parts = select_export_parts(debug_sources[mode], mode)
            if not mode_parts:
                continue
            mode_name = f"{lod}_{suffix_name}.obj"
            mv, mf = write_obj(mode_parts, model_dir / mode_name, f"{stem}_{mode}")
            debug_files[mode] = {
                "file": mode_name,
                "vertices": mv,
                "faces": mf,
                "size": (model_dir / mode_name).stat().st_size,
            }
        ship["files"][lod] = {
            "file": obj_name,
            "vertices": tv,
            "faces": tf,
            "size": (model_dir / obj_name).stat().st_size,
            "source_file": path.name,
            "debug_files": debug_files,
        }
        total_ok += 1
        total_vertices += tv
        total_faces += tf

    ships = sorted(grouped.values(), key=lambda item: item["id"])
    catalog = {
        "ships": ships,
        "fortresses": [],
        "planets": [],
        "summary": {
            "total_ships": len(ships),
            "total_fortresses": 0,
            "total_planets": 0,
            "total_vertices": total_vertices,
            "total_faces": total_faces,
            "total_textures": 0,
            "extracted_variants": total_ok,
            "failed_variants": total_fail,
        },
        "failures": failures,
    }

    (out_dir / "catalog.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(catalog["summary"], ensure_ascii=False, indent=2))
    print(f"catalog={out_dir / 'catalog.json'}")


if __name__ == "__main__":
    main()
