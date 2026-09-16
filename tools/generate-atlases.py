"""Generate the blank texture atlases the example dice set ships.

Somebody starting a dice set has to draw on something, and the thing they have
to draw on is not obvious: a die's artwork is one image per die, cut into a
grid the *app* chooses from the face count (`core/model`'s `ShapeAtlas`, and
docs/dice-sets.md, "Shape catalogue"). Guessing that grid and getting it wrong
produces an atlas that validates, installs and comes out sliced down the middle
of every face.

So `examples/textures/` carries one correctly sized, fully transparent PNG per
catalogue shape, and this is what writes them. Transparent rather than ruled:
the atlas is composited over the die's printed labels by its own alpha
(docs/dice-sets.md, "Labels, and the artwork over them"), so an undrawn cell
shows the number the app prints and a guide line drawn here would be a guide
line on the die. The file is the grid; the editor's checkerboard is the ruler.

The catalogue is repeated below rather than read out of `DieShape.kt`, because
a Python parser for Kotlin would be the fragile part. What keeps the two in
step is a test — `ExampleDiceSetTest` asks the real `DieShape` and the real
`ShapeAtlas` whether every file here is the size it should be, so a shape added
to the catalogue fails the build until this has been run again.

Nothing but the standard library: this writes a PNG of one colour, which is
about forty lines of zlib, and a dependency to do it would be a dependency to
install before anybody could regenerate eight blank pictures.

    python3 tools/generate-atlases.py .
"""
import argparse
import math
import struct
import zlib
from pathlib import Path

# The v1 shape catalogue: the name written as `shape = "…"` in a set file, and
# how many entries its `faces` takes. Closed in v1 (docs/dice-sets.md).
SHAPES = {
    "coin": 2,
    "tetrahedron": 4,
    "cube": 6,
    "octahedron": 8,
    "pentagonal-trapezohedron": 10,
    "dodecahedron": 12,
    "enneagonal-trapezohedron": 18,
    "icosahedron": 20,
}

# How many pixels one face gets. The same 256 the face designer's export uses,
# so a drawn set and a hand-authored one are the same kind of file — and well
# inside the 2048-pixel cap, which the widest grid (5 cells) reaches at 1280.
CELL_PIXELS = 256

# PNG colour type 6 at 8 bits: RGBA, straight alpha, which is what the decoder
# asks Android for (docs/dice-sets.md, "How an atlas reaches the tray").
BIT_DEPTH = 8
COLOUR_TYPE_RGBA = 6
BYTES_PER_PIXEL = 4

# Deflate at its slowest and smallest. A run this short happens by hand once,
# and the same level always produces the same bytes, which is what makes
# re-running this a no-op in `git status` rather than a diff.
COMPRESSION = 9


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description="Generate the example set's blank atlases.")
    parser.add_argument("out", type=Path, help="repository root to write into")
    return parser.parse_args(argv)


def out_file(out, *parts):
    """A path under `out`, with its directory made.

    The destination comes from the command line, so it is resolved and checked
    to be inside `out` before anything is written. Nothing hostile is expected
    — a developer runs this by hand — but a generator that can be talked into
    writing outside the tree it was pointed at is worth a few lines to rule
    out. The same guard `generate-font.py` and `generate-logo.py` carry.
    """
    root = out.resolve(strict=True)
    destination = root.joinpath(*parts).resolve()
    if not destination.is_relative_to(root):
        raise ValueError(f"refusing to write outside {root}: {destination}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    return destination


def grid_for(faces):
    """The cell grid a shape of `faces` faces uses: columns, then rows.

    The arithmetic `ShapeAtlas.gridFor` does, and it has to stay the same
    arithmetic: as square as it can be, widest first, so a d20 is 5x4 rather
    than 4x5 and face *i* lands in cell *i* reading left to right.
    """
    columns = math.ceil(math.sqrt(faces))
    return columns, math.ceil(faces / columns)


def chunk(tag, payload):
    """One PNG chunk: length, name, payload, and the CRC over the last two."""
    body = tag + payload
    return struct.pack(">I", len(payload)) + body + struct.pack(">I", zlib.crc32(body))


def transparent_png(width, height):
    """A `width` by `height` RGBA PNG in which every pixel is clear.

    Every scanline is a filter byte of zero followed by zero pixels, which
    deflate turns into a few hundred bytes however large the picture is.
    """
    scanline = b"\x00" * (1 + width * BYTES_PER_PIXEL)
    header = struct.pack(">IIBBBBB", width, height, BIT_DEPTH, COLOUR_TYPE_RGBA, 0, 0, 0)
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", header)
        + chunk(b"IDAT", zlib.compress(scanline * height, COMPRESSION))
        + chunk(b"IEND", b"")
    )


def main(argv=None):
    args = parse_args(argv)
    for shape, faces in SHAPES.items():
        columns, rows = grid_for(faces)
        width, height = columns * CELL_PIXELS, rows * CELL_PIXELS
        destination = out_file(args.out, "examples", "textures", f"{shape}.png")
        destination.write_bytes(transparent_png(width, height))
        print(f"{shape}: {faces} faces, {columns}x{rows} cells, {width}x{height} px -> {destination}")


if __name__ == "__main__":
    main()
