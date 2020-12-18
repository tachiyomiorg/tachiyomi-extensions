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