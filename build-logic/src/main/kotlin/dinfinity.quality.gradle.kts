// Linters and static analysis, applied by every other convention plugin so no
// module can opt out by accident. Warnings fail the build (.claude/CLAUDE.md).

import de.drehtuer.dinfinity.build.VerifyTextIsAResourceTask
import io.gitlab.arturbosch.detekt.Detekt

plugins {
  id("io.gitlab.arturbosch.detekt")
  id("org.jlleitschuh.gradle.ktlint")
  id("dinfinity.coverage")
}

detekt {
  buildUponDefaultConfig = true
  allRules = false
  config.setFrom(rootProject.file("config/detekt/detekt.yml"))
  parallel = true
  ignoreFailures = false
}

tasks.withType<Detekt>().configureEach {
  jvmTarget = "21"
  reports {
    html.required.set(true)
    xml.required.set(true)
    sarif.required.set(false)
    md.required.set(false)
  }
}

ktlint {
  android.set(true)
  ignoreFailures.set(false)
  filter {
    exclude { it.file.path.contains("/build/") }
  }
}

/**
 * Words a screen reads out must be string resources, not Kotlin literals.
 *
 * Android Lint's `HardcodedText` is enabled everywhere this project has an
 * `android` block, and it cannot see any of this: it reads layout XML, and
 * every screen here is Compose. So the rule is enforced by a check of its own
 * (`docs/architecture.md`, "Text a person reads").
 */
val verifyTextIsAResource =
  tasks.register<VerifyTextIsAResourceTask>("verifyTextIsAResource") {
    group = "verification"
    description = "Checks that no Kotlin literal reaches a screen as words."
    sources.from(fileTree("src/main/kotlin") { include("**/*.kt") })
    relativeTo.set(rootProject.layout.projectDirectory)
  }

tasks.named("check") {
  dependsOn(verifyTextIsAResource)
}
