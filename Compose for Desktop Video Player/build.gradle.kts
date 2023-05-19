import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform") version Version.kotlin
    id("org.jetbrains.compose") version Version.compose
    id("com.github.ben-manes.versions") version "0.46.0"
}

group = "de.appsonair.compose"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

kotlin {
    jvm {
        jvmToolchain(11)
        withJava()
    }
}

