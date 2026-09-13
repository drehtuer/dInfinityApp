plugins {
  id("dinfinity.kotlin-jvm")
}

dependencies {
  api(project(":core:model"))

  testImplementation(project(":test-fixtures"))
}
