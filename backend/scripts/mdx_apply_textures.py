#!/usr/bin/env python3
"""Convert DDS textures to PNG and create MTL files for OBJ models.

Usage:
  python mdx_apply_textures.py <tex_dir> <obj_dir>
"""

import argparse, os
from pathlib import Path
from PIL import Image


def main():
    parser = argparse.ArgumentParser(description='Apply textures to OBJ models')
    parser.add_argument('tex_dir', type=Path)
    parser.add_argument('obj_dir', type=Path)
    args = parser.parse_args()

    obj_dirs = sorted([d for d in args.obj_dir.iterdir() if d.is_dir()])
    converted = 0

    for obj_dir in obj_dirs:
        obj_file = obj_dir / 'high.obj'
        if not obj_file.exists():
            continue

        ship_name = obj_dir.name  # e.g. e_brunhild
        dds_file = args.tex_dir / f'{ship_name}_h.dds'
        if not dds_file.exists():
            continue

        # Convert DDS → PNG
        png_file = obj_dir / 'texture.png'
        try:
            im = Image.open(str(dds_file))
            im.save(str(png_file), 'PNG')
        except Exception as e:
            print(f'  {ship_name}: DDS convert failed: {e}')
            continue

        # Check for bump map
        bump_dds = args.tex_dir / f'{ship_name}_bump.dds'
        bump_png = None
        if bump_dds.exists():
            bump_png = obj_dir / 'bump.png'
            try:
                bim = Image.open(str(bump_dds))
                bim.save(str(bump_png), 'PNG')
            except Exception:
                bump_png = None

        # Create MTL file
        mtl_file = obj_dir / 'material.mtl'
        with open(mtl_file, 'w') as f:
            f.write(f'# Material for {ship_name}\n')
            f.write(f'newmtl ship_material\n')
            f.write(f'Ka 0.2 0.2 0.2\n')
            f.write(f'Kd 0.8 0.8 0.8\n')
            f.write(f'Ks 0.3 0.3 0.3\n')
            f.write(f'Ns 50.0\n')
            f.write(f'd 1.0\n')
            f.write(f'map_Kd texture.png\n')
            if bump_png:
                f.write(f'bump bump.png\n')

        # Prepend mtllib to OBJ
        obj_content = obj_file.read_text(encoding='utf-8')
        if 'mtllib' not in obj_content:
            lines = obj_content.split('\n')
            # Insert mtllib after first comment, usemtl before first face
            new_lines = []
            mtllib_added = False
            usemtl_added = False
            for line in lines:
                if not mtllib_added and (line.startswith('g ') or line.startswith('v ')):
                    new_lines.append('mtllib material.mtl')
                    mtllib_added = True
                if not usemtl_added and line.startswith('f '):
                    new_lines.append('usemtl ship_material')
                    usemtl_added = True
                new_lines.append(line)
            obj_file.write_text('\n'.join(new_lines), encoding='utf-8')

        converted += 1
        sz = png_file.stat().st_size // 1024
        print(f'  {ship_name}: {im.size[0]}x{im.size[1]} → texture.png ({sz}KB)')

    print(f'\n=== {converted}/{len(obj_dirs)} textured ===')


if __name__ == '__main__':
    main()
