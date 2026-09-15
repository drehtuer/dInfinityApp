# Dice sets

> **Design:** the installed-set list (options 1s and 1t), the set details
> (6a, and 6b for a failed validation), the "My dice" details with its export
> (8c), the disable/remove dialog (5a) and the update flow (9h–9i) are in the
> [clickable design](../design/dInfinity.dc.html) ([design/](../design/)).

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
| --- | --- | --- |
| `format` | yes | Integer. The app refuses formats newer than it knows. |
| `set.id` | yes | Slug of 3–40 characters (`[a-z0-9-]`, starting and ending with a letter or digit). Used as the `setref` in notation and as the folder name, which is why it has a floor. |
| `set.name`, `set.version` | yes | |
| `set.author`, `license`, `description`, `homepage` | no | Displayed only; nothing in the app enforces a licence. `license` is an SPDX identifier by convention — see "What a licence means". `homepage` is shown as text, opened only on explicit tap, `https` only. |
| `defaults.*` | no | Material and physics defaults, all clamped. |
| `die.id` | yes | Slug of 1–40 characters, unique within the set — shorter than a set id, because `d2`, `d4` and `d6` are the ids plain notation resolves. Standard names (`d2`…`d100`, `d10-tens`, `df`) are what typed notation resolves, optionally set-qualified as `brass:2d20`. A die with any other id is rolled by tapping it in the dice picker — the grammar in `docs/dice-notation.md` has no unambiguous way to write `skull-d6kh1`, since a slug and a modifier are made of the same characters. |
| `die.shape` | yes | A name from the shape catalogue below. v1 has no other option. |
| `die.faces` | yes | Integer values, one per face (or vertex). Length must match the shape. Range −9999..9999. Duplicates allowed (d2-as-d6). |
| `die.labels` | no | Strings printed on faces when no texture. Defaults to `faces` as text. Max 4 characters each. |
| `die.read` | no | `face-up` (default) or `vertex-up`. |
| `die.texture` | no | Path to a PNG/WebP atlas, relative, inside the set folder. |
| `die.color`, `number_color`, `roughness`, `metallic`, `size_mm`, `density`, `restitution`, `friction` | no | Per-die overrides of `defaults`. |
| `table.*` | no | Table looks; fields and limits in `docs/tables.md`. |

## What a licence means

`license` is **displayed and nothing else**. The app does not read it, does not
enforce it and does not refuse a package that has none: it is a statement by
the author to whoever installs the package, and the app's job is to carry that
statement faithfully and show it where it will be seen (design `6a`).

It should be an **SPDX identifier** — `CC-BY-4.0`, `MIT`, `GPL-2.0-or-later` —
because the point of the field is that the reader recognises what it says.
Where SPDX has no identifier for what the author means, its `LicenseRef-` form
is the way to say so; the face designer writes
`LicenseRef-All-Rights-Reserved` for a set somebody is keeping.

A package written by the app itself uses the word `unspecified` while nobody
has chosen, which is written down rather than left out — a missing field cannot
be told from one an older version never wrote. Nothing the app *shares* is ever
left at `unspecified`: the face designer's export is shut until a licence has
been picked (`docs/face-designer.md`, "The licence, and why it is a gate").

## Packages the app writes

One package is generated on the device rather than downloaded: **"My dice"**,
id `mine`, built from the drawings in the face designer
(`docs/face-designer.md`). It is a folder in `dicesets/` like any other, and
there is no privileged path for it:

- It is written through the same layout as any package — a `diceset.toml` and
  one `textures/<die-id>.png` per drawn die, at 256 px per atlas cell, with
  cells nobody drew on left out so they stay transparent.
- It goes through **the validator** before it is written to `dicesets/` and
  again before its zip is offered to anybody. A package the app built and could
  not install is a bug caught on this phone rather than an install failure on
  somebody else's.
- It is swapped into place through a staging folder, the way an install is, so
  a reading that catches it halfway sees the package it had before. The staging
  and holding folders begin with a dot and are therefore not packages.
- It is read back off the disk and validated again like everything else, it can
  be switched off and removed like everything else, and `mine:d20` resolves
  like any other `setref`.
- Exporting it produces a **zip of the folder**, handed to another application
  through the share sheet — the same file an install accepts from a URL or the
  file picker, so a set drawn on one phone installs on the next one down the
  ordinary path.

## Shape catalogue

The catalogue is **closed in v1**: these eight shapes and nothing else. Face
count, face order and texture atlas layout are defined by the app (documented
in `dicesets/format/shapes/` with reference images).

| Name | Faces | Typical use |
| --- | --- | --- |
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

**Up is `+z`**, in the tray, in the physics and in the renderer — one
right-handed coordinate system shared by all three, so nothing has to be
turned over on the way between them. "Up" on a *face* is that up flattened
onto the face: the part of `+z` that lies in the face's plane. A face pointing
straight up or straight down has no such part, and those two are turned by
`+y` instead. The face's own circle fills the cell, so a triangle and a
pentagon both touch its edges and a strip of cells is drawn at one size.

### The d4

The one shape where a cell and a readable position are different things. A
tetrahedron is read from the corner pointing up, so its four **numbers belong
to corners**, while its four **cells are painted on faces**.

Cell *i* is the face **opposite** corner *i* — which is to say, the triangle
whose three corners are the three that are *not* *i*.

A number is drawn at the corner it belongs to, on **every face that meets that
corner**. So cell *i* carries three numbers, not one: the values of the three
corners other than *i*, each at its own corner of the triangle. That is what a
moulded d4 does, and it is what makes the die readable — when a corner points
up, all three faces you can see carry that corner's number at their apex.

It also means the two faces sharing an edge agree along it: both draw the same
value at each end of that edge, because the value belongs to the corner rather
than to either face. An author who draws them differently has drawn a die that
reads as two different numbers depending on which way you look at it.

**Face order.** Faces are numbered from the top of the shape's reference
orientation downwards, and anticlockwise around each ring starting from the
`+x` side; face 0 is the one that is up when the die has not been turned. That
is a choice rather than a law, but it is a fixed one — it is the order `faces`
is read in and the order the atlas fills its cells, so changing it would
silently repaint every die of every set ever published. `simulation/api` owns
it, and the renderer and the physics hull are built from the same arithmetic
rather than from a model somebody exported.

### Size

`size_mm` is **how wide the die is**: the diameter of the sphere its corners
sit on, whatever solid it is. A 16 mm d6, a 16 mm d12 and a 16 mm d20 are all
16 mm across at their widest. Half of it is the bounding radius the table's
capacity rule sums (`docs/tables.md`).

It is deliberately not the dice maker's *nominal* size — the edge length for a
polyhedron — even though that is what a manufacturer quotes. A set author
writing `size_mm = 16` means "a 16 mm die", and should not have to know that a
dodecahedron's edge is 0.36 of its width to get one. Read as an edge length,
`size_mm = 16` makes a d12 that is 45 mm across, and dice that size under
ordinary gravity fall slowly enough to look weightless.

One consequence worth knowing: a d4 and a d20 of the same `size_mm` have the
same bounding sphere, so the d4 is the smaller solid inside it. That is how a
real set looks — the dice are sized to sit together in a hand, not to enclose
the same volume.

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

### Labels a die has no artwork for

A die with no `texture` at all has its `labels` printed instead, in
`number_color` on the body colour, in the same atlas grid an image would have
filled. Three rules decide what a face ends up carrying:

| The label | What is printed | Why |
| --- | --- | --- |
| something the built-in font can draw — digits, `+`, `−`, `×`, `%`, `.` | the label | it is what the author wrote |
| something it cannot, such as `💀` | the face's **value** | a row of blanks would make the die unreadable, and a box would be a lie about what the author wrote. The value is the one thing about a face the app can always write down, and it is what the player is about to read off it anyway |
| empty | nothing | a blank side is a face an author asked for, and half a Fudge die is exactly that |

**A number is underlined when it could be read as another number on the same
die.** Turn the label about; if what comes out is a *different* label this die
also carries, both get a bar. That is why a d20's `6` and `9` are barred and a
d6's `6` is not — a d6 has no `9` for its `6` to be mistaken for, which is
exactly what a moulded d6 does. An `8` turns into itself and a `2` turns into
nothing readable, so neither is ever barred.

The font is not the set's to choose. It is one built-in face, cut from Archivo
(`docs/assets/README.md`), and a set that wants its own lettering draws it and
ships it as a texture — which is what a texture is for.

## Installing from a URL or file

Users paste a URL. Accepted sources:

| Source | Example | How it is fetched |
| --- | --- | --- |
| GitHub | `https://github.com/ada/brass-and-bone`<br>`…/tree/v1.2.0`<br>`…/tree/main/sets/skulls` | Ref resolved to a commit SHA via the API, tarball for that SHA |
| GitLab (gitlab.com or self-hosted) | `https://gitlab.com/ada/brass-and-bone/-/tree/main` | Same, via the GitLab API |
| Codeberg / Gitea / Forgejo | `https://codeberg.org/ada/brass-and-bone` | Same, via the Gitea API |
| Any archive | `https://example.org/dice/brass.zip`<br>`https://example.org/dice/brass.tar.gz` | Direct download; the SHA-256 of the archive is recorded in place of a commit SHA |
| Local file | picked via the system file picker | `.zip`, `.tar.gz` or a folder |

The forge integrations exist for convenience (browse to a repo, paste the
URL, get updates). They are not what makes an install safe — the validator
is, and it runs identically for every source; an unknown host is treated as a
plain archive and goes through the same extraction and the same checks.

Plain `http://` is refused before a request is made. Redirects are followed
by hand rather than by the HTTP client, so that a redirect from `https` to
`http` — the oldest downgrade there is — is refused instead of taken; at most
5 hops. Downloads are capped at 64 MiB by the bytes that **arrive**, not by
the `Content-Length` the server claims, and time out after 60 s.

Install flow:

1. Recognise the source from the URL. Unknown hosts are treated as "any
   archive" and must end in `.zip`, `.tar.gz` or `.tgz`.
2. Fetch the archive and record its identity (commit SHA or archive
   SHA-256) so updates are diffable and the install is reproducible.
3. Stream-extract into a **temporary** folder with these checks:
   - Reject absolute paths and `..`, in either slash direction — an archive
     written on Windows carries `..\..\`, and a check that only knew about
     `/` would wave it through to a filesystem that knows both.
   - Reject a tar entry that declares itself a symbolic link, a hard link or a
     device: tar puts that in the entry header, where a streaming reader sees
     it. A zip cannot be checked the same way — its unix modes live in the
     central directory at the *end* of the file, which a streaming reader never
     reads — and does not need to be: the extractor has no code path that
     creates a link, so a zip "symlink" extracts as an ordinary little file
     whose contents are a path. The loader then refuses to read anything whose
     canonical path leaves the folder anyway.
   - Reject total uncompressed size > 64 MiB or > 500 entries, counted **as the
     archive is read**. A thing that expands to a terabyte has to be refused at
     the megabyte where that becomes obvious.
   - Only extract files whose extensions are on the allowlist — for a dice set,
     `toml, png, webp, obj, md, txt`. Anything else is skipped rather than
     refused: a repository is entitled to contain a `.gitignore`. The
     allowlist, the size cap and the entry count are the *caller's* to name, so
     that a saved-roll collection can come down this same path under bounds of
     its own (below).
4. Locate `diceset.toml` (at the root or at the given subfolder). Which file
   marks the package is the caller's to name as well; everything before this
   step is identical whatever is being unpacked.
5. Run the validator (below). On failure: delete the temp folder, show the
   report.
6. On success: move the folder atomically to `dicesets/<set.id>/`. If a set
   with that id exists, ask to replace (versions are compared). Replacing
   moves the old folder aside to `<set.id>.replacing` first and deletes it
   only once the new one is in place, so a failure halfway leaves the *old*
   set installed rather than neither. Every step of that is checked: if the
   old folder cannot be moved aside the install stops before writing
   anything, and if it cannot be put back after a failure the message says so
   rather than claiming the set is untouched. A `.replacing` folder left by an
   interrupted install is cleared by the next one.
7. Write `.meta.json` with the source URL, SHA, timestamp and validator
   output.

The app never runs anything from the repository. No scripts, no build steps.

Updates: **"Check for updates" re-resolves the ref** and compares the commit
with the one recorded at install. A set whose forge has moved on is badged on
the list, and updating it is a re-install from the source the install recorded
— through the same validator, over the top of the folder that is there. An
update that is refused costs nothing, because a package that fails validation
leaves the one already installed alone.

**A plain archive is checked too, by asking its server about the file.** It has
no commits to tell apart, so the install records what the server said — its
`ETag`, and the `Last-Modified` beside it — and the check is a `HEAD` request
comparing the two. A `HEAD`, because the whole point is to avoid pulling sixty
megabytes to find out that nothing changed.

Neither header is parsed. An `ETag` is an opaque string by definition, and the
date beside it is a header to compare rather than a time to reason about:
deciding that one date is *later* than another would read a meaning into a
server's clock that nobody promised. The `ETag` is preferred where both ends
have one, because it is the server's own statement about the bytes, where a
date changes when a file is rebuilt without changing.

**Like is compared with like, and nothing is said otherwise.** A set installed
with an `ETag`, against a server that has since stopped sending one, is
*unanswerable* rather than outdated — badging it would send somebody to
re-download a set that has not changed. The same is true of a set installed
before any of this was recorded: its note has a source and nothing to compare,
and re-installing it is the only honest way to give it one.

Which of the two questions a set gets is decided by **what its install
recorded**, not by reading the link again: a set with a commit against it came
from a forge, and anything else is an archive. Re-parsing the URL would be a
second place for "is this a forge" to be answered, and two answers to that is
one too many.

A set installed from a file has no source at all and is never asked.

**A download says how far it has got and can be stopped** (design `9i`). The
bar is drawn from bytes that have actually arrived; the `Content-Length` beside
them is what the *server* said and is only ever used to decide how full to draw
it, so a server that sends none gets an indeterminate bar rather than a wrong
one, and one that lies cannot push it past full. The cap is applied to what
arrives, never to the claim.

The bar is there only while something is coming down the wire. An install from
a file on the phone has nothing to show, and neither has the validation after a
download — a bar that reached full and sat there would say the app had hung at
the moment it was working hardest.

**Cancel stops the download and nothing else.** Once the archive is on disk the
install is a validator over a folder and finishes in a moment, and a
half-installed package is exactly what the install order above exists to make
impossible — so there is nothing left to interrupt that would be safe to. A
stopped download throws its half-written file away where it was being written,
and the screen says nothing about it: the person who pressed Cancel knows what
happened, and answering them with an error message is arguing with them.

The button is not drawn at all when nothing could be asked, and what a check
*found* is said in a line rather than only on the rows: a check that found
everything current and a check that could not reach anything look identical on
a list where nothing is badged either way. A forge that cannot be reached is
counted as unreachable rather than quietly read as up to date.

The button is still not drawn when nothing could be asked, and a set with
nothing to compare is skipped rather than asked a question whose only possible
answer is "no idea".

Resolving a ref means asking the forge which commit it is at now — GitHub and
Gitea call that hash `sha`, GitLab calls it `id`, and Gitea answers with a list
because its endpoint is "the log from here". The answer is recorded in
`.meta.json` as `commit`, beside the archive's own SHA-256.

Both are needed and neither replaces the other. The checksum is what makes an
install *reproducible*: it is the bytes that actually arrived. It cannot answer
"is there something newer", because a tarball built twice from the same commit
need not be byte-identical — the commit answers that.

A forge that cannot be asked, or that answers with something which is not a
commit hash, **does not stop an install**. What comes back is checked for being
forty or sixty-four hex digits and nothing else in the reply is read; if it is
not one, the set still installs and only the update check is poorer for it. A
forge is a stranger like any other host (`SECURITY.md`).

**The same fetch and extraction path is used for saved-roll collections**
(`docs/dice-notation.md`), which are a single JSON file rather than a folder.
A repository of them is recognised here, by the same table of forges above, and
fetched and unpacked by the same code under the same refusals. Two things are
named differently and nothing else is:

| Named by the caller | Dice set | Saved-roll collection |
| --- | --- | --- |
| What marks it | `diceset.toml`, anywhere in the archive or at the named subfolder | one `*.dinfinity.json`, at the repository root |
| What may be written | `toml, png, webp, obj, md, txt` | `json` |
| What it may expand to | 64 MiB | 1 MiB, the size a collection may itself be |
| What is kept afterwards | the folder, under `dicesets/<id>/` | nothing; the rolls go into the database and the unpacked folder is deleted |

The marker file is the same idea in both: a package says what it is by a name
everybody agrees on, at a place everybody can find. A collection's is at the
root and there may be only one, because a link names a repository rather than a
file — an import that quietly chose between two would be choosing for
somebody.

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
- **What is installed is read off the disk and validated again every time, not
  remembered from the install.** `InstalledSets` answers with a package that is
  either ready or broken-with-its-report, and the two cases exist precisely
  because passing the validator once does not make a folder valid for ever. A
  broken package **keeps its folder**: an update is the way out of that state
  and an update needs somewhere to update from (design `6b`).
- **One installed set can be made the one plain notation reaches for first**
  (design `6a`). `d20` with no set in front of it then means that set's d20,
  falling back to the bundled set per die for anything it does not define
  (`docs/dice-notation.md`). A set that is switched off or will not load is not
  offered the job, and one that stops being usable after being given it falls
  back without the setting being rewritten.
- **What is installed is what a formula resolves against.** `SetLibrary` builds
  the catalogue every time it reads the `dicesets/` folder, from the bundled set
  plus every installed package that is *on and still validates* — the same test
  a row's status shows. Reading the folder and deciding what a `d20` means are
  the same facts, and keeping them apart is how a set comes to be listed as
  installed and still not roll.
- The folder is read once as the process starts, not when the dice-set screen
  is first opened. The roll screen is home, so the first formula can be typed a
  moment after launch; a set that only became rollable once somebody visited a
  list would be a set that worked for the people who happened to look.
- **A file somebody picked is copied bounded, into the app's own cache.** A
  content URI is a handle to something another application controls: its size
  is not knowable in advance, the provider may report one figure and hand over
  another, and it may stream for ever. So the copy stops one byte past
  `InstallLimits.MAX_DOWNLOAD_BYTES` — the same cap a download gets, because
  the limit is about what the extractor is willing to open rather than about
  where the bytes came from. The copy is deleted however the install ends.
- **A rejection lists every error, not the first.** An author fixing a set
  wants the whole list, and a report that stopped at the first problem would be
  one round trip per mistake (design `1t`).
- **The report is shown where the dice would be.** A set's details screen
  answers one question — what is in this set — and for a package that no longer
  validates the answer is "nothing yet, and here is why", so the report takes
  the dice grid's place rather than appearing beside it (design `6b`). Each
  line carries `file:line`, because the person who can fix it is the author.
- **A link only when there is somewhere to go.** A set installed from a file,
  or a folder the app never installed, records a source that is a name rather
  than a URL; making that tappable would promise something it cannot do. Only
  `https` is ever handed to the system, and the check happens where the intent
  is started — the string came off a file on disk.
- **A row says which of the two ways a set can be unusable it is in.** Switched
  off and will-not-load are not the same thing and the remedies are opposite —
  one is a tap, the other is an update — so a list that showed only
  "unavailable" would send the player to the wrong one (design `5a`).
- **Whether a set is switched on is the database's to say, not the folder's.**
  `installed_set` holds one row per package the player has had an opinion
  about, and a package with no row is enabled — which is what a set does the
  moment it installs (`docs/statistics.md`, "Storage"). Disabling keeps the
  folder, the `.meta.json` and every statistic recorded against the set's dice,
  which is what makes it a reversible decision and why the screen offers both
  it and removal (design `5a`).
- The bundled set cannot be switched off. Every fallback resolves against it
  (`docs/dice-notation.md`), and unlike every other set there would be no way
  to install it back.
- A folder whose `diceset.toml` gives an id other than the folder's own name is
  refused. Notation resolves a set by folder, so the dice it handed out would
  come from a set the player never named — and the app cannot have produced it,
  because an install names the folder after the id the validator accepted.
- Physics/rendering never trust set data directly: every value passes through
  the clamps again at load time, and the convex hull is recomputed on load.

## Authoring tips

- Start from `examples/` in this repository: a complete, commented dice set
  using every catalogue shape, with blank atlases to draw over. Copy the
  folder and edit it. (The built-in set cannot be exported from the app —
  `examples/` is what it would have given you, kept where it can be reviewed
  and versioned.)
- Or draw one. The face designer's export is a complete package with its
  atlases already in the right grid, which is a working starting point for a
  set meant to be finished on a computer (`docs/face-designer.md`).
- Keep textures at 1024×1024 for a d20; nobody will see more on a phone.
- Use `labels` for symbol dice (a d6 with a skull on the 1) so the set works
  even before you draw textures.
