# Dice sets

A **dice set** is a folder containing a `diceset.toml` file and, optionally,
textures. The built-in dice are a dice set too; there is no privileged code
path for them. Anyone can publish a set on a git forge or as a plain archive
on any `https` server, and users can install it from inside the app.

The same package format also carries **tables** (the look of the dice tray);
see `docs/tables.md`. A package may contain dice, tables, or both.

Design rules, in priority order:

1. A set can never crash the app, hang it, fill its storage, or read anything
   outside its own folder.
2. A set that fails validation is rejected *entirely* with a readable report.
   There is no "partially installed" state.
3. A set that passes validation may still roll badly (e.g. an ugly, lopsided
   custom shape). That is the author's problem, not a safety problem; the
   physics rules still apply and the result is still read honestly.

## File layout

```
my-dice/
  diceset.toml
  textures/
    d20.png
    d6-face-1.png
    …
  README.md          (optional, shown in the app's set details)
  LICENSE            (optional, shown in the app's set details)
```

Only `diceset.toml` is required. Anything not referenced from it is ignored.

## `diceset.toml`

```toml
format = 1                       # schema version, required

[set]
id = "brass-and-bone"            # [a-z0-9-], 3..40 chars, unique per install
name = "Brass & Bone"
version = "1.2.0"                # semver, used for updates
author = "Ada Example"
license = "CC-BY-4.0"
description = "Brass numerals on bone-coloured resin."
homepage = "https://github.com/ada/brass-and-bone"

[defaults]                       # applied to every die unless overridden
color = "#e8dcc0"
number_color = "#8a6d1e"
roughness = 0.35
metallic = 0.0
size_mm = 16                     # clamped to 8..40
density = 1.2                    # g/cm³, clamped to 0.5..8
restitution = 0.3                # clamped to 0.0..0.8
friction = 0.5                   # clamped to 0.1..1.0

# --- dice -----------------------------------------------------------

[[die]]
id = "d6"                        # what the notation "d6" resolves to
shape = "cube"                   # from the shape catalogue
faces = [1, 2, 3, 4, 5, 6]       # value per face, in the catalogue's face order
texture = "textures/d6.png"      # atlas; layout defined by the catalogue shape

[[die]]
id = "d20"
shape = "icosahedron"
faces = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20]
texture = "textures/d20.png"
color = "#2b2b2b"                # per-die override

[[die]]
id = "d4"
shape = "tetrahedron"
read = "vertex-up"               # value at the top vertex, not the top face
faces = [1, 2, 3, 4]             # for vertex-up shapes: value per *vertex*

[[die]]
id = "d10-tens"
shape = "pentagonal-trapezohedron"
faces = [0, 10, 20, 30, 40, 50, 60, 70, 80, 90]
labels = ["00","10","20","30","40","50","60","70","80","90"]

[[die]]
id = "skull-d6"                  # any id; used as "brass-and-bone:skull-d6"
shape = "cube"
faces = [1, 2, 3, 4, 5, 6]
labels = ["💀", "2", "3", "4", "5", "6"]   # what is printed if no texture

[[die]]
id = "d7"
shape = "mesh"
mesh = "meshes/d7.obj"           # custom convex mesh, see below
faces = [1, 2, 3, 4, 5, 6, 7]
face_map = [0, 3, 1, 5, 2, 6, 4] # OBJ face index → faces[] index

# --- tables (optional, see docs/tables.md) ---------------------------

[[table]]
id = "bone-felt"
name = "Bone felt"
floor_texture = "tables/felt.png"
floor_tiling = [3, 6]
floor_color = "#d9cbb0"
wall_color = "#3a2a18"
sound = "felt"
```

### Fields

| Field | Required | Notes |
|---|---|---|
| `format` | yes | Integer. The app refuses formats newer than it knows. |
| `set.id` | yes | Slug. Used as the `setref` in notation and as the folder name. |
| `set.name`, `set.version` | yes | |
| `set.author`, `license`, `description`, `homepage` | no | Displayed only. `homepage` is shown as text, opened only on explicit tap, `https` only. |
| `defaults.*` | no | Material and physics defaults, all clamped. |
| `die.id` | yes | Slug, unique within the set. Standard names (`d2`…`d100`) are what plain notation resolves to. |
| `die.shape` | yes | Catalogue name or `mesh`. |
| `die.faces` | yes | Integer values, one per face (or vertex). Length must match the shape. Range −9999..9999. Duplicates allowed (d3-as-d6). |
| `die.labels` | no | Strings printed on faces when no texture. Defaults to `faces` as text. Max 4 characters each. |
| `die.read` | no | `face-up` (default) or `vertex-up`. |
| `die.texture` | no | Path to a PNG/WebP atlas, relative, inside the set folder. |
| `die.mesh` | for `mesh` shape | Relative path to a Wavefront OBJ. |
| `die.face_map` | for `mesh` shape | Maps OBJ polygon index to entry in `faces`. Coplanar OBJ polygons must map to the same face. |
| `die.color`, `number_color`, `roughness`, `metallic`, `size_mm`, `density`, `restitution`, `friction` | no | Per-die overrides of `defaults`. |
| `table.*` | no | Table looks; fields and limits in `docs/tables.md`. |

## Shape catalogue

Built-in shapes, with face count, face order and texture atlas layout defined
by the app (documented in `dicesets/format/shapes/` with reference images):

| Name | Faces | Typical use |
|---|---|---|
| `coin` | 2 | d2 |
| `triangular-prism` | 3 (+2 capped ends that are never "up") | d3 |
| `tetrahedron` | 4 | d4 |
| `cube` | 6 | d6, d3-as-d6, d2-as-d6 |
| `octahedron` | 8 | d8 |
| `pentagonal-trapezohedron` | 10 | d10, d10-tens |
| `dodecahedron` | 12 | d12 |
| `enneagonal-trapezohedron` | 18 | d18 |
| `icosahedron` | 20 | d20 |
| `rhombic-triacontahedron` | 30 | d30 |
| `mesh` | n | anything convex |

The atlas layout for a catalogue shape is a fixed grid: face *i* occupies cell
*i* of an N-cell grid, each cell square, drawn with the face's "up" direction
matching the catalogue's reference orientation. The face designer produces
exactly this layout, so hand-drawn and hand-authored sets are interchangeable.

## Custom meshes

- Wavefront OBJ, ASCII, vertices and faces only (normals/UVs optional and
  ignored except `vt` for texture mapping).
- Limits: ≤ 2,000 vertices, ≤ 2,000 polygons, file ≤ 512 KiB.
- The mesh must be **convex**: the convex hull of the vertices must contain
  every vertex on or within 1% of the bounding radius of the hull surface.
  Non-convex → rejected.
- Must be **closed**: every edge shared by exactly two polygons.
- Must have ≥ 2 distinct "landing" faces: after hull computation, faces are
  clustered by normal; each cluster is a potential result face. `face_map`
  must cover every cluster exactly once.
- Scaled to `size_mm` by longest axis; the centroid becomes the origin.
- Physics uses the convex hull; rendering uses the source mesh.

A perfectly legal custom mesh may still be a terrible die (e.g. a very flat
one that always lands on two faces). The set browser shows a **fairness
preview**: the app rolls the die 1,000 times in power-saving mode on import
and shows the face histogram. It is informational only.

## Textures

- PNG or WebP. Max 2048×2048, max 4 MiB per file, max 24 MiB per set.
- Image dimensions are read from the header *before* decoding
  (`inJustDecodeBounds`); oversized images are rejected without decoding.
- Decoded on the IO dispatcher into a size-capped bitmap pool; never on the
  main thread.
- Textures are optional per die and per face: an atlas may leave cells
  transparent, in which case the label is rendered in `number_color` on top
  of the die colour for that face.

## Installing from a URL or file

Users paste a URL. Accepted sources:

| Source | Example | How it is fetched |
|---|---|---|
| GitHub | `https://github.com/ada/brass-and-bone`<br>`…/tree/v1.2.0`<br>`…/tree/main/sets/skulls` | Ref resolved to a commit SHA via the API, tarball for that SHA |
| GitLab (gitlab.com or self-hosted) | `https://gitlab.com/ada/brass-and-bone/-/tree/main` | Same, via the GitLab API |
| Codeberg / Gitea / Forgejo | `https://codeberg.org/ada/brass-and-bone` | Same, via the Gitea API |
| Any archive | `https://example.org/dice/brass.zip`<br>`https://example.org/dice/brass.tar.gz` | Direct download; the SHA-256 of the archive is recorded in place of a commit SHA |
| Local file | picked via the system file picker | `.zip`, `.tar.gz` or a folder |

The forge integrations exist for convenience (browse to a repo, paste the
URL, get updates). They are not what makes an install safe — the validator
is, and it runs identically for every source. Plain `http://` is refused.
Redirects are followed only to `https` and at most 5 hops. Downloads are
capped at 64 MiB and time out after 60 s.

Install flow:

1. Recognise the source from the URL. Unknown hosts are treated as "any
   archive" and must end in `.zip`, `.tar.gz` or `.tgz`.
2. Fetch the archive and record its identity (commit SHA or archive
   SHA-256) so updates are diffable and the install is reproducible.
3. Stream-extract into a **temporary** folder with these checks:
   - Reject absolute paths, `..`, symlinks, hard links, device files.
   - Reject total uncompressed size > 64 MiB or > 500 entries.
   - Only extract files whose extensions are on the allowlist
     (`toml, png, webp, obj, md, txt, LICENSE`).
4. Locate `diceset.toml` (at the root or at the given subfolder).
5. Run the validator (below). On failure: delete the temp folder, show the
   report.
6. On success: run the fairness preview for custom-mesh dice, then move the
   folder atomically to `dicesets/<set.id>/`. If a set with that id exists,
   ask to replace (versions are compared).
7. Write `.meta.json` with the source URL, SHA, timestamp and validator
   output.

The app never runs anything from the repository. No scripts, no build steps.

Updates: "Check for updates" re-resolves the ref (forges) or re-downloads
the archive headers and compares checksums (plain URLs); if the identity
differs and the new `set.version` is higher, offer to reinstall.

The same fetch and extraction path is used for saved-roll collections
(`docs/dice-notation.md`), which are a single JSON file rather than a folder.

## Validation

The validator runs the same way for URL installs, local folder/zip imports,
face designer exports and the built-in set. It produces a report of
`error` / `warning` lines with file and line references.

Errors (set is rejected):

- TOML syntax error, unknown `format`, missing required fields
- Bad slug, duplicate die id
- Unknown shape; `faces` length ≠ shape face count
- `face_map` incomplete, duplicated or out of range
- Referenced file missing, outside the folder, wrong extension, over size
- Mesh not convex / not closed / over limits
- Texture over dimension or byte limits, not decodable
- Any numeric physics value non-finite

Warnings (set installs, user sees them):

- Physics value clamped
- A standard die id missing (e.g. no `d12`) — notation will fall back
- Label longer than 4 chars truncated
- Texture atlas has empty cells
- Mesh fairness histogram badly skewed (max/min face frequency > 3×)

The parser is a strict, hand-written (or `tomlkt` with a fixed schema) TOML
reader into plain data classes. No reflection, no polymorphic deserialization,
no default-on-error. Unknown keys are ignored with a warning to allow future
extensions.

## Runtime isolation

- Each set's data lives in its own folder; file references are resolved
  against that folder and rejected if canonicalised paths escape it.
- Loading a set at startup is lazy and wrapped: if a previously installed set
  fails to load (corrupted storage, app upgrade with stricter rules), it is
  marked *disabled* with a reason, and the app continues with the built-in
  set. Notation that references it falls back and the breakdown says so.
- Physics/rendering never trust set data directly: every value passes through
  the clamps again at load time, and the convex hull is recomputed on load.

## Authoring tips

- Start by exporting the built-in set from the app ("Export as template");
  it is a normal dice set with all catalogue shapes and blank atlases.
- Keep textures at 1024×1024 for a d20; nobody will see more on a phone.
- Use `labels` for symbol dice (a d6 with a skull on the 1) so the set works
  even before you draw textures.
