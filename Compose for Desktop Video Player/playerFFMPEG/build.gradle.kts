import org.jetbrains.compose.compose
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose") version Version.compose
}

repositories {
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.bytedeco:ffmpeg-platform:6.0-1.5.9-SNAPSHOT")
    //implementation("org.bytedeco:ffmpeg-platform-gpl:6.0-1.5.9-SNAPSHOT")
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "VideoPlayerSample"
            packageVersion = "1.0.0"
        }
    }
}