"""Generate the dInfinity mark from Archivo outlines.

The mark is the notation: `d` in ink, an accent-coloured infinity, Archivo 800
at -0.05em tracking (design/Logo.dc.html).
"""
import argparse
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.boundsPen import BoundsPen


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description="Generate the dInfinity mark.")
    parser.add_argument("font", type=Path, help="Archivo variable font (.ttf)")
    parser.add_argument("out", type=Path, help="repository root to write into")
    return parser.parse_args(argv)


args = parse_args()
FONT_PATH = args.font.resolve(strict=True)
OUT = args.out.resolve(strict=True)


def out_file(*parts):
    """A path under OUT, with its directory made.

    Both arguments come from the command line, so the destination is resolved
    and checked to be inside OUT before anything is written. Nothing hostile is
    expected — a developer runs this by hand, about once in the life of the
    project — but a generator that can be talked into writing outside the tree
    it was pointed at is worth a few lines to rule out.
    """
    destination = OUT.joinpath(*parts).resolve()
    if not destination.is_relative_to(OUT):
        raise ValueError(f"refusing to write outside {OUT}: {destination}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    return destination


font = TTFont(FONT_PATH)
font = instancer.instantiateVariableFont(font, {"wght": 800, "wdth": 100})
UPEM = font["head"].unitsPerEm
cmap, gs, hmtx = font.getBestCmap(), font.getGlyphSet(), font["hmtx"]

def glyph(ch):
    name = cmap[ord(ch)]
    pen = SVGPathPen(gs); gs[name].draw(pen)
    bp = BoundsPen(gs); gs[name].draw(bp)
    return {"path": pen.getCommands(), "adv": hmtx[name][0], "bounds": bp.bounds}

D, INF = glyph("d"), glyph("\u221e")
TRACK = -0.05 * UPEM                      # -.05em, as the design specifies
INF_X = D["adv"] + TRACK

# Ink extent of the whole mark, so it can be cropped tight and centred exactly.
x0 = min(D["bounds"][0], INF_X + INF["bounds"][0])
y0 = min(D["bounds"][1], INF["bounds"][1])
x1 = max(D["bounds"][2], INF_X + INF["bounds"][2])
y1 = max(D["bounds"][3], INF["bounds"][3])
W, H = x1 - x0, y1 - y0

INK_LIGHT, INK_DARK, PAPER, BLUE = "#201E1D", "#F3F2F2", "#F3F2F2", "#1F92CC"

def svg(ink, infinity, pad=0.0):
    """Mark as a standalone SVG, y flipped out of font space."""
    w, h = W + 2 * pad, H + 2 * pad
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w:.0f} {h:.0f}" \
width="{w / 8:.0f}" height="{h / 8:.0f}" role="img" aria-label="dInfinity">
  <title>dInfinity</title>
  <g transform="translate({pad - x0:.2f} {pad + y1:.2f}) scale(1 -1)">
    <path fill="{ink}" d="{D['path']}"/>
    <g transform="translate({INF_X:.2f} 0)"><path fill="{infinity}" d="{INF['path']}"/></g>
  </g>
</svg>
'''

out_file("docs", "assets", "logo-light.svg").write_text(svg(INK_LIGHT, BLUE, pad=40))
out_file("docs", "assets", "logo-dark.svg").write_text(svg(INK_DARK, BLUE, pad=40))

# --- Android adaptive icon -------------------------------------------------
# 108dp viewport; art must stay inside the middle 72dp or a round mask clips it.
VP, SAFE = 108.0, 66.0
scale = min(SAFE / W, SAFE / H)
tx = (VP - W * scale) / 2 - x0 * scale
ty = (VP - H * scale) / 2 + y1 * scale     # y flipped below

def vector(dfill, inffill):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated from Archivo 800 (SIL OFL 1.1); see docs/assets/README.md. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
  android:width="108dp"
  android:height="108dp"
  android:viewportWidth="108"
  android:viewportHeight="108">
  <group
    android:translateX="{tx:.3f}"
    android:translateY="{ty:.3f}"
    android:scaleX="{scale:.6f}"
    android:scaleY="{-scale:.6f}">
    <path
      android:fillColor="{dfill}"
      android:pathData="{D['path']}" />
    <group android:translateX="{INF_X:.2f}">
      <path
        android:fillColor="{inffill}"
        android:pathData="{INF['path']}" />
    </group>
  </group>
</vector>
'''

RES = ("app", "src", "main", "res", "drawable")
out_file(*RES, "ic_launcher_foreground.xml").write_text(vector(PAPER, BLUE))
out_file(*RES, "ic_launcher_monochrome.xml").write_text(vector("#FFFFFF", "#FFFFFF"))
print(f"mark {W:.0f}x{H:.0f} units, icon scale {scale:.4f}")
