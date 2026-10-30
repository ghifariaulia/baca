import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing comes from a git-ignored keystore.properties in the repo
// root (storeFile, storePassword, keyAlias, keyPassword); without it the
// release APK is built unsigned.
val keystoreProperties = rootProject.file("keystore.properties").takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }

android {
    namespace = "com.baca.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.baca.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
    }

    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.sqldelight.android.driver)
    implementation(project(":composeApp"))
    implementation(project(":shared"))
}
