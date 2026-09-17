// The single application module.

import com.android.build.api.artifact.SingleArtifact
import de.drehtuer.dinfinity.build.RenameApkTask
import de.drehtuer.dinfinity.build.configureDeviceTestVerification
import de.drehtuer.dinfinity.build.generateRobolectricProperties
import java.util.Properties

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
  id("dinfinity.quality")
}

// Signing keys live outside the repository (keystore/README.md). When
// keystore.properties is absent — a fresh checkout, or a CI run that only has
// to compile — debug builds fall back to the Android default debug key and
// release builds come out unsigned, rather than failing the build.
val keystoreProperties: Properties? =
  rootProject
    .file("keystore/keystore.properties")
    .takeIf { it.isFile }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

/**
 * v2 and v3, and neither v1 nor v4.
 *
 * v3 is the one that matters: it carries a proof-of-rotation record, so a
 * release key that is lost or compromised can be rotated to a new one and
 * installed apps still accept the update. Without it the key signed into the
 * first published APK is the only key that can ever update it. AGP does not
 * turn v3 on by default, so it is set here.
 *
 * v1 (JAR signing) is only read below API 24 and this app starts at 36, so it
 * would add a second, weaker signature nobody verifies. v4 is left off because
 * it writes a separate `.apk.idsig` next to the APK, which only speeds up
 * `adb install --incremental` and would have to be carried alongside every
 * release artefact.
 */
fun com.android.build.api.dsl.ApkSigningConfig.applySigningSchemes() {
  enableV1Signing = false
  enableV2Signing = true
  enableV3Signing = true
  enableV4Signing = false
}

fun keystoreEntry(prefix: String): Map<String, String>? {
  val props = keystoreProperties ?: return null
  val values =
    listOf("StoreFile", "StorePassword", "KeyAlias", "KeyPassword")
      .associateWith { props.getProperty(prefix + it).orEmpty() }
  return values.takeIf { entry -> entry.values.none(String::isBlank) }
}

val jacocoToolVersion =
  extensions
    .getByType<VersionCatalogsExtension>()
    .named("libs")
    .findVersion("jacoco")
    .get()
    .requiredVersion

val androidApi = providers.gradleProperty("dinfinity.androidApi").get().toInt()
val minimumSdk = providers.gradleProperty("dinfinity.minSdk").get().toInt()
val buildTools = providers.gradleProperty("dinfinity.buildTools").get()

// One source of truth for the version: version.txt at the repository root.
// `1.2.0` becomes versionName "1.2.0" and versionCode 10200.
val projectVersion: String = rootProject.file("version.txt").readText().trim()
val projectVersionCode: Int =
  projectVersion.split(".").let { (major, minor, patch) ->
    major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()
  }

android {
  compileSdk = androidApi
  buildToolsVersion = buildTools

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

  androidResources {
    // v1 ships English, and only English reaches the APK.
    //
    // Not a decision about what the app *could* speak — every string a screen
    // says is a resource, so a `values-de/` would work the day somebody writes
    // one (`docs/architecture.md`, "Text a person reads"). This is about what
    // is shipped: AndroidX and Material carry translations into seventy-odd
    // languages, and packing all of them beside one English app is weight
    // nobody reads. Adding a language here is the same line as adding the
    // folder.
    localeFilters += "en"
  }

  signingConfigs {
    keystoreEntry("debug")?.let { entry ->
      getByName("debug") {
        storeFile = rootProject.file(entry.getValue("StoreFile"))
        storePassword = entry.getValue("StorePassword")
        keyAlias = entry.getValue("KeyAlias")
        keyPassword = entry.getValue("KeyPassword")
        applySigningSchemes()
      }
    }
    keystoreEntry("release")?.let { entry ->
      create("release") {
        storeFile = rootProject.file(entry.getValue("StoreFile"))
        storePassword = entry.getValue("StorePassword")
        keyAlias = entry.getValue("KeyAlias")
        keyPassword = entry.getValue("KeyPassword")
        applySigningSchemes()
      }
    }
  }

  buildTypes {
    getByName("debug") {
      isMinifyEnabled = false
      // See dinfinity.android-library: AGP builds the JaCoCo report for the
      // debug unit tests, so the coverage plugin need not know its layout.
      enableUnitTestCoverage = true
    }
    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), file("proguard-rules.pro"))
      signingConfig = signingConfigs.findByName("release")
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

  testCoverage {
    jacocoVersion = jacocoToolVersion
  }

  lint {
    warningsAsErrors = true
    abortOnError = true
    // Named rather than left to `warningsAsErrors`, so that turning that off
    // one day does not quietly turn this off with it. `HardcodedText` only
    // reads layout XML, though, and every screen here is Compose — the check
    // that covers Kotlin is `verifyTextIsAResource` in `dinfinity.quality`
    // (`docs/architecture.md`, "Text a person reads").
    error += setOf("HardcodedText", "MissingTranslation", "ExtraTranslation")
    // Off, on purpose. `NewerVersionAvailable` asks Maven Central on every run
    // whether anything newer exists, so a dependency publishing a release turns
    // the build red on a commit that changed nothing — tomlj 1.3.0 did exactly
    // that. A build should succeed or fail on what is in the tree.
    //
    // It is also invisible locally: the devcontainer runs Gradle `--offline`,
    // so the detector has no network and says nothing, and `./gradlew check`
    // passes on a tree CI will reject. A check that only fires on one of the
    // two machines is worse than no check.
    //
    // Keeping up to date is Dependabot's, which opens a pull request per bump
    // with the verification metadata regenerated beside it — a version arrives
    // as something to review rather than as a broken build
    // (`docs/build-setup.md`).
    // Both of them: `GradleDependency` is the same question asked by a
    // different detector, and turning one off simply hands the failure to the
    // other. Found by putting tomlj back to 1.2.0 and watching the build fail
    // again under the other name.
    disable += setOf("NewerVersionAvailable", "GradleDependency")
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
    val rename =
      tasks.register<RenameApkTask>("renameApk$capitalised") {
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

configureDeviceTestVerification()

// Every module's unit tests run against the same API, without every module
// having to remember to say so (`docs/build-setup.md`).
generateRobolectricProperties(android)

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
  // collectAsStateWithLifecycle: settings are observed for as long as the
  // activity is actually on screen, and no longer.
  "implementation"(libs.findLibrary("androidx-lifecycle-runtime-compose").get())
  "debugImplementation"(libs.findLibrary("compose-ui-tooling").get())
  "debugImplementation"(libs.findLibrary("compose-ui-test-manifest").get())
  "testImplementation"(bom)
  "testImplementation"(libs.findLibrary("junit4").get())
  "testImplementation"(libs.findLibrary("robolectric").get())
  "testImplementation"(libs.findLibrary("androidx-test-junit").get())
  "testImplementation"(libs.findLibrary("compose-ui-test-junit4").get())
  // The device tier: these run on the Pixel 10a over WiFi debugging, never
  // on CI (.claude/CLAUDE.md).
  "androidTestImplementation"(bom)
  "androidTestImplementation"(libs.findLibrary("junit4").get())
  "androidTestImplementation"(libs.findLibrary("androidx-test-core").get())
  "androidTestImplementation"(libs.findLibrary("androidx-test-junit").get())
  "androidTestImplementation"(libs.findLibrary("androidx-test-runner").get())
  "androidTestImplementation"(libs.findLibrary("androidx-test-espresso-core").get())
  "androidTestImplementation"(libs.findLibrary("compose-ui-test-junit4").get())
}

// Robolectric reaches into JDK internals (java.io.FileDescriptor among others),
// which the module system closes off from Java 17 on. Without these opens every
// Robolectric test fails at start-up with "perhaps JRE has changed?".
val robolectricJvmArgs =
  listOf(
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
