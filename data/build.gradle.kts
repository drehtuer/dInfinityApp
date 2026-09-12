plugins {
    id("dinfinity.android-library")
}

android {
    namespace = "de.drehtuer.dinfinity.data"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:stats"))
}
