plugins {
    id("dinfinity.android-feature")
}

android {
    namespace = "de.drehtuer.dinfinity.feature.saved"
}

dependencies {
    api(project(":core:notation"))
    api(project(":data"))
}
