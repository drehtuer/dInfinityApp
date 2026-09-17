# Hand-over to the designer

The prototype in [design/](../design/) was last synced from this repository on
**2026-09-11**. The app has moved since. This is the list of what moved, so that
the next pass over the design starts from what the app now does rather than from
what it did then.

It is written to be read next to the prototype, screen by screen. Nothing here
is a complaint about the design: most of these changes came out of running the
thing on a real phone and finding that a screen was describing something the
physics no longer did.

**How to use it.** Open [design/dInfinity.dc.html](../design/dInfinity.dc.html)
and work down the table below. Each row names the option id on the canvas, says
what changed, and says whether the design needs a decision or only needs
bringing up to date. The last section is the questions that are genuinely
yours — the app is waiting on them.

## The one change that reaches every screen with dice on it

**Dice leave the table as they are read.** A roll used to be watched until every
die had stopped, and then read all at once. It now reads each die the moment it
can be — the instant a die has come to rest showing a face — takes it off the
table, and throws whatever is left again onto the room that made. It repeats
until there is nothing left to throw.

That is invisible in a still mock-up and very visible in the hand. By the time a
roll ends, most of the dice are gone and the answer has been arriving for a
second or two. So **the dice stop being the thing to watch**, and something has
to take their place.

What took their place is a line of text: how many dice have been read, and how
high and low the finished roll can still come out. It is one line whether the
throw was four dice or a hundred, which is why it was chosen over showing the
counted dice somewhere — an overlay of ninety-nine finished dice is not a
design, it is a problem.

**For the designer:** the prototype has no region for this. It currently sits
where "Rolling…" used to be, which works but was not designed. It is the most
important unstyled thing in the app.

## Behaviour that has moved

| Screen | Option | What changed | What the design needs |
| --- | --- | --- | --- |
| Roll | `1a`–`1j` | **A roll is counted and cleared as it goes**, not read at the end. See above. | A home for the running readout |
| Roll | `1j` | **An exploding die earns a throw; it does not take one.** `8d6!` used to throw each added die by itself. The dice that are down stay down, the screen asks for a shake, and the next shake throws every die the round earned — all the sixes at once, not one at a time. | A state the prototype does not have: "down, read, and one throw short" |
| Roll | `1j` | **A roll that cannot finish says so.** There used to be a twelve-second cap that force-settled every die still moving and read it off whatever face it was nearest. That is a number nobody rolled. A roll now runs until its dice stop; if it cannot, it says how many dice would not settle and offers to throw *those and only those* again. | A refusal state, and the button on it |
| Roll | `1a` | **Tapping a saved roll puts its dice on the table** rather than throwing them. The board follows the formula as it is edited and as the picker adds to it, so what you look at before you shake is what you are about to throw. | Confirm: the prototype shows an empty tray until the throw |
| Roll | `1k` | The outcome range carries a **`+`** when a chain can still earn dice: `3d6!` reads `3 to 21+`. Without it the ceiling was `1008`, which is true and useless. | Typography for the `+` |
| Roll | `1h` | The picker row still offers only the ten standard dice. A set's own dice (`skull-d6`) will become typable as `3{skull-d6}kh1`. | Whether the picker row grows a brace form |
| Roll | `1z` | Power-saving mode puts **no surface on the screen at all**, so the tray region is absent rather than blank. | Confirm the layout with no tray |
| Tables | `1u` | The picker is **a list of rows**, where the prototype has a two-column grid of cards. Deliberate — a row fits a 44 × 64 thumbnail and a name at a touch target worth pressing — but it is a real divergence. | Decide: keep the list, or make the grid work |
| Designer | `8a`–`8d` | There is **no 3D preview**; "Roll it" is the preview, as in the prototype. Recorded so it is not mistaken for something missing. | Confirm |
| Sets | `4a` | A **photograph can be a table**. It is downsized, written into the personal package and validated like any other table. The prototype's upload sheet does not say how a photo sits on a tray that changes shape with the phone. | How a photo crops |

## What has not changed

The palette, the type, the spacing scale and the zero corner radius are exactly
the design system's, and this pass has just been through every screen putting
back the corners and spacings that had drifted. The navigation graph, the screen
inventory and the notation are all as designed.

## Questions the app is waiting on

These are in [TODO.md](TODO.md) in full. They are here because they are yours.

1. **A filled button's label is 3.76:1 on its own accent and wants 4.5:1**, and
   **a divider is 2.41:1 on the light ground and wants 3:1.** Both are measured,
   both are the design system's own tokens. Darkening the accent changes the
   identity; a darker label on it may be the cheaper answer. The app now
   carries the full accent ramp, so `--color-accent-700` and `-800` are both
   available to a fix — which turns this from "no token can do it" into a
   choice about how loud the accent is allowed to be.
2. **Is percent typography text?** `"0 %"`, `"< 0.1 %"` and `"%.1f %%"` are
   currently three different renderings of the same idea.
3. **Does a refusal keep its words, or become a reason?** Refusal sentences are
   assembled across modules, half of them in plain-Kotlin modules that may not
   depend on Android. Giving each refusal a typed reason the screen phrases is a
   design change as much as a code one, and it is what translation waits on.
4. **Should a stamp be draggable after it is put down?** The prototype lets one
   be moved; the app places it and leaves it.
5. **Should the picker row offer "Doodle this die"?**
6. **Where does a table look's texture say which package it came from?** A die's
   artwork is addressed by package and path; a table's is a bare path.
7. **How should a photo sit on the tray** — cropped at import, losing pixels
   somebody chose, or mapped at draw time, which needs the tray's aspect and the
   tray's aspect changes with the phone.

## Screens whose shape differs, not just their styling

These came out of going through the prototype screen by screen. None of them
was changed — they are structure, and structure is yours. They are listed
roughly by how much is missing.

**Statistics** (`screen="stats"`) is one screen in the prototype: a set
segmented control, a die segmented control, a grid of four figures, a vertical
face histogram with a dashed "expected if fair" line, a streak sentence, a table
of every die, and two buttons out. The app is *a list of dice you open one at a
time*, with the filters as text buttons and the histogram one row per face. The
histogram has a stated reason — a d100 is a hundred bars on a 360 dp screen, and
the design assumes a d6 or a d20 — but the rest is a different screen.

**Saved-roll statistics** (`rollstats`) likewise: the prototype has four
figures, the totals chart, a verdict and a ranked Rolled / Mean / Expected / Δ
table. The app has a roll list and a stack of sentences. Is the sentence form
deliberate?

**History groups by session; the prototype groups by day.** The prototype's
heading is a date and that date's roll count — `Tue 12 Mar · 14 rolls` — and
the app emits a heading whenever the session changes instead, with no count.
The two are not variants of one screen: a day is a fact about when, a session
is a thing somebody named. Which one does History belong to, or does it offer
both?

**Sessions** is missing the prototype's whole lower half — the inline "New
session" field, the note that deleting moves rolls to Unfiled, and the entire
"Import and export" block.

**Dice sets** groups itself with accent kickers over 2 dp rules ("Install from a
URL or file", "Installed"); the app has the same controls in a different order
with no kickers. Its rows badge state with tags — update available, default,
disabled — where the app says all three in prose and never shows which set is
the default at all.

**Dice set details** has a two-tier header (a 20 px bar, then the set's name at
**28 px/800**), provenance as a two-column table with rules, dice as a 4-up grid
of rendered pictures, and a footer row of primary/secondary/ghost actions. The
app merges the header, stacks the provenance, lists the dice and scatters the
actions up the page. **28 px is not on the type scale** — the steps either side
are 25 and 32. Which did you mean?

**The face designer's tool row should be icons**: six 44 × 44 bordered buttons,
the chosen one inverted. The app has nine text labels in a wrapping row, because
there is no icon set. Undo and redo belong in the app bar with "face N of M"
beside the title. The face strip should be 52 × 52 thumbnails of each face; the
app shows the labels, deliberately, because a set may call a face `crit` — a
thumbnail *and* a label would satisfy both. The app also has copy, turn, mirror
and paste, which are not in the prototype at all, and it autosaves where the
prototype has a Save button.

## Things the design system does not yet say

1. **The dark ramp is missing the two steps a neutral tag is made of.** Both
   ramps have reached the app since this was written — all nine steps of
   `--color-accent-*` and of `--color-neutral-*` — so `tag-accent` and
   `tag-neutral` can now be built from real tokens rather than invented ones.

   The dark ground is not a straight swap, and the prototype says exactly what
   it is: `.dz-dark` in
   [dInfinityPhone.dc.html](../design/dInfinityPhone.dc.html) **reflects each
   ramp about its middle step**. `--color-accent-100` becomes the light `-900`,
   `-200` becomes `-800`, `-700` becomes `-300`, `-800` becomes `-200`; the
   neutrals do the same, `-200`↔`-800`, `-300`↔`-700`, `-400`↔`-600`, and
   `-500` maps to itself. A step is therefore a *depth* rather than a pigment,
   and `-500` is the reflection's fixed point — the only step that is the same
   colour on both pages. That is a genuinely elegant rule and the app now
   follows it.

   **It is applied to eight steps, and `tag-neutral` is made of the two it
   misses.** `.tag-neutral` is `--color-neutral-100` filled and `-800`
   lettered, and neither is in the override list — so on a dark page it comes
   out as a near-white chip with dark grey text, an inverted badge among tags
   that follow the ground. `tag-accent`, which uses `-100` and `-800` of the
   *accent* ramp, is covered and comes out as deep red, exactly right. So the
   omission looks like an override list that stopped short rather than a
   decision. The app extends the reflection to the two missing steps, because
   the rule is unambiguous where the list is silent — but it is an inference,
   and it is the one thing here that would change a drawn screen if you meant
   the other.
2. **A tag needs both ends of a ramp, and only two accents have one.** You
   ship exact ramps for `--color-accent` and `--color-accent-2`. The app lets
   a player pick six accents, and derives the deep end of the other four the
   way `design/Logo.dc.html` derives an ad-hoc one —
   `color-mix(in srgb, accent 58%, text)`. There is no rule for the **pale**
   end, and it cannot be mixed from the accent and the ground:
   `--color-accent-100` is `#fff2ef`, lighter in the red channel than either
   of them. So a filled accent tag either stops following the accent the
   player chose, or stops matching the prototype for the accent the prototype
   was drawn in. **The app currently draws no filled accent tag at all** —
   which is the badge that says a dice set has an update. `tag-neutral` and
   `tag-outline` need no ramp and are drawn.

3. **The design system's dialog and the prototype's sheet disagree, and the
   prototype wins.** `.dialog-backdrop` in `styles.css` centres its card and
   `.dialog-actions` puts the buttons at `flex-end`; every sheet in
   `dInfinityPhone.dc.html` overrides all three — `align-items: end`,
   `width: 100%`, `justify-content: flex-start`. The app follows the phone,
   because the thumb is at the bottom of a phone. Worth making the export say
   the same thing, so the next person reading the stylesheet alone does not
   build the centred one again. There is one shared `Sheet` in the app now
   rather than nine hand-made dialogs, so this is a one-line change when you
   decide it.

4. **Does a destructive action get a colour?** The prototype's set action
   sheet draws Remove as a plain `.btn-secondary` — a bordered box with
   ordinary text, sitting between a filled toggle and a ghost Cancel
   (`design/dInfinityPhone.dc.html`, line 713). The app used to draw it as
   accent-coloured text, so Remove was the one red word in the sheet. It now
   follows the prototype, and the "this is the one that takes something away"
   signal has gone with it.

   The same question one level up: the shared confirm sheet (line 843) fills
   the destructive confirm with `.btn-primary` and gives the ghost to "Keep
   it", so the destructive action is also the loudest button on the sheet. The
   app follows that too. Both are what the prototype says and both are worth a
   second look, because a system with one red and no other colour has only
   weight and wording left to say "careful".

5. **The slider has no design.** Hue, depth and brightness in the designer use
   Material's, which has a circular thumb and a rounded track, in a system with
   no round anything.
6. **Uppercase.** The prototype sets kickers and column headings in
   `text-transform: uppercase` with wide tracking. The tracking is applied; the
   case is not, because Compose has no text transform, so applying it means
   uppercasing the string itself and changing what a screen reader says.

   Until this pass the app did **both**: the dice-set screens uppercased in
   Kotlin and every other screen did not. There is one shared kicker now and it
   does not, so the app is at least consistent — and one reason it does not is
   that a kicker is sometimes a name somebody typed. The History heading is a
   session's name, and `THORIN'S CAMPAIGN` is a decision about someone else's
   words. If uppercase is wanted, is it wanted on those too?
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

The tokens moved into `ui/common` in this pass. They used to live beside the
theme in `app/`, which every screen module is *below* rather than above — so no
screen could read them and six of them each kept a transcription of the same
scale. Nothing about the design changed; it is only that the numbers now have
one home, and a test reads `styles.css` to prove that home still agrees with
it.
