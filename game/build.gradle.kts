plugins {
  id("org.jetbrains.kotlin.multiplatform") version "2.3.0"
}

kotlin {
  jvmToolchain(25)
  jvm()
}