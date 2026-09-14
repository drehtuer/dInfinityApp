plugins {
  id("dinfinity.android-feature")
}

android {
  namespace = "de.drehtuer.dinfinity.feature.sets"
}

dependencies {
  api(project(":dicesets:format"))
  api(project(":dicesets:install"))

  // The die outlines the details screen draws, shared with the roll screen's
  // picker so a d20 is the same shape in both.
  // What a formula resolves against: the library rebuilds the catalogue every
  // time it reads the folder, because those are the same facts.
  api(project(":core:notation"))

  api(project(":ui:common"))

  // What the player has switched off. What is *installed* comes off the disk;
  // only the opinion about it is in the database (`docs/dice-sets.md`).
  api(project(":data"))

  // Against a real database, like the other screens that read one. What the
  // registry does — absence meaning enabled, and pruning rows whose folder has
  // gone — is SQL, and a fake would only assert that the fake works.
  testImplementation(libs.androidx.room.runtime)
}
