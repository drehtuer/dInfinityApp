package de.drehtuer.dinfinity.designer

/**
 * What somebody may share a package they drew under
 * (`design/dInfinity.dc.html`, option `8c`; `docs/face-designer.md`,
 * "Export details").
 *
 * A short closed list rather than free text. The `license` field of a
 * `diceset.toml` is read by whoever installs the package and by nobody else —
 * nothing in the app enforces it — so the one thing it has to do is *say
 * something the reader recognises*, and an SPDX identifier is what a reader
 * recognises. A box somebody types "free to use i guess" into says nothing.
 *
 * The five real ones are the prototype's, in its order: the two public-domain
 * and attribution Creative Commons licences people put art under, the share-
 * alike one, and the two software licences somebody who already has a
 * repository is likely to be using. [AllRightsReserved] is the sixth, and it
 * is the honest default for a drawing somebody wants to hand to one friend
 * rather than to the world — the alternative is leaving the field at
 * [UNSPECIFIED], which reads as "the author did not think about it" and makes
 * the package unusable by anyone who does.
 *
 * @param id what goes in the file. An SPDX identifier where there is one; the
 *   `LicenseRef-` form SPDX reserves for everything else where there is not,
 *   because "All rights reserved" written into a field of identifiers is a
 *   sentence pretending to be a name.
 * @param label what the chooser shows. Not translated: a licence identifier is
 *   a proper name, and a translated `CC BY 4.0` would be a licence nobody
 *   could look up.
 */
enum class SetLicense(
  val id: String,
  val label: String,
) {
  PublicDomain("CC0-1.0", "CC0 1.0 (public domain)"),
  Attribution("CC-BY-4.0", "CC BY 4.0"),
  ShareAlike("CC-BY-SA-4.0", "CC BY-SA 4.0"),
  Mit("MIT", "MIT"),
  Gpl("GPL-2.0-or-later", "GPL-2.0-or-later"),
  AllRightsReserved("LicenseRef-All-Rights-Reserved", "All rights reserved"),
  ;

  companion object {
    /**
     * What the field says while nobody has chosen.
     *
     * A word rather than an empty field, because the personal package is
     * written to disk long before anybody thinks about sharing it, and a
     * `license` that is simply absent cannot be told from one an older version
     * of the app never wrote.
     */
    const val UNSPECIFIED: String = "unspecified"

    /** The licence [id] names, or null — including for [UNSPECIFIED], which is not one. */
    fun ofId(id: String?): SetLicense? = entries.firstOrNull { it.id == id }

    /** True when [id] is a choice somebody actually made. */
    fun chosen(id: String?): Boolean = ofId(id) != null
  }
}
