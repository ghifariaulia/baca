import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.sqldelight.sqlite.driver)
    implementation(project(":composeApp"))
    implementation(project(":shared"))
}

compose.desktop.application {
    mainClass = "com.baca.desktop.MainKt"

    nativeDistributions {
        targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
        packageName = "Baca"
        packageVersion = "0.1.0"
    }
}
