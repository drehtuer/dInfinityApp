# Tables

> **Design:** the table picker is option 1u of the [clickable design](../design/dInfinity.dc.html)
> ([design/](../design/)); 9j adds an installed set's table and 8a pins one to
> a saved-roll group.

The **table** is the dice tray: the box the dice tumble in. Two things about
it are fixed and one is exchangeable.

- **Fixed: the mesh.** A rectangular box with a flat floor, four vertical
  walls and rounded inner corners. Nobody can ship a table with a hole in the
  floor or a ramp in the corner.
- **Fixed: the size.** The table is the phone's screen. Its aspect ratio is
  the screen's, its walls sit at the screen edges, and the camera frames it
  edge to edge. It does not grow to fit a big roll; the dice shrink, and past
  a limit the roll is refused.
- **Exchangeable: the look.** Floor and wall textures, colours, material,
  sound profile and lighting preset — the same idea as choosing a wallpaper.
  Tables ship inside the same package format as dice sets and can be
  downloaded from the same sources.

## Geometry

Simulation units are millimetres.

| Property | Value |
| --- | --- |
| Long side | 240 mm, always (a real dice tray, regardless of phone size) |
| Short side | 240 mm × screen aspect ratio, clamped to 0.40–0.75 (Pixel 10a: 20:9 → ~108 mm) |
| Wall height | 60 mm as drawn; the collision box is closed to the ceiling at 200 mm |
| Inner corner radius | 12 mm (dice do not wedge into sharp corners) |
| Floor friction / restitution | from the table look, clamped (see below) |

The two heights are not a contradiction. Sixty millimetres is the rim the
renderer draws — the wall you can see. The *collision* walls run the full two
hundred up to the ceiling, because "dice cannot leave the table no matter how
hard the phone is shaken" is not true of a box with a lid two hundred
millimetres up and sides only sixty: a die thrown hard leaves through the gap
and never comes back. **The rounded corners run the full height too.** They
were once only as tall as the rim, on the reasoning that the rim is as far up
as a die can wedge into anything — which is true, and beside the point: a post
that stops at sixty has a *top*, and four horizontal ledges sixty millimetres
up inside the tray are four places a die can come to rest in mid-air. It took
a phone to see it, and what it looked like was a die floating with its shadow
on the floor beneath it.

Orientation follows the phone: portrait phone → portrait table. **The roll
screen then holds whatever orientation it opened in**, because the tray *is*
the screen and turning the phone rebuilds the table — a different shape, a
different capacity, the camera reframed. That is the right answer for somebody
who meant to turn it and an unwelcome surprise for somebody who is shaking it,
which is most of the time on that screen. Locked to the orientation it was
opened in rather than to portrait: a player who opened the app in landscape
meant it. The rest of the app turns as it likes.

The physical size of the phone's screen is deliberately *not* used to scale
the table. A 6.3" and a 6.9" phone get the same table; only the aspect ratio
differs. This keeps rolls comparable and keeps the capacity numbers stable
across devices.

## Capacity rule

Before any physics body is created, the roll is checked against the table:

```text
floorArea     = longSide × shortSide
footprint(d)  = π × r(d)²           r = bounding-sphere radius of die d at scale 1
required      = Σ footprint(d) over all dice in the roll
                (including the dice a first explosion could add)

scale = min(1, sqrt(0.30 × floorArea / required))

if scale < 0.40   → roll refused
if diceCount > 100 → roll refused (engine hard cap, independent of scale)
else               → all dice are spawned at `scale`
```

`r(d)` does not come from the shape at all. A die's `size_mm` is **how wide it
is** — the diameter of the sphere its corners sit on — so its bounding radius
is `size_mm / 2` whatever solid is inside it. A 16 mm d6, a 16 mm d12 and a
16 mm d20 are all 16 mm across at their widest, which is how a set of dice
looks in a hand.

`size_mm` used to be read as a dice maker's *nominal* size — the edge length
for a polyhedron — and each shape had its own ratio from that to its bounding
radius. That convention is defensible and is what a manufacturer quotes, but
it is not what anybody means by "a 16 mm die": a dodecahedron with 16 mm edges
is 45 mm across. Dice that size under ordinary gravity take half again as long
to fall their own length, and the roll reads as weightless — a miniature
filmed at normal speed. See `docs/dice-sets.md`, "Size".

That is: dice may collectively cover at most 30 % of the floor with their
bounding circles, and they may shrink to 40 % of their size to get there. Both numbers are tunable constants and both are covered by the golden
determinism tests.

Worked example on a Pixel 10a table (240 × 108 mm ≈ 259 cm²):

| Roll | Required at scale 1 | Result |
| --- | --- | --- |
| `1d20` (16 mm) | 2.0 cm² | scale 1.0 |
| `8d6` (16 mm) | 16 cm² | scale 1.0 |
| `38d6` | 76 cm² | scale 1.0 — the most that roll full size |
| `60d6` | 121 cm² | scale 0.80 |
| `100d6` | 201 cm² | scale 0.62 — the engine's whole cap |
| `101d6` | — | refused: "101 dice don't fit on the table; up to 100 do" |
| `500d6` | — | refused |

Note what the first column no longer does: **the floor rule stops refusing
anything.** Dice of the right size are small enough that it would take about
240 of them to shrink past the 40 % floor, and the engine stops at 100 long
before that. The rule still does its real job — it shrinks a crowded tray so
the dice have room to tumble — but the refusal a player actually meets is the
body cap. Whether 30 % and 40 % are still the right numbers now that they bite
so much later is a question for Step 5.3, with a device.

The refusal message always says the largest count that *would* fit, and
offers to open the outcome graph instead, which has no such limit.

Why refuse rather than batch or grow the table: a physics engine with
hundreds of convex bodies packed into a small box tunnels, jitters and
explodes. The result would not be a roll of the dice, it would be a bug. The
outcome graph already answers "what does 500d6 look like"; the table answers
"what did *these* dice do", and that only means something when they had room
to do it.

## Table looks

A table look is a `[[table]]` entry in a package's `diceset.toml`
(`docs/dice-sets.md`). A package can contain only tables — a "table pack".

```toml
[[table]]
id = "green-felt"                 # slug, unique within the package
name = "Green felt"
floor_texture = "tables/felt.png" # optional, relative, inside the package
floor_tiling = [3, 6]             # texture repeats across short/long side, default [1, 1]
wall_texture = "tables/oak.png"   # optional
wall_tiling = [8, 1]
floor_color = "#1f5e3a"           # tint; the whole colour when no texture
wall_color = "#5a3a1e"
roughness = 0.9                   # 0..1
metallic = 0.0                    # 0..1
friction = 0.6                    # floor + walls, clamped to 0.2..1.0
restitution = 0.2                 # clamped to 0.0..0.6
sound = "felt"                    # felt | wood | glass | stone | plastic  (built-in impact sound sets)
light = "warm"                    # neutral | warm | cool | dim  (built-in lighting presets)
```

Rules:

- No mesh field. There is nothing to put there.
- Textures: PNG/WebP, max 2048×2048, same byte limits as die textures.
  `floor_tiling` lets a 512×512 felt tile cover the floor without a
  screen-sized image.
- Physics values are clamped at validation and again at load, like dice.
  A table can be a bit slippery or a bit grippy; it cannot be frictionless.
- Sound and light are names from built-in lists so a package cannot ship
  audio files or HDR environment maps (both are large and both are attack
  surface for decoders). More presets can be added to the app over time.
- Unknown keys are ignored with a warning.

### Built-in tables

The bundled package ships `felt-green`, `felt-black`, `oak`, `dark-glass`
and `plain` (a neutral grey that is easy on the eyes and on the battery — it
is what power-saving mode's result screen echoes).

### Your own photo

"Use a photo as table" in settings takes any image from the system picker,
downsizes it to 2048 px on the long side, and writes a `[[table]]` entry
into the user's personal package (`mine`). It is then a normal table and can
be exported with the rest of `mine`.

## Selecting a table

- **Tables are global.** Every look from every installed package is listed in
  every table picker, whatever dice set it shipped with. A dice set never
  brings its own table along or overrides the chosen one: a roll that mixes
  Brass & Bone dice with built-in dice happens on the one selected table,
  like reaching into two bags over the same tray.
- Settings → Table shows all installed looks as thumbnails rendered on the
  actual box mesh, with a "roll a d20 here" preview.
- Saved-roll groups can pin a table ("the Strahd campaign is always played on
  black felt"), and so can an individual saved roll ("Fireball is thrown on
  black felt"). Precedence, most specific first: the saved roll's pin, then
  the active group's pin, then the app default from Settings.
- Power-saving mode ignores the table look entirely; the physics values of
  the *selected* table are still used so the roll is identical to what
  normal mode would produce.
