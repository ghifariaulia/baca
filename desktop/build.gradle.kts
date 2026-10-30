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
        // the bundled runtime only has the JDK modules listed here (suggestRuntimeModules)
        modules("java.instrument", "java.sql", "jdk.unsupported")
        // the book formats nothing else is likely to want: "Open with Baca" and double-click for these
        fileAssociation("application/epub+zip", "epub", "EPUB book")
        fileAssociation("application/x-fictionbook+xml", "fb2", "FictionBook")
        fileAssociation("application/vnd.comicbook+zip", "cbz", "Comic book archive")
        packageVersion = "0.1.0"
        macOS {
            // macOS rejects app versions whose first number is 0
            packageVersion = "1.0.0"
            iconFile.set(project.file("icons/baca.icns"))
        }
        windows {
            iconFile.set(project.file("icons/baca.ico"))
        }
        linux {
            iconFile.set(project.file("icons/baca.png"))
        }
    }
}
