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
tasks.register("runAllGenerators2") {
    doLast {
//        project.android.sourceSets.forEach {
//            println(it)
//        }
//        android.sourceSets.forEach {
//            println(it)
//        }
//
//        println(project.android.bootClasspath)
//        println(android.sourceSets.javaClass.name)
//        android.sourceSets.forEach { println(it.javaClass.name) }
//        configurations.debugCompileOnly.forEach {print(it)}
//        android.bootClasspath
    }
//    configurations.runtimeOnly.get().allDependencies.forEach{println(it)}
//    configurations.forEach{
//        try {
//            if (it.isCanBeResolved) {
//                println(it)
////        it.asFileTree
//                it.asFileTree.forEach { println(it) }
//                println()
//                println()
//            }
//        }
//        catch(e: Exception){}
//    }
//    configurations.debugCompileOnly.get().asFileTree.forEach { println(it) }
//    configurations.androidApis.get().asFileTree.first().let { println(it) }
    println(projectDir)

}
//
//task("execute", JavaExec::class) {
//    main = "eu.kanade.tachiyomi.multisrc.GeneratorMain"
//    classpath = files("$buildDir/build/tmp/kotlin-classes/debug") + configurations.debugCompileOnly.get().asFileTree + configurations.androidApis.get().asFileTree
//}