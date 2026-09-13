plugins {
  id("dinfinity.kotlin-jvm")
}

dependencies {
  api(project(":simulation:api"))

  testImplementation(project(":test-fixtures"))
}
