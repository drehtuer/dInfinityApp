"""Generate the built-in die font from Archivo outlines.

A die with no artwork prints its labels, and they have to be drawn from
something. This turns real Archivo outlines into the polygon contours
`core/glyphs` keeps — the same source as the mark
(docs/assets/README.md), for the same reason: a traced approximation would
be somebody's guess at a typeface, and live text would render in whatever
font the device happens to have.

Curves are flattened here rather than on the phone. A die's numbers are
turned into a distance field once per die and the field is what the shader
reads, so the only thing a cubic would buy at run time is arithmetic nobody
can see — and it would buy it in a module that has to stay plain Kotlin.
"""
import argparse
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from fontTools.pens.basePen import BasePen

# What a face may be printed with. Everything the built-in set uses, plus the
# characters a value can be written in, because a label the font cannot draw
# falls back to the face's value (docs/dice-sets.md).
CHARACTERS = "0123456789+-−×%."

# Where a flattened curve may sit from the true one, in em units. A die's
# atlas cell is 64 pixels across and a digit fills about two fifths of it, so
# an em is roughly 40 pixels: a 150th of an em is a quarter of a pixel, which
# is finer than a distance field sampled at that size can show.
TOLERANCE = 1.0 / 150.0


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description="Generate the built-in die font.")
    parser.add_argument("font", type=Path, help="Archivo variable font (.ttf)")
    parser.add_argument("out", type=Path, help="repository root to write into")
    parser.add_argument("--weight", type=int, default=700, help="wght to instantiate at")
    return parser.parse_args(argv)


def out_file(out, *parts):
    """A path under `out`, with its directory made.

    Both arguments come from the command line, so the destination is resolved
    and checked to be inside `out` before anything is written. Nothing hostile
    is expected — a developer runs this by hand, about once in the life of the
    project — but a generator that can be talked into writing outside the tree
    it was pointed at is worth a few lines to rule out. The same guard
    `generate-logo.py` carries, for the same reason.
    """
    root = out.resolve(strict=True)
    destination = root.joinpath(*parts).resolve()
    if not destination.is_relative_to(root):
        raise ValueError(f"refusing to write outside {root}: {destination}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    return destination


class FlattenPen(BasePen):
    """Outlines as closed polygons, in em units with the baseline at zero."""

    def __init__(self, glyph_set, units_per_em, tolerance):
        super().__init__(glyph_set)
        self.scale = 1.0 / units_per_em
        self.tolerance = tolerance
        self.contours = []
        self._current = None

    def _point(self, pt):
        return (pt[0] * self.scale, pt[1] * self.scale)

    def _moveTo(self, pt):
        self._current = [self._point(pt)]

    def _lineTo(self, pt):
        self._current.append(self._point(pt))

    def _curveToOne(self, a, b, c):
        start = self._current[-1]
        p1, p2, p3 = self._point(a), self._point(b), self._point(c)
        for step in range(1, self._segments(start, p1, p2, p3) + 1):
            t = step / self._segments(start, p1, p2, p3)
            self._current.append(self._at(start, p1, p2, p3, t))

    def _closePath(self):
        self._finish()

    def _endPath(self):
        self._finish()

    def _finish(self):
        if self._current is None:
            return
        points = self._current
        # A closing point equal to the first is implied by the contour being
        # closed, and a duplicate would be a zero-length edge in the field.
        while len(points) > 1 and self._near(points[0], points[-1]):
            points.pop()
        if len(points) >= 3:
            self.contours.append(points)
        self._current = None

    def _near(self, a, b):
        return abs(a[0] - b[0]) < 1e-9 and abs(a[1] - b[1]) < 1e-9

    def _segments(self, p0, p1, p2, p3):
        """Enough that the flattened curve stays inside the tolerance.

        A cubic's distance from the chords of an n-way split falls as
        L / (8 n^2), where L bounds the second derivative and the control
        polygon bounds L. So n = sqrt(L / 8 eps), and the control polygon is
        both an upper bound and cheap to measure.
        """
        length = sum(
            ((b[0] - a[0]) ** 2 + (b[1] - a[1]) ** 2) ** 0.5
            for a, b in zip((p0, p1, p2), (p1, p2, p3))
        )
        return max(2, min(24, int((length / (8 * self.tolerance)) ** 0.5) + 1))

    def _at(self, p0, p1, p2, p3, t):
        u = 1.0 - t
        return (
            u * u * u * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t * t * t * p3[0],
            u * u * u * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t * t * t * p3[1],
        )


def main(argv=None):
    args = parse_args(argv)
    font = TTFont(args.font.resolve(strict=True))
    if "fvar" in font:
        font = instancer.instantiateVariableFont(font, {"wght": args.weight, "wdth": 100})
    upem = font["head"].unitsPerEm
    cmap, glyph_set, hmtx = font.getBestCmap(), font.getGlyphSet(), font["hmtx"]

    glyphs = {}
    for character in CHARACTERS:
        name = cmap.get(ord(character))
        if name is None:
            raise SystemExit(f"the font has no glyph for {character!r}")
        pen = FlattenPen(glyph_set, upem, TOLERANCE)
        glyph_set[name].draw(pen)
        glyphs[character] = (hmtx[name][0] / upem, pen.contours)

    destination = out_file(args.out, "core/glyphs/src/main/resources/glyphs/builtin-font.txt")
    destination.write_text(render(glyphs, args.weight), encoding="utf-8")
    print(f"wrote {destination} ({destination.stat().st_size} bytes)")
    for character, (advance, contours) in glyphs.items():
        print(f"  {character!r}: advance {advance:.3f}, {len(contours)} contours, "
              f"{sum(len(c) for c in contours)} points")


def number(value):
    """Five decimals, which is a fiftieth of a pixel in a 64-pixel cell."""
    text = f"{value:.5f}".rstrip("0").rstrip(".")
    return "0" if text in ("", "-0") else text


def wrapped(values, width=100):
    """The numbers, one long row folded so no line is wider than the linters allow."""
    lines, line = [], ""
    for value in values:
        if line and len(line) + 1 + len(value) > width:
            lines.append(line)
            line = value
        else:
            line = f"{line} {value}" if line else value
    if line:
        lines.append(line)
    return lines


def render(glyphs, weight):
    lines = [
        "# The built-in die font: what a face is printed with when its die has no",
        "# artwork (docs/physics-and-rendering.md).",
        "#",
        "# GENERATED by tools/generate-font.py from real Archivo outlines - do not",
        "# edit. Archivo is by Omnibus-Type under the SIL Open Font License 1.1,",
        "# which permits embedding outlines in a work like this; the font itself is",
        "# not redistributed here, only these glyphs, as paths",
        "# (docs/assets/README.md).",
        "#",
        "# Em units, baseline at zero, up positive. Curves are already flattened: a",
        "# die's numbers become a signed distance field once per die, so a cubic on",
        "# the phone would buy arithmetic nobody can see.",
        "#",
        "#   font <family> <weight>",
        "#   glyph <code point, hex> <advance>",
        "#   contour",
        "#   <x> <y> <x> <y> ...            (folded over as many lines as it takes)",
        "",
        f"font archivo {weight}",
    ]
    for character, (advance, contours) in glyphs.items():
        lines.append("")
        lines.append(f"glyph {ord(character):04x} {number(advance)}")
        for points in contours:
            lines.append("contour")
            lines.extend(wrapped([number(v) for point in points for v in point]))
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    main()
