#!/usr/bin/env python3
"""App Store promotional artwork: the header and search-results backdrops.

App Store Connect's featuring slots take a header (5244x2950) and a
search-results banner (3840x1646). Apple sets the app's name over this art,
so it carries no text: the launcher mark (icon-512.svg) on the icon's dark
field, over a faint lattice of glance tiles that says "you lay it out".

Writes an SVG per size and renders it with rsvg-convert (brew install
librsvg). Deterministic: the tile layout comes from a fixed seed, so a
re-run reproduces the same art.
"""
import random
import subprocess
from pathlib import Path

HERE = Path(__file__).resolve().parent
FIELD = "#0E1726"
TILE = "#16233A"
TILE_EDGE = "#1E2E4B"
SUN = "#F2B705"
RAIN = "#2E9BD6"
ACCENT = "#EC6A1E"

# The launcher mark, verbatim from icon-512.svg (100-unit design, background
# dropped since the field is the same navy). Its 64-unit frame spans 18-82.
MARK = """
  <defs>
    <clipPath id="top"><rect x="18" y="18" width="64" height="30.8"/></clipPath>
    <clipPath id="bl"><rect x="18" y="51.2" width="30.8" height="30.8"/></clipPath>
    <clipPath id="br"><rect x="51.2" y="51.2" width="30.8" height="30.8"/></clipPath>
  </defs>
  <g clip-path="url(#top)" fill="#F2B705">
    <circle cx="50" cy="50" r="11"/>
    <path d="M50 35 L50 29 M58.82 37.87 L62.34 33.01 M41.18 37.87 L37.66 33.01 M64.27 45.37 L69.97 43.51 M35.73 45.37 L30.03 43.51" fill="none" stroke="#F2B705" stroke-width="2.8" stroke-linecap="round"/>
  </g>
  <g clip-path="url(#bl)" fill="#F2B705">
    <circle cx="50" cy="50" r="11"/>
    <path d="M35.73 54.64 L30.03 56.49" fill="none" stroke="#F2B705" stroke-width="2.8" stroke-linecap="round"/>
    <g stroke="#0E1726" stroke-width="2.4"><circle cx="29" cy="69" r="5"/><circle cx="35" cy="65.5" r="6.5"/><circle cx="41" cy="69" r="5"/><rect x="24" y="68" width="22" height="7" rx="3.5"/></g>
    <g fill="#ECEDEF"><circle cx="29" cy="69" r="5"/><circle cx="35" cy="65.5" r="6.5"/><circle cx="41" cy="69" r="5"/><rect x="24" y="68" width="22" height="7" rx="3.5"/></g>
  </g>
  <g clip-path="url(#br)" fill="#ECEDEF">
    <circle cx="45" cy="50" r="8"/><circle cx="57" cy="48" r="9.5"/><rect x="35" y="52" width="35" height="10" rx="5"/>
    <path d="M55 67 L57 74 M62 67 L64 74 M69 67 L71 74" fill="none" stroke="#2E9BD6" stroke-width="2.8" stroke-linecap="round"/>
  </g>
  <g stroke="#EC6A1E" stroke-width="4" fill="none" stroke-linecap="square">
    <line x1="50" y1="50" x2="50" y2="82"/><line x1="18" y1="50" x2="82" y2="50"/>
    <rect x="18" y="18" width="64" height="64"/>
  </g>
"""


def lattice(width, height, cell, gap, rng):
    """Pack tiles of 1-2 cells wide and 1-2 tall over the canvas, glance-style."""
    cols, rows = width // cell + 2, height // cell + 2
    taken = set()
    tiles = []
    for r in range(rows):
        for c in range(cols):
            if (c, r) in taken:
                continue
            w = 2 if rng.random() < 0.35 and (c + 1, r) not in taken else 1
            h = 2 if rng.random() < 0.3 else 1
            for dc in range(w):
                for dr in range(h):
                    taken.add((c + dc, r + dr))
            tiles.append((c, r, w, h))
    off_x = (width - cols * cell) / 2
    off_y = (height - rows * cell) / 2
    out = []
    for c, r, w, h in tiles:
        x, y = off_x + c * cell + gap / 2, off_y + r * cell + gap / 2
        tw, th = w * cell - gap, h * cell - gap
        out.append(
            f'<rect x="{x:.0f}" y="{y:.0f}" width="{tw:.0f}" height="{th:.0f}" '
            f'rx="{cell * 0.14:.0f}" fill="{TILE}" stroke="{TILE_EDGE}" stroke-width="{cell * 0.012:.1f}"/>'
        )
        # A few tiles carry a hint of what a glance tile holds: a sun, rain.
        roll = rng.random()
        cx, cy = x + tw / 2, y + th / 2
        if roll < 0.10:
            out.append(f'<circle cx="{cx:.0f}" cy="{cy:.0f}" r="{cell * 0.13:.0f}" fill="{SUN}" opacity="0.55"/>')
        elif roll < 0.18:
            s, d = cell * 0.09, cell * 0.11
            out.append(
                f'<path d="M{cx - d:.0f} {cy - s:.0f} l{s * 0.4:.0f} {2 * s:.0f} '
                f'M{cx:.0f} {cy - s:.0f} l{s * 0.4:.0f} {2 * s:.0f} '
                f'M{cx + d:.0f} {cy - s:.0f} l{s * 0.4:.0f} {2 * s:.0f}" '
                f'stroke="{RAIN}" stroke-width="{cell * 0.035:.0f}" stroke-linecap="round" opacity="0.55"/>'
            )
        elif roll < 0.24:
            out.append(
                f'<rect x="{x + tw * 0.2:.0f}" y="{cy - cell * 0.02:.0f}" width="{tw * 0.6:.0f}" '
                f'height="{cell * 0.04:.0f}" rx="{cell * 0.02:.0f}" fill="{ACCENT}" opacity="0.45"/>'
            )
    return "\n  ".join(out)


def art(width, height, seed):
    rng = random.Random(seed)
    cell = round(height / 6.5)
    mark_px = height * 0.62  # the mark's 64-unit frame, in pixels
    scale = mark_px / 64
    tx, ty = width / 2 - 50 * scale, height / 2 - 50 * scale
    # The lattice fades out behind the mark so the mark reads first.
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<!-- Generated by generate-featuring-art.py; edit that, not this. -->
<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">
  <defs>
    <radialGradient id="hush" cx="50%" cy="50%" r="{0.5 * height / width * 100 * 1.6:.0f}%" gradientTransform="translate(0.5 0.5) scale({height / width:.4f} 1) translate(-0.5 -0.5)">
      <stop offset="0" stop-color="{FIELD}" stop-opacity="1"/>
      <stop offset="0.55" stop-color="{FIELD}" stop-opacity="0.92"/>
      <stop offset="1" stop-color="{FIELD}" stop-opacity="0.15"/>
    </radialGradient>
  </defs>
  <rect width="{width}" height="{height}" fill="{FIELD}"/>
  {lattice(width, height, cell, cell * 0.08, rng)}
  <rect width="{width}" height="{height}" fill="url(#hush)"/>
  <g transform="translate({tx:.1f} {ty:.1f}) scale({scale:.4f})">{MARK}</g>
</svg>
"""


def main():
    for name, w, h in [("featuring-header-5244x2950", 5244, 2950), ("featuring-search-3840x1646", 3840, 1646)]:
        svg = HERE / f"{name}.svg"
        svg.write_text(art(w, h, seed=7))
        png = HERE / f"{name}.png"
        subprocess.run(["rsvg-convert", "-w", str(w), "-h", str(h), "-b", FIELD, str(svg), "-o", str(png)], check=True)
        print(png.name)


if __name__ == "__main__":
    main()
