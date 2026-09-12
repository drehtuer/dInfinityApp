plugins {
    id("dinfinity.android-library")
}

android {
    namespace = "de.drehtuer.dinfinity.simulation.jolt"
}

dependencies {
    api(project(":simulation:api"))
}
