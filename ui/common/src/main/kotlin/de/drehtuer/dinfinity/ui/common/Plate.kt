package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

/**
 * The ground a control stands on when it is drawn over the table
 * (`docs/physics-and-rendering.md`, "What is drawn over the table", and every
 * absolutely-positioned block over the tray in `design/dInfinityPhone.dc.html`).
 *
 * Opaque `--color-bg`, `--shadow-sm`, **no radius and no border**, `7 / 11 /
 * 8 dp` of padding, and it hugs its content rather than filling the width.
 *
 * It exists because the roll screen is one full-bleed 3D table with everything
 * floating on it, and a lit table whose colour the player picked is not
 * something words can be printed on. Two of the faults the first device
 * session found were exactly that: a formula whose dashed rule ran the width
 * of the screen, so the text read as struck through rather than underlined,
 * and a picker sitting on bare felt.
 *
 * **It is also what keeps the accent off the felt.** An accent the player
 * chooses freely and a shelf of tables whose colour is also chosen are a pair
 * nobody can check — unless the accent is never drawn on a table at all. So
 * every accent-coloured run of text on the roll screen is on one of these, and
 * the pairing that has to be legible is accent-on-`--color-bg`, which is one
 * pairing rather than a matrix (`docs/physics-and-rendering.md`).
 *
 * The shadow is Compose's elevation rather than the CSS's three lengths;
 * [Modernist.Shadow] explains what is transcribed and why it is the blur.
 */
@Composable
fun Plate(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Box(
    modifier =
      modifier
        .shadow(elevation = Modernist.Shadow.sm, shape = Modernist.square)
        .background(MaterialTheme.colorScheme.background)
        .padding(start = SIDE, top = TOP, end = SIDE, bottom = BOTTOM),
  ) {
    content()
  }
}

/**
 * `padding: 7px 11px 8px`, which is not on the `--space-*` scale and is not
 * meant to be: it is the optical padding of a label-sized block, tighter at
 * the top than at the bottom because a line of type sits high in its box.
 */
private val TOP = 7.dp
private val SIDE = 11.dp
private val BOTTOM = 8.dp
