# Logo files

The mark is the notation: **d∞**, Archivo 800 at −0.05em tracking, the infinity
in the identity's blue. It is specified in
[design/Logo.dc.html](../../design/Logo.dc.html) — "no drawn die, because the
app is about any die, so the name does the work".

| File | Used by |
| --- | --- |
| `logo-light.svg` | `README.md` on a light background |
| `logo-dark.svg` | `README.md` on a dark background, via `<picture>` |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | The launcher icon |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | Themed icons, Android 13+ |

## The blue is not the accent

The infinity is `#1F92CC`, and it stays `#1F92CC`. The interface accent is
something the player chooses in Settings — a preset or any colour at all
(`core/model/AccentChoice`) — but the
identity is not theirs to change, and a launcher icon cannot follow a runtime
setting anyway.

## Regenerating

The glyphs are real Archivo outlines, converted to paths — not a traced
approximation and not live text, which would render in whatever font the
viewer happens to have. [../../tools/generate-logo.py](../../tools/generate-logo.py)
produces every file above from the font.

fontTools is not in the devcontainer image: this runs about once in the life of
the project, so it is a one-off install rather than weight in every image.

```sh
sudo apt-get install -y python3-fonttools
curl -sSL --proto '=https' -o /tmp/Archivo.ttf \
  'https://github.com/google/fonts/raw/main/ofl/archivo/Archivo%5Bwdth,wght%5D.ttf'
python3 tools/generate-logo.py /tmp/Archivo.ttf .
```

The script instantiates the variable font at `wght=800`, so the outlines match
the weight the design names rather than a faux-bolded 400.

## The die font comes from the same place

The numbers on a die with no artwork are Archivo too, at weight 700, and they
are generated the same way — by
[../../tools/generate-font.py](../../tools/generate-font.py), into
`core/glyphs/src/main/resources/glyphs/builtin-font.txt`. Same one-off install,
same font file:

```sh
python3 tools/generate-font.py /tmp/Archivo.ttf .
```

What it writes is the digits, three signs — `+`, the hyphen-minus `-` and the
typographic minus `−` (U+2212) — a times, a per cent and a full stop, as closed
polygons in em units with the curves already flattened. A die turns them into
a distance field once and the field is what the shader reads
(`docs/physics-and-rendering.md`). It is a resource rather than Kotlin so that
it stays diffable and stays inside the hundred-and-twenty-column rule the
linters hold everything else to.

## Font licence

Archivo is by Omnibus-Type, under the
[SIL Open Font License 1.1](https://openfontlicense.org/). The OFL permits
embedding outlines in a work like this; the font itself is not redistributed
here, only the two glyphs of the mark and the sixteen a die is printed with,
as paths.

## The room the dice are lit by

The tray's image-based light is a photographed panorama, not a drawing:
**Brown Photostudio 02** by Sergej Majboroda, from
[Poly Haven](https://polyhaven.com/a/brown_photostudio_02), at 1k (1024 × 512)
in Radiance `.hdr`. It is shipped unmodified as
`render/filament/src/main/resources/de/drehtuer/dinfinity/render/filament/brown_photostudio_02_1k.hdr`
(1,648,130 bytes, MD5 `1362911793f932724326e6e56421f102`, as Poly Haven lists
it), and why that panorama and that size is in
`docs/physics-and-rendering.md`, "Rendering (normal mode)".

Poly Haven releases its assets under
[CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/): no rights
reserved, no attribution required. It is credited here anyway.

Changing it means re-projecting its irradiance into `StudioLight.IRRADIANCE`,
and `StudioLightTest` fails until that is done: run `StudioLight.irradianceOf`
over the new file's decoded pixels (the test's `decoded()` shows how) and
paste the result.

## Table textures

The bundled felt and oak (`docs/tables.md`, "Textures") are photographs, cut
and re-encoded from two CC0 sources by
[../../tools/generate-table-textures.py](../../tools/generate-table-textures.py)
into `dicesets/builtin/src/main/resources/dicesets/builtin/tables/`.

| File | Size | From | Cut |
| --- | --- | --- | --- |
| `felt-albedo.webp` | 1024², 234 KiB | ambientCG [Fabric034](https://ambientcg.com/a/Fabric034), `1K-JPG` colour | made grey, so one cloth is both felts; laid at 80 mm a copy (12.8 px/mm) |
| `felt-normal.webp` | 1024², 446 KiB | the same, `NormalGL` | as it is |
| `felt-roughness.webp` | 1024², 175 KiB | the same, roughness | as it is |
| `oak-albedo.webp` | 2048², 281 KiB | Poly Haven [oak_wood_planks](https://polyhaven.com/a/oak_wood_planks), 8k diffuse | the top-left 300 mm of the 1200 mm board (6.8 px/mm) |
| `oak-normal.webp` | 1024², 189 KiB | the same, 4k `nor_gl` | the same 300 mm (3.4 px/mm) |
| `oak-roughness.webp` | 1024², 230 KiB | the same, 4k roughness (16-bit) | the same 300 mm, to 8 bits |

**1.5 MiB together**, which is what they add to the APK — WebP is stored
compressed already, so the zip gains nothing on them. `BuiltinDiceSetTest`
fails past 2 MiB so that growing them is a decision rather than a drift.

**Why these sizes.** On the Pixel 10a the whole tray is about 10 screen pixels
to the millimetre. The felt at 12.8 px/mm is sharper than the screen can show,
so a 1024 swatch is enough and repeats every 80 mm, which at the felt's grain
is not seen to repeat. The oak is a board, not a swatch: a 300 mm cut covers
the whole floor once, so it never repeats there, and its colour picture is the
one 2048 here because grain lines are the detail an eye goes to; its bumps and
sheen are softer and stay at 1024. The oak's cut does not tile, so on the walls,
which run round 700 mm of tray, it meets itself two or three times — where a
board would have a joint anyway.

**Licence.** Both sources are [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/)
— ambientCG's and Poly Haven's own terms for every asset they publish. CC0
asks for nothing, not even attribution; the sources are recorded here so the
files can be made again from the originals and checked against them, not
because a licence requires it.

Pillow and numpy are not in the devcontainer image, for the reason fontTools
is not: this runs about once.

```sh
python3 -m pip install --target /tmp/pylib pillow numpy
PYTHONPATH=/tmp/pylib python3 tools/generate-table-textures.py
```

It takes no arguments: it downloads into `build/table-texture-sources` and
writes into the built-in package, both found from where the script is.
