plugins {
  id("org.jetbrains.kotlin.multiplatform") version "2.3.0"
  id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
}

kotlin {
  jvmToolchain(25)
  jvm()

  sourceSets {
    commonMain.dependencies {
      implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    }
    jvmMain.dependencies {
      implementation("com.github.LucasAlfare:FL-Turns:1.0.0")
      implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    }
  }
}