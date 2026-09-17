# The app and the design, and where they still differ

The prototype in [design/](../design/) and the documents in `docs/` are two
halves of one specification. This is the list of places where the two halves do
not yet say the same thing, kept so that the next pass over either one starts
from what is actually true.

It was written on 2026-09-17 as a hand-over to the designer, eleven questions
long. **The pass came back the same day and answered all eleven.** What is left
here is what that pass did not cover, plus the questions it asked back.

## What was answered, and where the answer lives now

A decision lives in `docs/` once it is taken, so none of these are specified
here any more — this table is only a map from the question to its home.

| Question | Answer | Written down in |
| --- | --- | --- |
| Does the roll screen keep its bands, or become one picture? | One picture. Every control is an opaque floating plate | `docs/physics-and-rendering.md`, "What is drawn over the table" |
| Where does the running readout go? | Its own plate across the bottom, with the count, the still-possible range and a progress rule | same |
| Should last-pass dice be marked? | Yes — accent outline and a `pass 2` label | same |
| What does the accent do over felt? | Nothing. Accent only ever appears on a plate | same |
| Typography for the `+` on an open chain | `--color-accent-700`, like every accent-coloured run of text at body size | same |
| Six accent swatches wrap 5 + 1 | Four across, 44 dp: six presets and a custom swatch come out 4 + 3 | `docs/architecture.md`, "Settings" |
| A filled accent tag needs both ends of a ramp | Both ends are mixed from the accent the player picked, against text and ground rather than black and white. Built: `ui/common`'s `TagKind.Accent`, and it is what says "Update available" on a dice-set row | `docs/architecture.md`, "Settings" |
| Is there a 3D preview in the designer? | There is now: a **Solid** tab | `docs/face-designer.md`, "The solid, not just the face" |
| Where does the total go, and how many times? | Once, in the result sheet | `docs/TODO.md`, 4.1 |

The pass also decided a good deal nobody had asked about — per-set weight,
translucency and size; a table-view setting; staggered drops; opposite-face
numbering; dragged saved rolls; the two-stage back gesture; and the removal of
the sound switch. All of it is in `docs/` beside the rest, and `docs/TODO.md`
has the work each one makes.

## Still open

### The design asked six of its own

They are in `docs/TODO.md` under "Open questions": uppercase on a session-name
kicker, whether Statistics splits its three axes, whether History groups by day
or by saved roll, whether a set row carries make-default, whether the d18 keeps
its true shape, and whether an oversized stamp on a d4 shrinks to fit.

### Two the app is waiting on

1. **Is percent typography text?** `"0 %"`, `"< 0.1 %"` and `"%.1f %%"` are
   three renderings of the same idea, and the space before the `%` is a
   convention that differs by language.
2. **Does a refusal keep its words, or become a reason?** Refusal sentences are
   assembled in plain-Kotlin modules that may not depend on Android. Giving
   each refusal a typed reason the screen phrases is a design change as much as
   a code one, and it is what translation waits on.

### Four the pass did not reach

- **Power-saving mode still draws nothing.** The prototype has a panel — grey,
  "Power-saving mode" over "Same physics, no rendering. The result is identical
  to what the tray would show." The app puts no surface on the screen and adds
  no panel, so a player sees an empty tray and a number arriving from nowhere.
  The first device session lost twenty minutes to it, convinced the renderer
  had failed. **It is the clearest case of the prototype being right and the
  app simply not having built it** (`docs/physics-and-rendering.md`).
- **The table picker is a list of rows**, where the prototype `1u` is a
  two-column grid of cards. Deliberate — a row fits a 44 × 64 dp thumbnail and
  a name at a touch target worth pressing — but a real divergence, and a card
  gives a picture about five times the area.
- **How does a photograph sit on the tray?** Cropped at import, losing pixels
  somebody chose, or mapped at draw time, which needs the tray's aspect — and
  the tray's aspect changes with the phone.
- **Does the picker row grow a brace form?** A set's own dice (`skull-d6`) are
  becoming typable as `3{skull-d6}kh1`; the row still offers only the ten
  standard dice.

## Screens whose shape still differs

None of these was changed by either side. They are structure, and structure is
the design's.

**Statistics** (`screen="stats"`) is one screen in the prototype: a set
segmented control, a die segmented control, a grid of four figures, a vertical
face histogram with a dashed "expected if fair" line, a streak sentence, a table
of every die, and two buttons out. The app is *a list of dice you open one at a
time*, with the filters as text buttons and the histogram one row per face. The
histogram has a stated reason — a d100 is a hundred bars on a 360 dp screen —
but the rest is a different screen. The design's own question 2 is about this
screen, so it is being looked at from both ends.

**Saved-roll statistics** (`rollstats`) likewise: the prototype has four
figures, the totals chart, a verdict and a ranked Rolled / Mean / Expected / Δ
table; the app has a roll list and a stack of sentences. Is the sentence form
deliberate?

**History** groups by session where the prototype groups by day — `Tue 12 Mar ·
14 rolls` against a heading whenever the session changes, with no count. A day
is a fact about when; a session is a thing somebody named. The design's own
question 3 asks the same thing.

**Sessions** is missing the prototype's whole lower half — the inline "New
session" field, the note that deleting moves rolls to Unfiled, and the entire
"Import and export" block.

**Dice set details** has a two-tier header (a 20 dp bar, then the set's name at
**28 dp/800**), provenance as a two-column table with rules, dice as a 4-up grid
of rendered pictures, and a footer row of primary/secondary/ghost actions. The
app merges the header, stacks the provenance, lists the dice and scatters the
actions up the page. **28 dp is not on the type scale** — the steps either side
are 25 and 32. Which did you mean?

**The face designer's tool row should be icons**: six 44 × 44 dp bordered
buttons, the chosen one inverted. The app has nine text labels in a wrapping
row, because there is no icon set. Undo and redo belong in the app bar with
"face N of M" beside the title. The face strip should be 52 × 52 dp thumbnails;
the app shows the labels, deliberately, because a set may call a face `crit` —
a thumbnail *and* a label would satisfy both. The app also has copy, turn,
mirror and paste, which are not in the prototype at all, and it autosaves where
the prototype has a Save button.

## Things the design system does not yet say

1. **A neutral tag has no dark ground.** `.dz-dark` in
   [dInfinityPhone.dc.html](../design/dInfinityPhone.dc.html) **reflects each
   ramp about its middle step** — `--color-accent-100` becomes the light
   `-900`, `-200` becomes `-800`, `-700` becomes `-300`, `-800` becomes `-200`,
   and the neutrals do the same, `-200`↔`-800`, `-300`↔`-700`, `-400`↔`-600`,
   with `-500` its own fixed point. A step is a *depth* rather than a pigment,
   which is a genuinely elegant rule, and the app follows it.

   **It is applied to eight steps, and `tag-neutral` is made of the two it
   misses.** `.tag-neutral` is `--color-neutral-100` filled and `-800`
   lettered, and neither is in the override list — so on a dark page it comes
   out as a near-white chip with dark grey text, an inverted badge among tags
   that follow the ground. `tag-accent` is the question that no longer arises:
   the app mixes both of its ends from the accent the player picked, against
   the ground's own page and ink, so it follows the page by construction rather
   than by being on the override list (`core/model/AccentRamp`). The app
   extends the reflection to the two missing steps, because the rule is
   unambiguous where the list is silent — but it is an inference, and it is the
   one thing here that would change a drawn screen if you meant the other. Its
   contrast is measured in a test rather than eyeballed: 9.3:1 on paper and
   11.4:1 on the dark ground.
2. **The design system's dialog and the prototype's sheet disagree, and the
   prototype wins.** `.dialog-backdrop` in `styles.css` centres its card and
   `.dialog-actions` puts the buttons at `flex-end`; every sheet in
   `dInfinityPhone.dc.html` overrides all three — `align-items: end`,
   `width: 100%`, `justify-content: flex-start`. The app follows the phone,
   because the thumb is at the bottom of a phone. Worth making the export say
   the same thing, so the next person reading the stylesheet alone does not
   build the centred one again. There is one shared `Sheet` in the app, so it
   is a one-line change when you decide it.
3. **One tag says something other than what the prototype writes.** The
   dice-set rows are badged `default` and, for a set somebody has turned off,
   **`switched off`** where the prototype writes `disabled`.

   The reason is not a preference. On Android, "disabled" is the word a screen
   reader says about a control that **cannot be operated**, and these rows very
   much can be — tap to open, long-press to act. A listener hearing "Brass,
   1.0.0, 5 dice, disabled" would take it as a dead row. It is the one place
   where the app says something the prototype does not, and it is flagged here
   so that nobody "fixes" it back.
4. **Does a destructive action get a colour?** The prototype draws Remove as a
   plain `.btn-secondary`, and fills the destructive confirm with
   `.btn-primary` while giving the ghost to "Keep it" — so the destructive
   action is the loudest button on the sheet. The app follows both, and the
   "this is the one that takes something away" signal has gone with it. A
   system with one red and no other colour has only weight and wording left to
   say "careful".
5. **The slider has no design.** Hue, depth and brightness in the designer use
   Material's, which has a circular thumb and a rounded track, in a system with
   no round anything.
6. **Uppercase.** The prototype sets kickers and column headings in
   `text-transform: uppercase` with wide tracking. The tracking is applied; the
   case is not, because Compose has no text transform, so applying it means
   uppercasing the string itself and changing what a screen reader says. There
   is one shared kicker and it does not, so the app is consistent — and one
   reason it does not is that a kicker is sometimes a name somebody typed,
   which is the design's own first open question seen from this side.
7. **The designer's canvas paper is white** — a literal, not a token. Is that
   the die's real painted ground, or chrome that should follow the theme?
8. **`gap: 6px` in the face strip** is not on the 4/8/12 scale. Deliberate?

## Where the screens live in the code

| Screen | Module |
| --- | --- |
| Roll, result sheet, picker, tray | `feature/roll` |
| Outcome graph | `feature/graph` |
| Saved rolls, editor | `feature/saved` |
| Dice sets, set detail | `feature/sets` |
| Table picker | `feature/tables` |
| Face designer | `feature/designer` |
| Statistics, history, sessions | `feature/stats` |
| Settings, menu, notation reference | `feature/settings` |
| Shared widgets, **and the design tokens** | `ui/common` |
| Palette, type and shapes as Material's roles | `app/src/main/kotlin/de/drehtuer/dinfinity/theme` |

A test in `ui/common` reads `styles.css` and proves the tokens still agree with
it, so the two sides of the specification cannot drift apart silently.
