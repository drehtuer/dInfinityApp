# Design

The UI design of dInfinity, exported from a Claude Design project. It is a
living prototype, not a picture: every screen runs in the
browser with a working notation parser, the table capacity rule and the exact
outcome distribution, all following the specification in `docs/`. Physics is
faked with a random face and a tumble animation.

**▶ [Open `dInfinity.dc.html`](dInfinity.dc.html) in a browser.** These files are
the prototype — there is nothing to sign in to and nothing to install. Open the
file from a clone; a link to it on GitHub shows the source instead of running
it, because GitHub does not serve HTML from a repository.

It does need the internet, though not an account: `support.js` pulls React and
Babel from unpkg (pinned, with subresource integrity) to render
`android-frame.jsx`. Everything else — the styles, the design system, the
prototype's own logic — is in this folder.

## Files

| File | What it is |
| --- | --- |
| `dInfinity.dc.html` | The canvas: an options board of every design turn — layouts, result-sheet densities, picker styles, graph styles, saved-roll tile styles, and one phone per screen. Open this one. |
| `dInfinityPhone.dc.html` | The phone prototype itself, imported by the canvas once per variant with different attributes. Its own tweaks — `screen`, `formula`, `rollState`, `theme`, `firstLaunch`, `editing` — reach any screen or state directly. |
| `android-frame.jsx` | Android (Material 3) device frame: status bar, app bar, gesture nav, keyboard. Starter scaffold; intentionally uses raw values. |
| `Logo.dc.html` | The identity: the `d∞` monogram, its lockups and the app icon. The blue there is the mark's own and is not the interface accent, which the player chooses ([docs/assets/README.md](../docs/assets/README.md)). |
| `support.js` | Claude Design's generated runtime that renders `.dc.html` files. Do not edit. |
| `_ds/modernist-…/` | The "Modernist" design system the prototype is built on: `styles.css` (tokens + component classes), `readme.md` (usage guide), `_ds_manifest.json`, `_ds_bundle.js`, and the adherence lint config. |

The project's `.thumbnail`, its screenshots of each screen and its 4× logo
exports are not imported: the pictures are captures of these files, and the
marks are generated here from the font instead
([../docs/assets/README.md](../docs/assets/README.md)).

**The design answered, on 2026-09-17.** The pass that came back settles every
question [../docs/design-handover.md](../docs/design-handover.md) asked,
including the one that was blocking: accent text at body size is
`--color-accent-700`, and the ramp mixes toward `--color-text` / `--color-bg`
rather than black and white, so a filled accent tag can be built for any accent
a player picks.

What it changed, all of it in `dInfinityPhone.dc.html`:

- **The roll screen is one picture, not a column of bands.** The table is
  full-bleed and every control is an opaque floating plate. Accent never
  touches felt.
- **A table view setting** — straight down, or angled with two walls
  ([../docs/physics-and-rendering.md](../docs/physics-and-rendering.md),
  "Rendering (normal mode)").
- **Per-set physical properties**: weight, translucency and size, all three
  real rather than metadata.
- **Dice land one at a time** and shove the dice already down. The app is to
  take the stagger and not the shove — a settled die moved by code is the
  invisible hand — and neither is built yet
  ([../docs/physics-and-rendering.md](../docs/physics-and-rendering.md),
  "What is drawn over the table").
- **The face designer gained a Solid tab** — the real polyhedron, with
  opposite-face numbering, d4 values at the corners and d6 pips. Built:
  [../docs/face-designer.md](../docs/face-designer.md), "The solid, not just
  the face".
- **The face designer's tools are the phone frame's own sprite** — the app
  draws `#ic-pencil`, `#ic-eraser`, `#ic-bucket`, `#ic-type`, `#ic-image`,
  `#ic-undo`, `#ic-redo` and `#ic-copy` from
  [dInfinityPhone.dc.html](dInfinityPhone.dc.html) and a test holds the two
  equal, so **editing a `<symbol>` in the prototype fails the build until the
  app is edited with it**. The paste and the mirror have no symbol yet:
  [../docs/design-handover.md](../docs/design-handover.md), "Five the pass did
  not reach". Built:
  [../docs/face-designer.md](../docs/face-designer.md), "The tools are
  pictures".
- **The face designer has a Save to set** where it used to autosave silently,
  and **Roll it throws the drawing** rather than a plain die of that shape.
  Built: [../docs/face-designer.md](../docs/face-designer.md), "Save to set"
  and "Flow", step 4.
- **A test throw has a way back** — the `fromDesigner` banner over the roll
  screen, which returns to the designer on the die being tested. The app
  draws it as a plate rather than in the prototype's accent tint, because
  accent never touches felt. Built:
  [../docs/face-designer.md](../docs/face-designer.md), "The way back".
- **Saved rolls are dragged into the order the player wants**; pinning and
  favourites are gone.
- **Settings gained an accent picker and lost the sound switch.**

Every one of those is folded into `docs/` — that is where a decision lives once
it is taken. What the design left open is in
[../docs/TODO.md](../docs/TODO.md).

**The roll screen has moved on since this was imported.** A device session
asked for the dice to be a pull-down at the top of the table and the formula
to sit on the right under the menu button, and a second one asked for the
formula to be **hidden behind a tab that slides in from the side**, the saved
rolls to be a **pull-up** (which option `1c` already names in one line), the
empty-tray hint to go, and the expected range to stay readable through a
chain of re-rolls. The app does all of that now; the prototype still draws an
always-out picker strip, the formula printed in the left corner, a hint block
and a saved-roll strip in the column. The tray also casts no shadow on its own
felt any more. The whole difference is tabulated in
[../docs/design-handover.md](../docs/design-handover.md), "The roll screen's
top, after the second device session", with the six questions the next pass
over the prototype has to answer. It is written down rather than drawn
because this file is generated and a rearrangement that size belongs in the
design project — see **Editing**, below.

**A throw clears the top, and a double tap clears the table** (decision 83 in
[../docs/architecture.md](../docs/architecture.md#key-decisions-log)): after a
shake the formula's tab folds into the shut `Dice` pull-down, and two taps on
the felt slide every pull-down, pull-up and tab off it until the next two. The
prototype has neither the pull-down nor the tab to fold, so this too is in the
hand-over's table rather than drawn
([../docs/physics-and-rendering.md](../docs/physics-and-rendering.md),
"Clearing the table").

**A shake is now the only way to roll** (decision 66 in
[../docs/architecture.md](../docs/architecture.md#key-decisions-log)), so two
things the prototype still draws are not the app's: the shake switch in
Settings (`1y`), which the app no longer has, and the welcome's "or tap the
tray to roll". Both are listed in
[../docs/design-handover.md](../docs/design-handover.md), "The shake is the
throw, and the prototype half-says so".

**Dice that land cocked wait for a shake** (decision 70), on a `THROW AGAIN`
plate the prototype does not draw: its `rollState` tweak has `earned` and
`stuck` and no state for it. The app borrows the earned plate's layout until
the design has one; the same hand-over section lists it.

**Imported from** project `5cee69c8-e516-4414-a446-7fd89bb7c706`, last synced
2026-09-17, with three hand edits to `dInfinityPhone.dc.html` since (see
**Editing**).

## Viewing

Open `dInfinity.dc.html` from a local web server (the runtime fetches the
imported files, so `file://` will not work):

```sh
cd design && python3 -m http.server 8000
# then http://localhost:8000/dInfinity.dc.html
```

## Which screen specifies what

The prototype and `docs/` are two halves of one specification. Each document
links to the screens that realise it; this is the same map the other way
round. Option ids (`1a`, `9c`, …) are the labels on the canvas.

| Screens | Specified in |
| --- | --- |
| Roll screen: tray, layouts, result sheet — 1a, 1b–1d, 1e–1g, 1z | [../docs/physics-and-rendering.md](../docs/physics-and-rendering.md), [../docs/tables.md](../docs/tables.md) |
| Notation field, dice pickers, errors — 2a, 1h–1j, 9c, 6d | [../docs/dice-notation.md](../docs/dice-notation.md) |
| Outcome graph — 1k–1m, 7a | [../docs/probability.md](../docs/probability.md) |
| Saved rolls, editor, import — 1n–1p, 1r, 7b, 9b, 9f–9g, 6e | [../docs/dice-notation.md](../docs/dice-notation.md) |
| Dice sets, set details, install and update — 1s–1t, 5a, 6a–6b, 8c, 9h–9i | [../docs/dice-sets.md](../docs/dice-sets.md) |
| Table picker — 1u, 8a, 9j | [../docs/tables.md](../docs/tables.md) |
| Face designer — 1v, 4c, 8d; quick mode off the breakdown, 1f; the way back from a test throw, `fromDesigner`; its export on the "My dice" details, 8c | [../docs/face-designer.md](../docs/face-designer.md) |
| Statistics, history, sessions — 1w, 1x, 5b–5c, 6c, 8b, 9e | [../docs/statistics.md](../docs/statistics.md) |
| Menu, Settings — 1q, 1y, 2d | [../README.md](../README.md), [../docs/architecture.md](../docs/architecture.md) |

## Editing

Edit in the [Claude Design project](./) and re-import here so the two stay in
step.

**`dInfinityPhone.dc.html` carries hand edits a re-import would undo.** Three
small corrections were made here after the 2026-09-17 sync, to keep the
prototype true to the app: a Fudge face prints `−`, blank and `+` rather than
its value (`faceText` beside `pipDot`); the throw buttons are gone — the
`earned` plate's `Throw N more` and `Stop the chain`, the `stuck` plate's
`Throw those N again`, and the result sheet's `Again` / `Roll again` (its
`rollAgain` handler is left in the script, unreferenced); and the face
designer's `Clear eyes` is `Clear face` (`clearFace`, dead on a blank face),
which takes everything off the face in front of the player in one undoable
step, with the undo snapshots now carrying the pips and the d4's corner
numbers so that undo brings them back
([../docs/face-designer.md](../docs/face-designer.md), "Clear face"). Before the next
import, either make the same changes in the design project or re-apply them
afterwards; `git log -- design/dInfinityPhone.dc.html` lists them
([../docs/design-handover.md](../docs/design-handover.md), "The shake is the
throw"). Anything larger is not hand-edited: it is written down in the
hand-over and waits for the design project. Decisions made in the design that change behaviour, limits or defaults
must be reflected in `docs/` in the same PR (see `../.claude/CLAUDE.md`).
An import brings the project's own `github.md` sync notes along; fold its
decisions into `docs/`, list anything left over in
[../docs/TODO.md](../docs/TODO.md), and drop the file — `docs/` is where
decisions live once they are taken.
