// A subproject, not the standalone build the generator emits.
//
// The generated build.gradle carries its own buildscript, wrapper and maven-publish because
// openapi-generator assumes it produces a whole project. Included that way it fights the root
// build, so it is listed in .openapi-generator-ignore and replaced by this.
//
// Plain Kotlin JVM rather than an Android library: Retrofit and kotlinx.serialization need no
// Android APIs. That also means nothing Android-specific can live here — it belongs in :api.

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.retrofit)
    api(libs.retrofit.kotlinx.serialization)
    api(libs.okhttp)
    api(libs.okhttp.logging)
    api(libs.retrofit.scalars)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
}
