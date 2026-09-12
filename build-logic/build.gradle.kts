plugins {
  `kotlin-dsl`
}

// The convention plugins compile against these; the modules that apply the
// plugins get them on their own classpath at apply time.
dependencies {
  implementation(libs.agp.gradle.plugin)
  implementation(libs.kotlin.gradle.plugin)
  implementation(libs.compose.compiler.gradle.plugin)
  implementation(libs.detekt.gradle.plugin)
  implementation(libs.ktlint.gradle.plugin)
}

kotlin {
  jvmToolchain(21)
}

// ktlint is deliberately not applied here. Gradle generates the plugin
// accessors into a source directory under build/, they end up in the main
// source set, and neither a path filter nor overriding the tasks' source keeps
// ktlint off them — it reports tens of thousands of violations in generated
// code. The convention plugins follow the same style as the rest of the
// repository (build-logic/.editorconfig), just unenforced. See docs/TODO.md.
