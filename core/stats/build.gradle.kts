plugins {
  id("dinfinity.kotlin-jvm")
}

dependencies {
  api(project(":core:model"))

  // What a formula was *supposed* to do. Comparing the totals somebody rolled
  // against the exact distribution is arithmetic over both, and it belongs
  // where the rest of the statistics arithmetic is (`docs/statistics.md`).
  api(project(":core:probability"))

  testImplementation(project(":test-fixtures"))
}
