#!/usr/bin/env python3

import getopt
import os
import re
import shutil
import sys
from subprocess import call


def find_tool(names):
    for n in names:
        p = shutil.which(n)
        if p:
            return p
    return None


def read_svg_dims(path):
    """Extract width/height from an SVG — prefer explicit width/height, fall back to viewBox."""
    with open(path, 'r', encoding='utf-8', errors='replace') as f:
        head = f.read(4096)
    m = re.search(r'<svg\b[^>]*>', head, flags=re.IGNORECASE)
    if not m:
        return None, None
    tag = m.group(0)
    w_m = re.search(r'\bwidth\s*=\s*"([^"]+)"', tag)
    h_m = re.search(r'\bheight\s*=\s*"([^"]+)"', tag)

    def parse_num(s):
        if s is None:
            return None
        s = s.strip().lower()
        s = re.sub(r'(px|pt|em|%)$', '', s)
        try:
            return float(s)
        except ValueError:
            return None

    w = parse_num(w_m.group(1)) if w_m else None
    h = parse_num(h_m.group(1)) if h_m else None
    if w is None or h is None:
        vb = re.search(r'\bviewBox\s*=\s*"([^"]+)"', tag)
        if vb:
            parts = re.split(r'[\s,]+', vb.group(1).strip())
            if len(parts) == 4:
                try:
                    w = w or float(parts[2])
                    h = h or float(parts[3])
                except ValueError:
                    pass
    return w, h


def main(argv):
    rsvg = find_tool(['rsvg-convert'])
    im = find_tool(['magick', 'convert'])

    if not rsvg and not im:
        print('Need either rsvg-convert (librsvg2-bin) or ImageMagick to build.')
        sys.exit(1)

    svg_dir = ''
    png_dir = ''
    try:
        opts, _ = getopt.getopt(argv, 'hi:o:', ['input=', 'output='])
    except getopt.GetoptError:
        print('convert-svg-to-png.py -i <svgDirectory> -o <pngDirectory>')
        sys.exit(2)
    for opt, arg in opts:
        if opt == '-h':
            print('convert-svg-to-png.py -i <svgDirectory> -o <pngDirectory>')
            sys.exit()
        elif opt in ('-i', '--input'):
            svg_dir = arg.strip()
        elif opt in ('-o', '--output'):
            png_dir = arg.strip()

    print('Input svg directory: ' + svg_dir)
    print('Output png directory: ' + png_dir)

    MAX_WIDTH = 180
    MAX_HEIGHT = 140
    count = 0

    for i in sorted(os.listdir(svg_dir)):
        fname, _, ext = i.rpartition('.')
        ext = ext.lower()
        in_path = os.path.join(svg_dir, i)
        out_path = os.path.join(png_dir, fname + '.png')

        if ext == 'png':
            if im:
                call([im, in_path, '-resize', '%dx%d' % (MAX_WIDTH, MAX_HEIGHT), out_path])
            else:
                shutil.copyfile(in_path, out_path)
            continue
        if ext != 'svg':
            continue

        w, h = read_svg_dims(in_path)
        if not w or not h:
            w, h = MAX_WIDTH, MAX_HEIGHT
        bound_h = MAX_WIDTH * (h / w)
        if rsvg:
            if bound_h > MAX_HEIGHT:
                call([rsvg, '-h', str(MAX_HEIGHT), in_path, '-o', out_path])
            else:
                call([rsvg, '-w', str(MAX_WIDTH), in_path, '-o', out_path])
        else:
            call([im, '-background', 'none', in_path,
                  '-resize', '%dx%d' % (MAX_WIDTH, MAX_HEIGHT), out_path])
        count += 1

    print('Converted {0} svgs to png'.format(count))


if __name__ == '__main__':
    main(sys.argv[1:])
