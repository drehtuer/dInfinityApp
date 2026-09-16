plugins {
  id("dinfinity.kotlin-jvm")
}

dependencies {
  api(project(":core:model"))

  testImplementation(project(":test-fixtures"))
}

// `NotationReference` is the grammar written out for somebody to read, and it
// is English *beside the parser* on purpose: the screen that draws it is one
// module up, and a notation the app accepts and a notation the app explains
// that have drifted apart are worse than no explanation at all. Keeping them
// in one JVM module is what lets one be tested against the other, and that
// costs it resources. Translating it means mapping entries to resources by
// key, which is a design change rather than a string move
// (`docs/TODO.md`, "Open questions").
tasks.named<de.drehtuer.dinfinity.build.VerifyTextIsAResourceTask>("verifyTextIsAResource") {
  exempt.add("NotationReference.kt")
}
