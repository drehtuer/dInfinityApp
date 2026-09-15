plugins {
  id("dinfinity.kotlin-jvm")
}

dependencies {
  // The numbers a run is made of come out of a `SimulationOutcome`, and the
  // bars it is scored against are the constants `CorrectionLadder` and
  // `SettleRule` already hold. One place for a budget, not two.
  api(project(":simulation:api"))

  // JSON through its DOM, never through a deserializer — see the note in the
  // version catalogue. Here the reason is the other one: the document is the
  // artefact a person reads off a run, so every field is written and read by
  // name and a missing one is named in the failure.
  implementation(libs.kotlinx.serialization.json)
}
