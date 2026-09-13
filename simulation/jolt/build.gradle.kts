plugins {
  id("dinfinity.android-library")
}

val ndk = providers.gradleProperty("dinfinity.ndk").get()
val cmakeVersion = providers.gradleProperty("dinfinity.cmake").get()

android {
  namespace = "de.drehtuer.dinfinity.simulation.jolt"
  ndkVersion = ndk

  defaultConfig {
    ndk {
      // The two ABIs the app is actually run on: `arm64-v8a` is the phone and
      // every Android device that matters, `x86_64` is the emulator in the
      // devcontainer. Both, because "identical outcomes for identical seeds
      // across JVM, emulator and device" is a release blocker and cannot be
      // checked on an ABI that is not built (`docs/TODO.md`, Step 5.2).
      abiFilters += listOf("arm64-v8a", "x86_64")
    }
  }

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = cmakeVersion
    }
  }
}

dependencies {
  api(project(":simulation:api"))

  testImplementation(project(":test-fixtures"))
}
