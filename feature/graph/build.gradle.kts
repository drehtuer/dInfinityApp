plugins {
    id("dinfinity.android-feature")
}

android {
    namespace = "de.drehtuer.dinfinity.feature.graph"
}

dependencies {
    api(project(":core:notation"))
    api(project(":core:probability"))
}
