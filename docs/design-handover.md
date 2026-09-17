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
   identity; a darker label on it may be the cheaper answer.
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
| Shared widgets | `ui/common` |
| Palette, type, shapes | `app/src/main/kotlin/de/drehtuer/dinfinity/theme` |
