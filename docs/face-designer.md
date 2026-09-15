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
   looks in motion (`docs/physics-and-rendering.md`, "Starting a roll"). It
   opens the tray with the die in the formula field and **does not throw it**:
   the throw is the player's to make, which is the same answer every other way
   into the tray gives.
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

**The body scrolls and the strip stays.** A square canvas and three rows of
controls do not fit above the fold on a short phone, so everything from the
base-die chooser down to the palette scrolls, and the face strip is pinned to
the bottom — which face is in front of the player is where the screen is
steered from. The tool, clipboard and colour rows **wrap** rather than scroll
sideways: a tool hidden off the edge of a row is a tool nobody finds. Only the
face strip scrolls sideways, because twenty faces have to go somewhere.

The toolbar the design asks for is three-quarters there: the **fill bucket**,
**copy face → paste with a turn and a mirror**, and a **colour picker past the
twelve presets** (`4c`). Each of them is arithmetic over the stored vectors and
lives in `designer/` where a plain test can reach it; what is left in the
screen is a path and a mask.

**The d4 rule is derived, not checked.** A cell's three numbers are read from
the three corners that cell meets, so two cells sharing an edge draw the same
value at each end of it because they are reading the same corner. There is no
second copy to disagree with and so nothing to warn about.

**Changing the base die changes the drawing, and keeps both.** A different die
has different faces, a different number of them, and different values under the
guide, so nothing carries over from one canvas to the other — but nothing is
lost either. Each die has a draft of its own, written down as its canvas is
left and read back when it is opened, so a row of dice is a row of drawings
rather than one canvas the next tap overwrites.

It used to ask before changing die, because changing die threw the drawing
away. A dialog that warns about a loss that cannot happen is worse than no
dialog, so it is gone.

The pen, its colour and whether the guide is showing all stay put across the
change: those are how somebody is working rather than what they are working
on.

**Roll it** is there: the button hands the tray the die being drawn and follows
the chooser, so it throws the die in front of the player rather than the one
the screen opened on. Two things about it are worth knowing. It throws the
**die, not the drawing** — the strokes are not on it, because nothing puts an
atlas on a die yet (`docs/TODO.md`, Step 3) — so what it answers today is how
the solid looks in motion, which is the preview this designer has instead of a
3D one. And it is **absent rather than dead** for a die plain notation cannot
name: a set's own `skull-d6` has no spelling a formula could carry
(`docs/architecture.md`, decision 31).

Which set the formula names is decided by what would resolve, not by where the
die came from — the chooser lists dice by id across every installed set and so
has no answer to "which set is this one". A bare `1d20` when the set a plain
`d20` already means has one, and `brass:1d18` when it does not and `brass`
does.

Still to come, in `docs/TODO.md` 4.6: the stamp and "fill all faces with
numbers" — both of which place a glyph, and so both wait on the built-in SDF
font (Step 3) — and the export.

## Drawing tools

Deliberately small:

- Pen with three widths, eraser, fill bucket
- Colour palette (the set's default colours + 12 presets + a picker)
- Undo/redo (per face, unlimited within the session)
- Copy face → paste onto another face, with optional turn/mirror (for making
  all faces share a border, for example)
- Stamp: place a digit/letter/symbol from the built-in font, scalable and
  rotatable, so people who cannot draw a legible "8" still get an "8" — not
  built yet, and neither is the "fill all faces with numbers" one-tap starting
  point it shares a font with

### The fill bucket

**A fill is a shape added to the drawing, not a flood of pixels.** There are no
pixels here to flood: a face is a list of vectors in fractions of the canvas,
so the bucket's whole job is to decide *which region* and add it as another
mark. It picks the region a raster flood fill would have looked as though it
picked — **the smallest closed stroke the tap landed inside**, and **the face
itself** when it landed on bare paper. Shapes nest, and a tap in the eye of a
drawn skull means the eye rather than the skull.

A stroke counts as closed when its two ends come back within eight hundredths
of the canvas of each other; a finger never lands on the pixel it started from,
and demanding that it did would make the bucket useless. An eraser stroke is
not a boundary — it takes ink away rather than drawing a line to fill against —
and neither is an existing fill, because filling inside the paper is what
filling the face already does.

The whole-face region is stored as **the canvas square** rather than a copy of
the cell's outline. Every renderer already clips the drawing to that outline —
the screen and the exporter both do — so a fill of the square *is* a fill of
the face, and a fill carrying its own copy of the polygon could come to
disagree with the mask.

**Fills sink under the ink.** The marks on a face are kept with every fill in
front, oldest first, and every stroke behind them, so a later fill covers an
earlier fill and never the drawing. A bucket colours the paper, not the line:
a fill that landed on top would hide the drawing it was aimed at, and the way
to cover ink is the eraser. A fill is one mark, so it is one press of undo, it
counts against the two-hundred limit like a stroke, and it round-trips through
the draft file with its region and its colour.

### Copy and paste

Copying takes what is on the face — the drawing, not its undo stack, which
belongs to the face it was made on. Pasting **merges**: the copy lands on top
of whatever is already there, which is what "make every face share this
border" needs. A face that should be replaced is cleared first, two presses,
both undoable — whereas a paste that replaced would take away work nobody
asked it to. A paste is **one** step however many marks it carried, and a
paste that would carry the face past the limit is refused whole, because half
of what was copied is not what was copied.

**The turn is the cell's own**: a third of a turn on a triangle, a quarter on a
square, a fifth on a pentagon, and quarters on the d2's disc, which has every
turn there is. A turn that did not carry the cell onto itself would carry the
drawing off the face and under the mask. A kite — the d10's and the d18's cells
— has no such turn at all, so those dice are offered the mirror and the turn is
disabled rather than removed: a row whose buttons come and go as the base die
changes is a row nobody learns.

**There is one mirror, the vertical one**, left for right. It is the only axis
every cell outline here shares; a triangle standing on its base does not
survive being flipped top for bottom. The mirror goes on first and the turn
after it, which is the order that makes "mirror, then turn twice" mean what it
says.

The clipboard outlives the face and the die. Marks are fractions of the canvas,
so a border copied off a d6 lands on a d20's triangle as readily as on another
square, and a dot the turn carries off the canvas is clipped by the mask like
any other ink rather than squashed back inside — squashing would change the
shape that was copied.

### A colour beyond the twelve

The twelve presets stay the fast path: twelve is what a finger can hit without
a dialog, and the picker sits after them, which is where the design puts it
(`4c`). It opens on the colour already in the pen and offers **hue, depth and
brightness** — three numbers somebody can move one at a time and mean
something by, which red-green-blue is not. The swatch and the hex are shown
next to the row, so what is in the pen can be read off rather than guessed at,
and the chosen colour is stored per stroke, so it round-trips through the draft
file like any other ink.

**Ink is always opaque.** Alpha is forced rather than offered: a
half-transparent stroke is a stroke whose colour depends on what is behind it,
and what is behind it is the die's own material, which the set decides and the
designer never sees.

**There is no contrast rule on ink, and that is deliberate.**
`core/model/AccentColor` holds every accent to 3:1 against both grounds because
those grounds are the app's own — it knows what the surface behind a button is.
A die face is not the app's ground: the cell is transparent in the atlas and
the colour under it comes from the dice set, so a ratio computed against the
designer's white paper would be a promise about a surface that is not there.
The rule that applies here is the other one — the drawing is shown at the size
it will be drawn and the judgement is the player's, which is also why white is
one of the twelve even though it is invisible on the canvas and perfect on a
black die.

### The draft file

Marks are recorded as vector paths in a draft file so that the canvas can be
re-rendered at export resolution and so drafts survive process death.

**One file per die, under the app's own files.** The file holds the marks and
not the undo stack — undo is unlimited *within a session*, and a history
restored from disk would rewind a drawing past the point somebody opened it.
It is written after every stroke, undo and clear rather than when somebody
remembers to save, because the moment a draft is most likely to be lost is the
one where nobody is thinking about it; the write is launched off the drawing
thread, so a line never waits on a disk.

The file names its die by **id** rather than carrying a copy of it: a draft is
a drawing *on* a die, and the die belongs to a dice set that can be updated
under it. A draft whose die is not installed is not offered, and its file is
kept rather than deleted — re-installing the package brings the drawing back,
which is the rule the default set and the default table already follow. A cell
the die no longer has is dropped and the rest of the drawing is kept; losing
six faces over one is not a trade worth making.

It is read through the JSON DOM with every field taken by hand, the way
`core/collection` reads a collection. A draft is the app's own file rather than
a stranger's, so the reason is the other one: a deserializer's idea of the file
is the class shape of the day, and a drawing has to survive the class changing
under it. Anything that does not read is simply not a draft, and the canvas
opens blank.

**The format number was not bumped for fills.** A fill is a new kind of entry
in the list of marks that was already there, told apart by a field a stroke
never carries, so every draft written before fills existed reads exactly as it
did and a build that predates them drops a fill it cannot draw rather than
losing the drawing around it. Bumping the number would blank every drawing on
the device to spare an older build one shape, which is not a trade.

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

- Drafts are limited to 50 per device and 200 marks per face — strokes and
  fills alike — to keep storage and export time bounded.
- The mark limit **warns and then refuses**: the face stops taking marks and
  says so twenty strokes before it does, because a canvas that silently stops
  drawing reads as a broken screen. A paste that would not fit is refused
  whole rather than in part.
- The draft limit **makes room**: drawing on a fifty-first die drops the draft
  nobody has touched for longest. Refusing it would be a dead end — there is
  no screen yet on which to delete one ("My dice", `docs/TODO.md` 4.6) — and a
  cap that cannot be reached is not a cap. It is the same answer the history's
  fifty thousand rows already take. The draft being worked on is never the one
  dropped, whatever the filesystem's clock says.
- A drawing with nothing on it is not a draft and is not kept, so opening the
  designer and leaving it cannot push a real drawing over the limit.
- Everything is on-device; nothing leaves the phone unless the user shares
  the zip.
