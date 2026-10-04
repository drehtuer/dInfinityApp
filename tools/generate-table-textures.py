#!/usr/bin/env python3
"""Make the built-in tables' textures from their CC0 sources.

docs/assets/README.md, "Table textures", says where each source comes from,
under which licence, and why it is cut the way it is. This is the recipe, so
that the files in the built-in package can be made again from the originals
rather than trusted as they are.

Needs Pillow (with WebP) and numpy, which are not in the devcontainer image:
like the font and logo scripts beside it, this runs about once in the life of
the project, so it is a one-off install rather than weight in every image.

    python3 -m pip install --target /tmp/pylib pillow numpy
    PYTHONPATH=/tmp/pylib python3 tools/generate-table-textures.py

It takes no arguments: the sources are downloaded into the repository's
`build/table-texture-sources` (and read from there on a second run), and the
pictures are written into the built-in package.

What it prints at the end is each picture's size and each colour picture's
linear mean. The tray scales a look's colour by one over that mean
(`color_mode = "average"`, docs/tables.md, "Textures"), so a mean far from
mid-grey is worth knowing about: under a sixteenth, the tray stops scaling.
"""

import io
import os
import sys
import urllib.request
import zipfile

import numpy as np
from PIL import Image

Image.MAX_IMAGE_PIXELS = None

FELT_ZIP = "https://ambientcg.com/get?file=Fabric034_1K-JPG.zip"
OAK = "https://dl.polyhaven.org/file/ph-assets/Textures"
OAK_FILES = {
    # The albedo is cut from the 8k so it carries 6.8 pixels to the millimetre;
    # the normal and the roughness from the 4k at half that, because a bump
    # and a sheen are softer things than a grain line.
    "albedo": f"{OAK}/jpg/8k/oak_wood_planks/oak_wood_planks_diff_8k.jpg",
    "normal": f"{OAK}/png/4k/oak_wood_planks/oak_wood_planks_nor_gl_4k.png",
    "roughness": f"{OAK}/png/4k/oak_wood_planks/oak_wood_planks_rough_4k.png",
}

# oak_wood_planks is 1200 mm square (Poly Haven's own figure), and the tray
# uses a 300 mm square of it: a quarter of each side.
OAK_SOURCE_MM = 1200.0
OAK_TILE_MM = 300.0


# Where everything goes, worked out from where this script is rather than
# taken from the command line: the script writes into the repository and
# downloads into its build folder, and nowhere else.
REPO = os.path.dirname(os.path.dirname(os.path.realpath(__file__)))
SOURCES = os.path.join(REPO, "build", "table-texture-sources")
OUT = os.path.join(REPO, "dicesets", "builtin", "src", "main", "resources", "dicesets", "builtin", "tables")

FELT_ALBEDO = "felt-albedo.webp"
FELT_NORMAL = "felt-normal.webp"
FELT_ROUGHNESS = "felt-roughness.webp"
OAK_ALBEDO = "oak-albedo.webp"
OAK_NORMAL = "oak-normal.webp"
OAK_ROUGHNESS = "oak-roughness.webp"


def fetch(url, into):
    # The file is named after the last part of the URL and nothing more: a
    # bare name, so a URL can never point the download outside [into].
    base = os.path.basename(url.rsplit("/", 1)[-1].split("=")[-1])
    if not base or base in (".", ".."):
        sys.exit(f"cannot name a download after {url}")
    name = os.path.join(into, base)
    if not os.path.exists(name):
        request = urllib.request.Request(url, headers={"User-Agent": "dinfinity-asset-prep"})
        with urllib.request.urlopen(request, timeout=600) as response:
            data = response.read()
        with open(name, "wb") as out:
            out.write(data)
    return name


def linear(srgb):
    return np.where(srgb <= 0.04045, srgb / 12.92, ((srgb + 0.055) / 1.055) ** 2.4)


def mean_linear(image):
    pixels = np.asarray(image.convert("RGB")).astype(np.float64) / 255.0
    return linear(pixels).reshape(-1, 3).mean(axis=0)


def grey(image):
    """An 8-bit grey picture of [image], whatever its depth: Poly Haven's
    roughness is a 16-bit PNG, and Pillow's own conversion clips it to white."""
    if image.mode in ("I;16", "I;16B", "I"):
        pixels = np.asarray(image).astype(np.float64) / 65535.0
        return Image.fromarray(np.round(pixels * 255).astype(np.uint8), "L")
    return image.convert("L")


def crop(image, source_mm, tile_mm):
    side = round(image.width * tile_mm / source_mm)
    return image.crop((0, 0, side, side))


def save(image, path, quality):
    image.save(path, "WEBP", quality=quality, method=6)
    return os.path.getsize(path)


def main():
    sources, out = SOURCES, OUT
    os.makedirs(sources, exist_ok=True)
    os.makedirs(out, exist_ok=True)

    felt_zip = fetch(FELT_ZIP, sources)
    with zipfile.ZipFile(felt_zip) as archive:
        felt = {
            name: Image.open(io.BytesIO(archive.read(f"Fabric034_1K-JPG_{name}.jpg")))
            for name in ("Color", "NormalGL", "Roughness")
        }
    # Grey, so that the look's own colour is the felt's colour: green felt and
    # black felt are the same cloth dyed differently.
    felt_albedo = felt["Color"].convert("L").convert("RGB")
    written = {
        FELT_ALBEDO: save(felt_albedo, os.path.join(out, FELT_ALBEDO), 85),
        FELT_NORMAL: save(felt["NormalGL"].convert("RGB"), os.path.join(out, FELT_NORMAL), 90),
        FELT_ROUGHNESS: save(
            felt["Roughness"].convert("L").convert("RGB"), os.path.join(out, FELT_ROUGHNESS), 85
        ),
    }

    oak = {name: Image.open(fetch(url, sources)) for name, url in OAK_FILES.items()}
    oak_albedo = crop(oak["albedo"].convert("RGB"), OAK_SOURCE_MM, OAK_TILE_MM)
    oak_normal = crop(oak["normal"].convert("RGB"), OAK_SOURCE_MM, OAK_TILE_MM)
    oak_rough = crop(grey(oak["roughness"]), OAK_SOURCE_MM, OAK_TILE_MM).convert("RGB")
    written[OAK_ALBEDO] = save(oak_albedo, os.path.join(out, OAK_ALBEDO), 82)
    written[OAK_NORMAL] = save(oak_normal, os.path.join(out, OAK_NORMAL), 90)
    written[OAK_ROUGHNESS] = save(oak_rough, os.path.join(out, OAK_ROUGHNESS), 85)

    for name, size in written.items():
        image = Image.open(os.path.join(out, name))
        print(f"{name:22} {image.width}x{image.height} {size / 1024:7.1f} KiB")
    print(f"{'total':22} {sum(written.values()) / 1024:17.1f} KiB")

    means = {
        "felt": mean_linear(Image.open(os.path.join(out, FELT_ALBEDO))),
        "oak": mean_linear(Image.open(os.path.join(out, OAK_ALBEDO))),
    }
    for texture, mean in means.items():
        print(f"{texture} albedo linear mean: {np.round(mean, 4)}")


if __name__ == "__main__":
    if len(sys.argv) != 1:
        sys.exit("usage: generate-table-textures.py (no arguments; it writes into this repository)")
    main()
