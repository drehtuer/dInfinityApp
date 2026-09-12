plugins {
    id("dinfinity.android-app")
}

android {
    namespace = "de.drehtuer.dinfinity"
}

// The app wires every screen and every engine implementation together; it is
// the only module allowed to know about all of them.
dependencies {
    implementation(project(":feature:roll"))
    implementation(project(":feature:graph"))
    implementation(project(":feature:saved"))
    implementation(project(":feature:sets"))
    implementation(project(":feature:tables"))
    implementation(project(":feature:designer"))
    implementation(project(":feature:stats"))
    implementation(project(":feature:settings"))

    implementation(project(":dicesets:builtin"))
    implementation(project(":input:shake"))
    implementation(project(":render:filament"))
    implementation(project(":render:headless"))
    implementation(project(":simulation:jolt"))
}
