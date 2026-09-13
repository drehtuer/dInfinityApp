# Outcome graph

> **Design:** the three graph treatments are options 1k–1m of the
> [clickable design](../design/dInfinity.dc.html) ([design/](../design/)); 7a shows the graph opened
> after a roll, with the rolled total marked.

Before rolling, the app shows the probability distribution of the total.
This works for a typed formula in the editor *and* for dice picked by
tapping on the roll screen — both are the same `RollPlan` underneath. It
lets a player see at a glance that `3d6` is bell-shaped and centred on 10.5,
while `1d20` is flat, and what `2d20kh1` does to the odds.

The graph is available even for formulas the table cannot physically roll
(`500d6`); the roll button is what gets disabled, not the math.

## What is shown

- A bar chart of P(total = k) for every reachable k, with the mean marked and
  ±1σ shaded.
- A toggle for **"at least"** (P(total ≥ k)), which is what most game
  questions actually ask ("what are my odds of hitting AC 15?"). Tapping a bar
  shows the exact numbers.
- Mean, standard deviation, min, max, and the probability of the extreme
  results.
- For formulas with a label, the label is used as the chart title.

The distribution is bell-shaped for sums of many dice (central limit
theorem), but it is **not** computed as a normal approximation — that would be
wrong for a single d20 and visibly wrong for `2d6`. We compute the exact
discrete distribution.

## Computation

Every AST node produces a probability mass function (PMF): a map from integer
outcome to probability, stored as a dense array with an offset.

| Node | PMF |
|---|---|
| integer `n` | `{n: 1}` |
| single die with faces `f₁…fₘ` | each distinct value with probability (count / m) — face values come from the set, so a d6 labelled `1,2,1,2,1,2` gives the d2 distribution automatically |
| `NdX` | convolve the single-die PMF with itself N times (FFT above ~64 dice, plain O(N·m²) below) |
| `+`, `-` | convolution (with negation for `-`) |
| `*`, `/` by a constant | value remap |
| `*` of two dice expressions | product distribution by enumeration over both supports (bounded by the limits in `docs/dice-notation.md`) |
| `kh n` / `kl n` / `dh n` / `dl n` | order statistics: enumerate outcomes of N dice via dynamic programming over sorted values; exact, O(N · m · support) |
| `!` (exploding) | geometric tail, truncated at the explosion depth limit; the truncated mass (< 1e-9 for d6 at depth 20) is reported in the tooltip |
| `r n` (reroll once) | conditional PMF composition |
| `min n` | clamp remap |

Support size is bounded by the notation parse limit (1,000 dice × max face
value), so memory stays small. Results are cached per formula string.

Precision: `Double`, with probabilities renormalised after each convolution
step. For display we show 4 significant digits.

## Physics vs. probability

The graph assumes fair dice. The physics simulation with uniform-density
convex solids and honest tumbling *is* fair for the catalogue shapes, up to
numerical noise; the built-in test suite rolls each catalogue die 100,000
times in power-saving mode and asserts a chi-squared test passes.

Since v1's shape catalogue is closed (`docs/dice-sets.md`), every die in
every set is one of those eight tested solids, however it is painted: a
custom-looking die is still a fair die, and the graph is exact for all of
them. If author-supplied mesh shapes arrive later, the graph will have to
assume fairness it cannot verify and say so on screen — which is one more
reason they are not in v1.

## Testing

- Property tests: the PMF sums to 1 ± 1e-12; mean of `NdX` equals
  N·(X+1)/2 for standard faces; `2d20kh1` matches the closed form.
- Golden tests against a brute-force enumerator for small formulas.
