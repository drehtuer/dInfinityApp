// The single application module.

import com.android.build.api.artifact.SingleArtifact
import de.drehtuer.dinfinity.build.RenameApkTask

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("dinfinity.quality")
}

val androidApi = providers.gradleProperty("dinfinity.androidApi").get().toInt()
val minimumSdk = providers.gradleProperty("dinfinity.minSdk").get().toInt()

// One source of truth for the version: version.txt at the repository root.
// `1.2.0` becomes versionName "1.2.0" and versionCode 10200.
val projectVersion: String = rootProject.file("version.txt").readText().trim()
val projectVersionCode: Int = projectVersion.split(".").let { (major, minor, patch) ->
    major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()
}

android {
    compileSdk = androidApi

    defaultConfig {
        minSdk = minimumSdk
        targetSdk = androidApi
        versionName = projectVersion
        versionCode = projectVersionCode
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), file("proguard-rules.pro"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

// dInfinityApp-<version>.apk / dInfinityApp-<version>-debug.apk
androidComponents {
    onVariants { variant ->
        val suffix = if (variant.buildType == "release") "" else "-${variant.buildType}"
        val capitalised = variant.name.replaceFirstChar { it.uppercase() }
        val rename = tasks.register<RenameApkTask>("renameApk$capitalised") {
            group = "build"
            description = "Copies the $capitalised APK to its release name."
            apkDirectory.set(variant.artifacts.get(SingleArtifact.APK))
            builtArtifactsLoader.set(variant.artifacts.getBuiltArtifactsLoader())
            appVersion.set(projectVersion)
            variantSuffix.set(suffix)
            outputDirectory.set(layout.buildDirectory.dir("outputs/named-apk/${variant.name}"))
        }
        // The variant's assemble task does not exist yet at onVariants time,
        // so match it lazily rather than looking it up.
        tasks.matching { it.name == "assemble$capitalised" }.configureEach { finalizedBy(rename) }
    }
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    val bom = platform(libs.findLibrary("compose-bom").get())
    "implementation"(bom)
    "implementation"(libs.findLibrary("compose-ui").get())
    "implementation"(libs.findLibrary("compose-foundation").get())
    "implementation"(libs.findLibrary("compose-material3").get())
    "implementation"(libs.findLibrary("compose-ui-tooling-preview").get())
    "implementation"(libs.findLibrary("androidx-core-ktx").get())
    "implementation"(libs.findLibrary("androidx-activity-compose").get())
    "implementation"(libs.findLibrary("androidx-navigation-compose").get())
    "debugImplementation"(libs.findLibrary("compose-ui-tooling").get())
    "debugImplementation"(libs.findLibrary("compose-ui-test-manifest").get())
    "testImplementation"(bom)
    "testImplementation"(libs.findLibrary("junit4").get())
    "testImplementation"(libs.findLibrary("robolectric").get())
    "testImplementation"(libs.findLibrary("androidx-test-junit").get())
    "testImplementation"(libs.findLibrary("compose-ui-test-junit4").get())
}

// Robolectric reaches into JDK internals (java.io.FileDescriptor among others),
// which the module system closes off from Java 17 on. Without these opens every
// Robolectric test fails at start-up with "perhaps JRE has changed?".
val robolectricJvmArgs = listOf(
    // SharedSecrets is not merely closed, it is unexported, so it needs
    // --add-exports rather than --add-opens.
    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
    "--add-opens=java.base/java.io=ALL-UNNAMED",
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
    "--add-opens=java.base/java.net=ALL-UNNAMED",
    "--add-opens=java.base/java.nio=ALL-UNNAMED",
    "--add-opens=java.base/java.security=ALL-UNNAMED",
    "--add-opens=java.base/java.text=ALL-UNNAMED",
    "--add-opens=java.base/java.util=ALL-UNNAMED",
)

tasks.withType<Test>().configureEach {
    jvmArgs(robolectricJvmArgs)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
