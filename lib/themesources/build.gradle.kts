plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    compileSdkVersion(Config.compileSdk)
    buildToolsVersion(Config.buildTools)

    defaultConfig {
        minSdkVersion(Config.minSdk)
        targetSdkVersion(Config.targetSdk)
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