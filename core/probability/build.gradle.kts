plugins {
  id("dinfinity.kotlin-jvm")
}

dependencies {
  api(project(":core:model"))
  api(project(":core:notation"))

  testImplementation(project(":test-fixtures"))
}
