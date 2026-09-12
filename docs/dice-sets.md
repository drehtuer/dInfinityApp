# Dice sets

> **Design:** the installed-set list (options 1s and 1t), the set details
> (6a, and 6b for a failed validation), the disable/remove dialog (5a) and the
> update flow (9h–9i) are in the [clickable design](../design/dInfinity.dc.html)
> ([design/](../design/)).

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

```text
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
id = "skull-d6"                  # any id; reached from the dice picker
shape = "cube"
faces = [1, 2, 3, 4, 5, 6]
labels = ["💀", "2", "3", "4", "5", "6"]   # what is printed if no texture

[[die]]
id = "d4"
shape = "tetrahedron"
read = "vertex-up"
faces = [1, 2, 3, 4]
color = "#f0e6d0"

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
| `set.id` | yes | Slug of 3–40 characters (`[a-z0-9-]`, starting and ending with a letter or digit). Used as the `setref` in notation and as the folder name, which is why it has a floor. |
| `set.name`, `set.version` | yes | |
| `set.author`, `license`, `description`, `homepage` | no | Displayed only. `homepage` is shown as text, opened only on explicit tap, `https` only. |
| `defaults.*` | no | Material and physics defaults, all clamped. |
| `die.id` | yes | Slug of 1–40 characters, unique within the set — shorter than a set id, because `d2`, `d4` and `d6` are the ids plain notation resolves. Standard names (`d2`…`d100`, `d10-tens`, `df`) are what typed notation resolves, optionally set-qualified as `brass:2d20`. A die with any other id is rolled by tapping it in the dice picker — the grammar in `docs/dice-notation.md` has no unambiguous way to write `skull-d6kh1`, since a slug and a modifier are made of the same characters. |
| `die.shape` | yes | A name from the shape catalogue below. v1 has no other option. |
| `die.faces` | yes | Integer values, one per face (or vertex). Length must match the shape. Range −9999..9999. Duplicates allowed (d2-as-d6). |
| `die.labels` | no | Strings printed on faces when no texture. Defaults to `faces` as text. Max 4 characters each. |
| `die.read` | no | `face-up` (default) or `vertex-up`. |
| `die.texture` | no | Path to a PNG/WebP atlas, relative, inside the set folder. |
| `die.color`, `number_color`, `roughness`, `metallic`, `size_mm`, `density`, `restitution`, `friction` | no | Per-die overrides of `defaults`. |
| `table.*` | no | Table looks; fields and limits in `docs/tables.md`. |

## Shape catalogue

The catalogue is **closed in v1**: these eight shapes and nothing else. Face
count, face order and texture atlas layout are defined by the app (documented
in `dicesets/format/shapes/` with reference images).

| Name | Faces | Typical use |
|---|---|---|
| `coin` | 2 | d2 |
| `tetrahedron` | 4 | d4 |
| `cube` | 6 | d6, d2-as-d6 |
| `octahedron` | 8 | d8 |
| `pentagonal-trapezohedron` | 10 | d10, d10-tens (together: d100 / `d%`) |
| `dodecahedron` | 12 | d12 |
| `enneagonal-trapezohedron` | 18 | d18 |
| `icosahedron` | 20 | d20 |

A set is free to define any number of dice on these shapes, with any face
values and any artwork — a d6 of runes and a d20 of skulls are both just
`cube` and `icosahedron`. What v1 does not accept is a *new solid*: a d30, or
a shape supplied as a mesh. See "Shapes after v1" below.

The atlas layout for a catalogue shape is a fixed grid: face *i* occupies cell
*i* of an N-cell grid, each cell square, drawn with the face's "up" direction
matching the catalogue's reference orientation. The face designer produces
exactly this layout, so hand-drawn and hand-authored sets are interchangeable.

## Shapes after v1

Everything in this section is **planned, not implemented**. v1 rejects
`shape = "mesh"` at validation with "unknown shape"; a set that uses it does
not install. The design is written down here so the format does not have to
change shape later, and so nobody has to rediscover the constraints.

Two things are wanted: more catalogue solids (a d30 as a
`rhombic-triacontahedron` is the obvious first one) and author-supplied
convex meshes. The catalogue is easy — it is one more entry per solid. The
mesh path is the one that needs rules:

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

Plus `die.mesh` (relative path to the OBJ) and `die.face_map` (OBJ polygon
index → entry in `faces`; coplanar polygons map to the same face) as extra
fields.

A perfectly legal custom mesh may still be a terrible die — a very flat one
lands on two faces and nothing else — and the geometry alone cannot say how
unfair it is. So mesh support also needs a **fairness preview**: roll the die
1,000 times headless on import and show the face histogram, informational
only. That preview is part of the same future work; v1 has no dice whose
fairness is in question.

Why this is not in v1: every catalogue solid is a fair die whose behaviour is
known and tested, and the physics, the atlas layout and the statistics all
lean on that. Arbitrary hulls bring convexity checks, degenerate geometry,
unfair dice and a fairness UI — a whole feature, not a field.

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
6. On success: move the folder atomically to `dicesets/<set.id>/`. If a set
   with that id exists, ask to replace (versions are compared).
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

- TOML syntax error, unknown `format`, missing required fields, a field of the
  wrong kind
- Bad slug, duplicate die id, duplicate table id
- Unknown shape (including `mesh`, which v1 does not implement); `faces` length
  ≠ shape face count; a face value outside −9999..9999
- A `read`, `sound` or `light` naming something the app does not have
- Referenced file missing, outside the folder, absolute, wrong extension, over
  size; the package's textures over 24 MiB together
- Texture over the dimension limit, or not a picture of the kind its name
  claims
- Any numeric physics value non-finite
- `homepage` that is not `https`

Warnings (set installs, user sees them):

- Physics value clamped, table tiling clamped
- A standard die id missing (e.g. no `d12`) — notation will fall back
- Label longer than 4 chars truncated
- Unknown key, ignored
- A texture that does not divide into square atlas cells

Every error is reported, not only the first: an author fixing a set wants the
whole list, and the screen that shows a failed install has room for it
(option 6b).

The whole check runs over an abstraction of the package's files rather than
over a folder, so the same code validates a temporary extraction during an
install, a zip, the app's own assets and a test's memory. A validator that
could only read one of those would only ever be tested on one of those.

**Dimensions before decoding.** A texture's width and height are read out of
its PNG `IHDR` or WebP header, in plain Kotlin, before any decoder sees the
bytes — that is what makes refusing a 30,000-pixel image safe, since the
decoder is the part with the attack surface. The same read is what rejects a
truncated download or a `.png` that is not one. The full decode happens later,
on the IO dispatcher at load time, inside these same caps; a file that
survives the header check but cannot actually be decoded is reported then, and
the die falls back to its label.

The parser is [tomlj](https://github.com/tomlj/tomlj), a TOML 1.0 reader,
chosen over a hand-written one because parsing TOML is exactly the sort of
thing that should not be hand-rolled (`.claude/CLAUDE.md`) and over the
alternatives because it reports the line and column of every key it read —
without which the report above could not carry `file:line` at all. It is used
through its document tree only: every field is asked for by name and checked by
hand into a plain data class. No reflection, no polymorphic deserialization, no
default-on-error, and no deserializer ever sees a downloaded file. Unknown keys
are ignored with a warning to allow future extensions.

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

- Start from `examples/` in this repository: a complete, commented dice set
  using every catalogue shape, with blank atlases to draw over. Copy the
  folder and edit it. (The built-in set cannot be exported from the app —
  `examples/` is what it would have given you, kept where it can be reviewed
  and versioned.)
- Keep textures at 1024×1024 for a d20; nobody will see more on a phone.
- Use `labels` for symbol dice (a d6 with a skull on the 1) so the set works
  even before you draw textures.
