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

// The convention plugins carry one piece of real logic — the scan behind
// `verifyTextIsAResource` — and a check nobody tested is a check that passes
// everything. `check` here runs these, and the root build's `check` runs
// `ktlintCheckConventions` and `test` in this build for the same reason.
dependencies {
  testImplementation(libs.junit4)
}

tasks.withType<Test>().configureEach {
  useJUnit()
  testLogging {
    events("failed")
  }
}

// The convention plugins are linted by the ktlint *CLI* rather than its Gradle
// plugin. The plugin lints whole source sets, and Gradle generates its plugin
// accessors into this build's main source set — tens of thousands of
// violations in code nobody wrote. Neither a path filter nor overriding the
// tasks' source kept it off them. The CLI takes explicit patterns instead, so
// it sees the hand-written files and nothing else.
val ktlintCli: Configuration = configurations.create("ktlintCli")

dependencies {
  ktlintCli(libs.ktlint.cli)
}

val ktlintCheckConventions =
  tasks.register<JavaExec>("ktlintCheckConventions") {
    group = "verification"
    description = "Runs ktlint over the convention plugins."
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    // ktlint resolves .editorconfig from the working directory upwards, and this
    // is a separate build, so it finds build-logic/.editorconfig.
    workingDir = layout.projectDirectory.asFile
    args("src/main/kotlin/**/*.kt", "src/main/kotlin/**/*.kts", "*.kts")
  }

val ktlintFormatConventions =
  tasks.register<JavaExec>("ktlintFormatConventions") {
    group = "formatting"
    description = "Fixes what ktlint can fix in the convention plugins."
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    workingDir = layout.projectDirectory.asFile
    args("--format", "src/main/kotlin/**/*.kt", "src/main/kotlin/**/*.kts", "*.kts")
  }

tasks.named("check") {
  dependsOn(ktlintCheckConventions)
}
