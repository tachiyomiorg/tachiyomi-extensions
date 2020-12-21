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
        var classPath = ""
        classPath += configurations.androidApis.get().asFileTree.first().absolutePath + ":" // android.jar path
        classPath += "$projectDir/build/intermediates/aar_main_jar/debug/classes.jar:"

        configurations.debugCompileOnly.get().asFileTree.forEach { classPath = "$classPath:$it" }

        val javaPath = System.getProperty("java.home") + "/bin/java"

        val mainClass = "eu.kanade.tachiyomi.multisrc.GeneratorMain"

        Runtime.getRuntime().exec("$javaPath -classpath $classPath $mainClass")
    }
}
