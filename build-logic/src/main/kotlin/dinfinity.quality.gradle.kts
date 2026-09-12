// Linters and static analysis, applied by every other convention plugin so no
// module can opt out by accident. Warnings fail the build (.claude/CLAUDE.md).

import io.gitlab.arturbosch.detekt.Detekt

plugins {
  id("io.gitlab.arturbosch.detekt")
  id("org.jlleitschuh.gradle.ktlint")
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
