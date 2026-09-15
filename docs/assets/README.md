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
something the player chooses in Settings (`core/model/AccentColor`), but the
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

What it writes is the digits, the two signs, a times, a per cent and a full
stop, as closed polygons in em units with the curves already flattened — a die
turns them into a distance field once and the field is what the shader reads
(`docs/physics-and-rendering.md`). It is a resource rather than Kotlin so that
it stays diffable and stays inside the hundred-and-twenty-column rule the
linters hold everything else to.

## Font licence

Archivo is by Omnibus-Type, under the
[SIL Open Font License 1.1](https://openfontlicense.org/). The OFL permits
embedding outlines in a work like this; the font itself is not redistributed
here, only the two glyphs of the mark and the sixteen a die is printed with,
as paths.
