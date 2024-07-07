import org.jetbrains.compose.compose
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose") version Version.compose
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.bytedeco:ffmpeg-platform:${Version.ffmpeg}")
}

compose.desktop {
    application {
        mainClass = "de.appsonair.ui.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "VideoPlayerSample"
            packageVersion = "1.0.0"
        }
    }
}