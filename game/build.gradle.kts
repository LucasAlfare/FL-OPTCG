plugins {
  id("org.jetbrains.kotlin.multiplatform") version "2.3.0"
}

kotlin {
  jvmToolchain(25)
  jvm()
}

dependencies {
  commonMainImplementation("com.github.LucasAlfare:FL-Turns:1.0.0")
}