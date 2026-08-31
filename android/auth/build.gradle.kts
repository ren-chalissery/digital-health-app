plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.simplicity.training.auth"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


dependencies {
    api(project(":foundation"))
    implementation(libs.amplify.core)
    implementation(libs.amplify.core.kotlin)
    implementation(libs.amplify.auth.cognito)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
