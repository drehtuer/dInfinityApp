plugins {
    `kotlin-dsl`
}

// The convention plugins compile against these; the modules that apply the
// plugins get them on their own classpath at apply time.
dependencies {
    implementation(libs.agp.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.compose.compiler.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
}

kotlin {
    jvmToolchain(21)
}
