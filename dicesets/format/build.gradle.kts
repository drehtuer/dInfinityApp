plugins {
  id("dinfinity.kotlin-jvm")
}

dependencies {
  api(project(":core:model"))

  // A TOML 1.0 parser rather than a hand-written one (.claude/CLAUDE.md), and
  // this one in particular because it reports the line and column of every key
  // it read: a validation report without file:line is not the report
  // docs/dice-sets.md describes. Read through its DOM into plain data classes —
  // no reflection and no deserializer ever sees a downloaded file.
  implementation(libs.tomlj)

  testImplementation(project(":test-fixtures"))
}
