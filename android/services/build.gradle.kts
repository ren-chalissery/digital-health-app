plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.simplicity.training.services"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


dependencies {
    api(project(":api"))
    implementation(libs.hilt.android)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.retrofit.kotlinx.serialization)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
