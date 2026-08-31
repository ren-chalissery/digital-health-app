plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.simplicity.training"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.simplicity.training"
        // 26 rather than iOS's 17-equivalent floor: Android's install base is far longer-tailed,
        // and nothing here needs a newer API.
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Amplify refuses to be consumed without it: it uses java.time, which minSdk 26 predates.
        isCoreLibraryDesugaringEnabled = true
    }
}


dependencies {
    implementation(project(":foundation"))
    implementation(project(":api"))
    implementation(project(":design"))
    implementation(project(":services"))
    implementation(project(":auth"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
