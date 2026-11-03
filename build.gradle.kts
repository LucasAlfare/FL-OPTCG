plugins {
  id("org.jetbrains.kotlin.multiplatform") version "2.3.0" apply false
}

subprojects {
  group = "com.lucasalfare"
  version = "1.0-SNAPSHOT"

  repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
  }
}