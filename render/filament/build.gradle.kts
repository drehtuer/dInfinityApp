plugins {
    id("dinfinity.android-library")
}

android {
    namespace = "de.drehtuer.dinfinity.render.filament"
}

dependencies {
    api(project(":simulation:api"))
}
