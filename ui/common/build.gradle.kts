plugins {
  id("dinfinity.android-feature")
}

android {
  namespace = "de.drehtuer.dinfinity.ui.common"
}

dependencies {
  // What the shared pieces are *about*: formulas and the dice a formula names.
  // No data, no simulator, no navigation — a screen's furniture should not be
  // able to reach a database (`docs/architecture.md`, Modules).
  api(project(":core:notation"))
}

// The design system is allowed to write the design system down. `Modernist.kt`
// is the one file in the app whose job is to say what `--color-bg`, the accent
// ramp and the spacing scale actually are; every other file — in this module
// and in every feature — reads them from it or from the theme
// (`docs/design-handover.md`).
tasks.named<de.drehtuer.dinfinity.build.VerifyDesignSystemTask>("verifyDesignSystem") {
  exempt.addAll("Modernist.kt")
}
