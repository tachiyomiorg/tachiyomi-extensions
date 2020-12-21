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

    compileOnly("com.github.salomonbrys.kotson:kotson:2.5.0")
    compileOnly(project(":duktape-stub"))
}
apply("$rootDir/common-dependencies.gradle")



tasks.register("runAllGenerators") {
    doLast {
        // android sdk dir is not documented but this hidden api gets it
        // ref: https://stackoverflow.com/questions/20203787/access-sdk-dir-value-in-build-gradle-after-project-evaluation
        val androidSDK = "${android.sdkDirectory.absolutePath}"

        val projectRoot = rootProject.projectDir

        var classPath = ""
        classPath += "$androidSDK/platforms/android-29/android.jar:"
        classPath += "$androidSDK/platforms/android-29/data/res:"
        classPath += "$projectRoot/multisrc/build/intermediates/javac/debug/classes:"
        classPath += "$projectRoot/multisrc/build/intermediates/compile_r_class_jar/debug/R.jar:"
        classPath += "$projectRoot/multisrc/build/tmp/kotlin-classes/debug:"
        classPath += "$projectRoot/multisrc/build/generated/res/resValues/debug"

        configurations.debugCompileOnly.asFileTree.forEach { classPath = "$classPath:$it" }

        val javaPath = System.getProperty("java.home") + "/bin/java"

        val mainClass = "eu.kanade.tachiyomi.multisrc.GeneratorMain"

        Runtime.getRuntime().exec("$javaPath -classpath $classPath $mainClass")
    }
}