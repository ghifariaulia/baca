plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.baca.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.baca.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
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
