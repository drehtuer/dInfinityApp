# Face designer

> **Design:** the designer is options 1v (d6 with a skull on the 1), 4c
> (colour picker) and 8d (d20, triangular face mask) of the
> [clickable design](../design/dInfinity.dc.html) ([design/](../design/)).

The face designer lets a user draw the faces of a die with a finger and roll
the result immediately. Its output is a normal dice set (see
`docs/dice-sets.md`), so a designed die can be exported, shared on any git forge or web server and
installed by other users like any other set.

## Flow

1. **Pick a base die.** Any catalogue shape or any installed die. The designer
   copies its `faces` and `labels`, so face *values* are inherited; the user
   is only drawing what the face looks like.
2. **Draw.** The screen shows one face at a time as a large square canvas
   with the face's outline (triangle, square, pentagon, kite for the d10…)
   masked in. Swipe left/right or use the strip at the bottom to move between
   faces. The current face value is shown faintly as a guide and can be
   hidden.

   **A d4 is the exception and needs three guides, not one.** Its numbers
   belong to corners rather than to faces, so each of its four triangles
   carries the values of its three corners, one at each corner of the canvas
   (`docs/dice-sets.md`, "The d4"). The guide shows all three in place, and
   the two triangles sharing an edge have to agree along it — a die drawn
   otherwise reads as a different number depending on which way it is looked
   at, which the designer should make hard to do by accident rather than
   merely warn about afterwards.
3. **Preview.** A 3D preview of the die with the drawn atlas applied, rotatable
   by drag, updated live.
4. **Roll it.** The Roll button throws the die into the tray to see how it
   looks in motion (`docs/physics-and-rendering.md`, "Starting a roll").
5. **Save.** The die is added to the user's personal set ("My dice", id
   `mine`), or to a new set the user names. The set folder is written with a
   generated `diceset.toml` and one atlas PNG per die, and then run through
   the standard validator like any import.

## What is built

The canvas, and the taking-back. One face at a time with its outline masked
in, the guide under it, three pen widths and an eraser, undo/redo and clear per
face, the twelve presets, and the strip that moves between faces. Strokes are
vectors in fractions of the canvas, so they survive a rotation and can be
re-rendered at export resolution.

**The d4 rule is derived, not checked.** A cell's three numbers are read from
the three corners that cell meets, so two cells sharing an edge draw the same
value at each end of it because they are reading the same corner. There is no
second copy to disagree with and so nothing to warn about.

**Changing the base die starts again.** A different die has different faces, a
different number of them, and different values under the guide, so nothing
carries over — which is why a drawing with anything on it is asked about before
the die changes, and a blank one simply changes. The pen, its colour and
whether the guide is showing all stay put: those are how somebody is working
rather than what they are working on.

Still to come, in `docs/TODO.md` 4.6: the fill bucket and stamp, drafts on
disk, "Roll it", and the export.

## Drawing tools

Deliberately small:

- Pen with three widths, eraser, fill bucket
- Colour palette (the set's default colours + 12 presets + a picker)
- Undo/redo (per face, unlimited within the session)
- Stamp: place a digit/letter/symbol from the built-in font, scalable and
  rotatable, so people who cannot draw a legible "8" still get an "8"
- Copy face → paste onto another face, with optional rotate/mirror (for
  making all faces share a border, for example)
- "Fill all faces with numbers" one-tap starting point

Strokes are recorded as vector paths in a draft file so that the canvas can be
re-rendered at export resolution and so drafts survive process death.

## Export details

- Atlas resolution: 256 px per face cell; a d20 atlas is therefore
  1280×1024 (5×4 cells). Well within the set limits.
- Background of each cell is transparent; the die colour and material come
  from the set defaults, so the same drawing works on a black or a white die.
- Strokes are rasterised with anti-aliasing at export time from the vector
  draft.
- The generated `diceset.toml` for a personal set marks
  `author = "<device user name>"` and `license = "unspecified"`; the export
  screen asks the user to pick a license before sharing.
- "Share" produces a zip of the set folder, which can be uploaded to a git
  repository as-is.

## Quick mode

From the roll screen, long-pressing a die offers "Doodle this die": the
designer opens on that die with its existing texture (if any) as the starting
layer. Saving creates a variant in "My dice" with the same id suffixed
`-doodle` and switches the current roll to use it. This is the "draw a skull
on the 1 in ten seconds" path.

## Constraints

- Drafts are limited to 50 per device and 200 strokes per face to keep
  storage and export time bounded; the UI warns before the limit.
- Everything is on-device; nothing leaves the phone unless the user shares
  the zip.
