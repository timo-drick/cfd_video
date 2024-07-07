import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}
dependencies {
    implementation(compose.desktop.currentOs)
    implementation("uk.co.caprica:vlcj:4.8.3")
}


compose.desktop {
    application {
        mainClass = "de.appsonair.compose.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "video-lib-vlcj"
            packageVersion = "1.0.0"
        }
    }
}
