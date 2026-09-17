package de.drehtuer.dinfinity.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The Modernist design system's tokens, transcribed from
 * `design/_ds/modernist-.../styles.css` — the single source of the look
 * (see `design/README.md`).
 *
 * Flat, architectural, one red, zero corner radius, strong 2 dp rules. The
 * values here must stay equal to the CSS, and [ModernistTest] reads the
 * stylesheet to say so: re-import the design system with a different scale and
 * a test fails, rather than a screen quietly going out of step.
 *
 * **It lives here, in `ui/common`, because this is the only module every
 * screen can see.** It used to live in `:app` — which *depends on* the feature
 * modules, so no feature could reach it, and six of them each kept a
 * transcription of the same numbers. Six copies of one scale is six chances
 * for it to drift; the arrow is the reason, so the fix is to move the tokens
 * rather than to check the copies harder (`docs/design-handover.md`).
 */
object Modernist {
  /** Light ground: ink on paper. */
  object Light {
    val background = Color(0xFFF3F2F2)
    val surface = Color(0xFFEAE9E9)
    val text = Color(0xFF201E1D)
  }

  /**
   * Dark ground. The design swaps ink and ground and leaves the accent
   * alone (`design/dInfinity.dc.html`, option 2b).
   */
  object Dark {
    val background = Color(0xFF201E1D)
    val surface = Color(0xFF2D2B2B)
    val text = Color(0xFFF3F2F2)
  }

  /** The system's single accent, used sparingly. */
  val accent = Color(0xFFEC3013)
  val accent600 = Color(0xFFDD2B0F)
  val accent700 = Color(0xFFAE1800)

  /**
   * Body copy in the accent must use a deep ramp step: the accent itself
   * reaches only 3:1 against the ground, which is enough for chrome and
   * large text but not for paragraphs.
   */
  val accentOnLightText = accent700

  /**
   * `--color-accent-100` … `--color-accent-900`: the accent's own ramp, as it
   * is on the **light** ground.
   *
   * The design system ships nine steps of it and the app was using three. The
   * pale end is what a `.tag-accent` is filled with and the deep end is what
   * its label is printed in — a pairing that needs both ends of a ramp and
   * cannot be made by thinning one colour, which is why tags could not be
   * drawn at all while only the middle existed.
   *
   * **A dark ground reflects the ramp about [v500]**: `.dz-dark` in
   * `design/dInfinityPhone.dc.html` redefines `--color-accent-100` as the
   * light `-900`, `-200` as `-800`, `-700` as `-300` and `-800` as `-200`. So
   * a step is not a fixed colour — it is a *depth*, and which pigment it lands
   * on depends on the page. Only [v500] is the same on both.
   */
  object Accent {
    val v100 = Color(0xFFFFF2EF)
    val v200 = Color(0xFFFFE0D9)
    val v300 = Color(0xFFFFC4B8)
    val v400 = Color(0xFFFF9783)
    val v500 = Color(0xFFFF563C)
    val v600 = Color(0xFFDD2B0F)
    val v700 = Color(0xFFAE1800)
    val v800 = Color(0xFF7C1405)
    val v900 = Color(0xFF4D170E)
  }

  /**
   * `--color-neutral-100` … `--color-neutral-900`: the grey ramp, as it is on
   * the **light** ground.
   *
   * It reflects about [v500] on a dark ground, the same way the accent ramp
   * does: `.dz-dark` redefines `--color-neutral-200` as the light `-800`,
   * `-300` as `-700` and `-400` as `-600`. **[v500] is the reflection's fixed
   * point** — the one step that is `#9b9797` on both grounds by construction
   * rather than by coincidence, and therefore the only one a screen can name
   * when it wants a grey that does not follow the page. `feature/graph` draws
   * a bar outside ±1σ in exactly that step, for exactly that reason.
   *
   * Naming a step is still better than thinning the ink, which is what it
   * replaced: 45 % of the ink lands near `-500` on a light page but near
   * `-600` on a dark one, and on two further greys again where it composites
   * against a surface rather than the ground.
   */
  object Neutral {
    val v100 = Color(0xFFF8F4F4)
    val v200 = Color(0xFFEAE7E7)
    val v300 = Color(0xFFD7D3D3)
    val v400 = Color(0xFFBAB6B6)
    val v500 = Color(0xFF9B9797)
    val v600 = Color(0xFF7D7979)
    val v700 = Color(0xFF605D5D)
    val v800 = Color(0xFF444141)
    val v900 = Color(0xFF2D2B2B)
  }

  /** `--color-divider`: the text colour at 40 %. Rules are 2 dp, not hairlines. */
  fun divider(text: Color): Color = text.copy(alpha = DIVIDER_ALPHA)

  /** `--space-1` … `--space-8`. Nothing in the app is off this scale. */
  val x1: Dp = 4.dp
  val x2: Dp = 8.dp
  val x3: Dp = 12.dp
  val x4: Dp = 16.dp
  val x6: Dp = 24.dp
  val x8: Dp = 32.dp

  /**
   * `.hr`, `.nav`'s underline and every section rule: 2 dp. The system draws
   * rules, not hairlines — a 1 px line is what it uses *inside* one block
   * ([hairline]), to separate rows of the same thing.
   */
  val rule: Dp = 2.dp

  /**
   * `.table td`'s `border-bottom: 1px`, `.btn-secondary`'s and `.input`'s
   * `border: 1px` — the thinnest line the system draws, and what edges a
   * control or divides two rows of one list.
   */
  val hairline: Dp = 1.dp

  /** `--radius-*` is 0 on purpose. Nothing in this system has a rounded corner. */
  val radius: Dp = 0.dp

  /**
   * [radius], as the shape a Material component has to be handed.
   *
   * Material's buttons are pills unless they are told otherwise:
   * `ButtonDefaults.shape` reads `CornerFull`, which is a circle no `Shapes`
   * override reaches.
   */
  val square: Shape = RectangleShape

  /** The CSS type scale, in sp. */
  object Type {
    val display: TextUnit = 42.sp
    val heading: TextUnit = 32.sp
    val title: TextUnit = 25.sp
    val subtitle: TextUnit = 20.sp
    val body: TextUnit = 15.sp
    val label: TextUnit = 13.sp
    val caption: TextUnit = 11.sp

    /** `.card-title` — a heading a step below the subtitle. */
    val cardTitle: TextUnit = 17.sp

    /** `.btn` — the heading face at this size, not a body weight. */
    val button: TextUnit = 14.sp
  }

  /**
   * `h6` and `.table th`: `letter-spacing: .08em`, uppercase. The tracking a
   * section heading and a column heading are set at.
   */
  val headingTracking: TextUnit = 0.08.em

  /** `.card-kicker`: `letter-spacing: .1em` — the widest the system tracks. */
  val kickerTracking: TextUnit = 0.1.em

  /** How far the ink is dimmed for secondary copy: the prototype's `opacity: .65`. */
  const val MUTED: Float = 0.65f

  /** `.text-muted`: `color-mix(... var(--color-text) 55%, transparent)`. */
  const val FAINT: Float = 0.55f

  /** `.btn:disabled { opacity: .45 }`. */
  const val DISABLED: Float = 0.45f

  private const val DIVIDER_ALPHA = 0.4f
}
