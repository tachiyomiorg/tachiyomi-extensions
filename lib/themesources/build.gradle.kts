plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    compileSdkVersion(AndroidConfig.compileSdk)
    buildToolsVersion(AndroidConfig.buildTools)

    defaultConfig {
        minSdkVersion(AndroidConfig.minSdk)
        targetSdkVersion(AndroidConfig.targetSdk)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(Deps.kotlin.stdlib)
    compileOnly(Deps.okhttp)
    compileOnly(Deps.jsoup)
    compileOnly("com.github.tachiyomiorg:extensions-lib:a596412")
}
