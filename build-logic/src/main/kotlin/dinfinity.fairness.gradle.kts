import de.drehtuer.dinfinity.build.PhysicsFingerprintTask
import de.drehtuer.dinfinity.build.VerifyFairnessRecordTask

// The fairness gate (`docs/build-setup.md`, "Fairness before a release"),
// applied by the root build only.
//
// What decides how a die lands: the physics bridge and its native code, the
// contract it implements, the shapes and materials it is handed, the bundled
// dice, the native toolchain it is compiled with, and the test that judges
// it. A change to any of them is a change to what the record measured.
val physicsInputs =
  files(
    fileTree("simulation/api/src/main"),
    fileTree("simulation/jolt/src/main"),
    "simulation/jolt/build.gradle.kts",
    fileTree("core/model/src/main"),
    "dicesets/builtin/src/main/resources/dicesets/builtin/diceset.toml",
    "gradle.properties",
    "simulation/jolt/src/androidTest/kotlin/de/drehtuer/dinfinity/simulation/jolt/FairnessTest.kt",
  )

tasks.register<PhysicsFingerprintTask>("physicsFingerprint") {
  group = "verification"
  description = "Writes the fingerprint of the physics a fairness run measures."
  physics.from(physicsInputs)
  root.set(layout.projectDirectory)
  output.set(layout.buildDirectory.file("fairness/fingerprint.txt"))
}

tasks.register<VerifyFairnessRecordTask>("verifyFairnessRecord") {
  group = "verification"
  description = "Fails unless fairness/record.txt is a passing run of exactly this physics."
  physics.from(physicsInputs)
  root.set(layout.projectDirectory)
  record.set(layout.projectDirectory.file("fairness/record.txt"))
}
