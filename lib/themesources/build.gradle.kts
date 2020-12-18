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
    compileOnly(project(":annotations"))
    compileOnly(project(":defaultRes"))

    // Lib 1.2, but using specific commit so we don't need to bump up the version
    compileOnly("com.github.tachiyomiorg:extensions-lib:a596412")

    // These are provided by the app itself
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${Deps.kotlin.version}")

    compileOnly("com.github.inorichi.injekt:injekt-core:65b0440")
    compileOnly("com.squareup.okhttp3:okhttp:3.10.0")
    compileOnly("io.reactivex:rxjava:1.3.6")
    compileOnly("org.jsoup:jsoup:1.10.2")
    compileOnly("com.google.code.gson:gson:2.8.2")
    compileOnly("com.github.salomonbrys.kotson:kotson:2.5.0")
    compileOnly(project(":duktape-stub"))
}


tasks.register("runAllGenerators") {
    doLast {
        // android sdk dir is not documented but this hidden api gets it
        // ref: https://stackoverflow.com/questions/20203787/access-sdk-dir-value-in-build-gradle-after-project-evaluation
        val androidSDK = "${android.sdkDirectory.absolutePath}"

        val projectRoot = rootProject.projectDir

        var classPath = ""
        classPath += "$androidSDK/platforms/android-29/android.jar:"
        classPath += "$androidSDK/platforms/android-29/data/res:"
        classPath += "$projectRoot/lib/themesources/build/intermediates/javac/debug/classes:"
        classPath += "$projectRoot/lib/themesources/build/intermediates/compile_r_class_jar/debug/R.jar:"
        classPath += "$projectRoot/lib/themesources/build/tmp/kotlin-classes/debug:"
        classPath += "$projectRoot/lib/themesources/build/generated/res/resValues/debug"

        configurations.debugCompileOnly.asFileTree.forEach { classPath = "$classPath:$it" }

        val javaPath = System.getProperty("java.home") + "/bin/java"

        val mainClass = "eu.kanade.tachiyomi.lib.themesources.GeneratorMain"

        Runtime.getRuntime().exec("$javaPath -classpath $classPath $mainClass")
    }
}