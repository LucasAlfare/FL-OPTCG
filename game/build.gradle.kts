plugins {
  id("org.jetbrains.kotlin.multiplatform") version "2.3.0"
  id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
}

kotlin {
  jvmToolchain(25)
  jvm()
}

dependencies {
  commonMainImplementation("com.github.LucasAlfare:FL-Turns:1.0.0")
  commonMainImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}