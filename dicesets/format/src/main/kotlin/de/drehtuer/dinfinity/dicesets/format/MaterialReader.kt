package de.drehtuer.dinfinity.dicesets.format

import de.drehtuer.dinfinity.core.model.DieMaterial
import org.tomlj.TomlTable

/**
 * Reads the material and physics keys a `[defaults]` table or a `[[die]]`
 * entry may carry, clamping each into its range.
 *
 * Clamping with a warning rather than rejecting, because `docs/dice-sets.md`
 * says so and because it is the kinder rule: a set with a slightly too bouncy
 * d20 still works, and the author is told. A value that is not a number at
 * all is a different matter — a `NaN` reaching the solver is not a bouncy die,
 * it is a die that vanishes — so that one is an error.
 */
internal class MaterialReader(
  private val fields: TomlFields,
  private val report: Reporter,
) {
  /** The keys this reader knows, so a caller can tell a typo from a value. */
  val keys: Set<String> =
    setOf(
      "color",
      "number_color",
      "roughness",
      "metallic",
      "size_mm",
      "density",
      "translucency",
      "restitution",
      "friction",
    )

  /** [table]'s material keys, with anything it does not set taken from [inherited]. */
  fun read(
    table: TomlTable,
    where: String,
    inherited: DieMaterial = DieMaterial(),
  ): DieMaterial =
    DieMaterial(
      colorArgb = colour(table, "color", where) ?: inherited.colorArgb,
      numberColorArgb = colour(table, "number_color", where) ?: inherited.numberColorArgb,
      roughness = bounded(table, "roughness", where, DieMaterial.UnitRange) ?: inherited.roughness,
      metallic = bounded(table, "metallic", where, DieMaterial.UnitRange) ?: inherited.metallic,
      sizeMm = bounded(table, "size_mm", where, DieMaterial.SizeMmRange) ?: inherited.sizeMm,
      density = bounded(table, "density", where, DieMaterial.DensityRange) ?: inherited.density,
      translucency = percentage(table, "translucency", where) ?: inherited.translucency,
      restitution = bounded(table, "restitution", where, DieMaterial.RestitutionRange) ?: inherited.restitution,
      friction = bounded(table, "friction", where, DieMaterial.FrictionRange) ?: inherited.friction,
    )

  /**
   * A per cent as a fraction, or `null` when the file did not set one.
   *
   * `translucency` is the one material key an author writes in per cent rather
   * than in the unit the renderer wants, because "18 % translucent" is how
   * anybody describes a die and "0.18" is how nobody does
   * (`docs/dice-sets.md`). The clamping, the warning and the refusal of a
   * value that is not a number are [bounded]'s, so the two cannot disagree
   * about what a bad number means.
   */
  fun percentage(
    table: TomlTable,
    key: String,
    where: String,
  ): Double? = bounded(table, key, where, PERCENT_RANGE)?.div(PERCENT)

  /**
   * A number forced inside [range], saying so when it had to be moved, or
   * `null` when the file did not set it at all.
   */
  fun bounded(
    table: TomlTable,
    key: String,
    where: String,
    range: ClosedFloatingPointRange<Double>,
  ): Double? {
    val value = fields.decimal(table, key, where) ?: return null
    val line = fields.lineOf(table, key)
    if (!value.isFinite()) {
      report.error(ValidationCode.NotFinite, "$where's '$key' is not a number the physics can use", line)
      return null
    }
    val clamped = value.coerceIn(range)
    if (clamped != value) {
      report.warn(
        ValidationCode.Clamped,
        "$where's '$key' of $value is outside ${range.start}–${range.endInclusive}, and counts as $clamped",
        line,
      )
    }
    return clamped
  }

  /** An `#rrggbb` or `#aarrggbb` colour as ARGB, or `null` when the file did not set one. */
  fun colour(
    table: TomlTable,
    key: String,
    where: String,
  ): Int? {
    val raw = fields.string(table, key, where) ?: return null
    val line = fields.lineOf(table, key)
    val digits = raw.removePrefix("#")
    val parsed = if (digits.length in setOf(RGB_DIGITS, ARGB_DIGITS)) digits.toLongOrNull(HEX) else null
    if (parsed == null) {
      report.error(ValidationCode.WrongType, "$where's '$key' has to be a colour like \"#e8dcc0\"", line)
      return null
    }
    return if (digits.length == RGB_DIGITS) (parsed or OPAQUE).toInt() else parsed.toInt()
  }

  private companion object {
    /** What a per-cent key is clamped to before it becomes a fraction. */
    val PERCENT_RANGE: ClosedFloatingPointRange<Double> = 0.0..100.0

    const val PERCENT = 100.0
    const val RGB_DIGITS = 6
    const val ARGB_DIGITS = 8
    const val HEX = 16
    const val OPAQUE = 0xFF000000L
  }
}
