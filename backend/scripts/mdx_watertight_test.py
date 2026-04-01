#!/usr/bin/env python3
"""Watertight mesh test — edge manifold analysis for OBJ files.

Checks that every edge is shared by exactly 2 faces (manifold condition).
A watertight mesh has no boundary edges (shared by only 1 face)
and no non-manifold edges (shared by 3+ faces).

Usage:
  python mdx_watertight_test.py <obj_file_or_dir>

Metrics:
  - manifold edges: shared by exactly 2 faces
  - boundary edges: shared by 1 face (holes)
  - non-manifold edges: shared by 3+ faces (overlapping geometry)
  - manifold ratio = manifold / total

Judgment:
  >= 95%: PASS
  90-95%: WARN
  < 90%:  FAIL
"""

import argparse
from pathlib import Path
from collections import defaultdict


def parse_obj(path):
    """Parse OBJ file, return list of faces (each face = list of vertex indices)."""
    faces = []
    vertex_count = 0
    with open(path) as f:
        for line in f:
            line = line.strip()
            if line.startswith('v '):
                vertex_count += 1
            elif line.startswith('f '):
                parts = line.split()[1:]
                indices = []
                for p in parts:
                    idx = int(p.split('/')[0])
                    indices.append(idx)
                if len(indices) >= 3:
                    faces.append(tuple(indices))
    return vertex_count, faces


def edge_key(a, b):
    """Canonical edge key (unordered pair)."""
    return (min(a, b), max(a, b))


def analyze_edges(faces):
    """Count how many faces share each edge."""
    edge_counts = defaultdict(int)
    for face in faces:
        n = len(face)
        for i in range(n):
            a, b = face[i], face[(i + 1) % n]
            edge_counts[edge_key(a, b)] += 1
    return edge_counts


def test_watertight(obj_path):
    """Run watertight test on a single OBJ file."""
    vc, faces = parse_obj(obj_path)
    if not faces:
        return {'name': obj_path.stem, 'status': 'EMPTY', 'faces': 0}

    edge_counts = analyze_edges(faces)
    total = len(edge_counts)

    manifold = sum(1 for c in edge_counts.values() if c == 2)
    boundary = sum(1 for c in edge_counts.values() if c == 1)
    non_manifold = sum(1 for c in edge_counts.values() if c >= 3)

    ratio = manifold / total if total > 0 else 0

    if ratio >= 0.95:
        status = 'PASS'
    elif ratio >= 0.90:
        status = 'WARN'
    else:
        status = 'FAIL'

    return {
        'name': obj_path.stem,
        'vertices': vc,
        'faces': len(faces),
        'total_edges': total,
        'manifold': manifold,
        'boundary': boundary,
        'non_manifold': non_manifold,
        'ratio': ratio,
        'status': status,
    }


def main():
    parser = argparse.ArgumentParser(description='Watertight Mesh Test')
    parser.add_argument('input', type=Path)
    args = parser.parse_args()

    if args.input.is_file():
        objs = [args.input]
    else:
        objs = sorted(args.input.rglob('high.obj'))

    if not objs:
        print('No OBJ files found.')
        return

    results = []
    for obj in objs:
        r = test_watertight(obj)
        results.append(r)

    # Print results
    pass_count = sum(1 for r in results if r['status'] == 'PASS')
    warn_count = sum(1 for r in results if r['status'] == 'WARN')
    fail_count = sum(1 for r in results if r['status'] == 'FAIL')

    print(f'{"Name":<30} {"Faces":>7} {"Edges":>7} {"Manifold":>8} {"Boundary":>8} {"NonMnf":>6} {"Ratio":>7} {"Status"}')
    print('-' * 90)
    for r in results:
        if r['status'] == 'EMPTY':
            print(f'{r["name"]:<30} {"EMPTY":>7}')
            continue
        print(f'{r["name"]:<30} {r["faces"]:>7} {r["total_edges"]:>7} '
              f'{r["manifold"]:>8} {r["boundary"]:>8} {r["non_manifold"]:>6} '
              f'{r["ratio"]:>6.1%} {r["status"]}')

    print(f'\n=== {len(results)} files: {pass_count} PASS, {warn_count} WARN, {fail_count} FAIL ===')

    if results:
        ratios = [r['ratio'] for r in results if r['status'] != 'EMPTY']
        if ratios:
            avg = sum(ratios) / len(ratios)
            mn = min(ratios)
            print(f'Average manifold ratio: {avg:.1%}, min: {mn:.1%}')


if __name__ == '__main__':
    main()
