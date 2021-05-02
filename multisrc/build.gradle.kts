import java.io.BufferedReader
import java.io.InputStreamReader

plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    compileSdkVersion(Config.compileSdk)
    buildToolsVersion(Config.buildTools)

    defaultConfig {
        minSdkVersion(29)
        targetSdkVersion(Config.targetSdk)
    }
}

repositories {
    mavenCentral()
}

// dependencies
apply("$rootDir/common-dependencies.gradle")

tasks {
    val generateExtensions by registering {
        doLast {
            val isWindows = System.getProperty("os.name").toString().toLowerCase().contains("win")
            var classPath = (configurations.debugCompileOnly.get().asFileTree.toList() +
                listOf(
                    configurations.androidApis.get().asFileTree.first().absolutePath, // android.jar path
                    "$projectDir/build/intermediates/aar_main_jar/debug/classes.jar" // jar made from this module
                ))
                .joinToString(if (isWindows) ";" else ":")

            var javaPath = "${System.getProperty("java.home")}/bin/java"

            val mainClass = "generator.GeneratorMainKt" // Main class we want to execute

            if (isWindows) {
                classPath = classPath.replace("/", "\\")
                javaPath = javaPath.replace("/", "\\")
            }

            val javaProcess = ProcessBuilder()
                .directory(null).command(javaPath, "-classpath", classPath, mainClass)
                .redirectErrorStream(true).start()

            val inputStreamReader = InputStreamReader(javaProcess.inputStream)
            val bufferedReader = BufferedReader(inputStreamReader)

            var s: String?
            while (bufferedReader.readLine().also { s = it } != null) {
                logger.info(s)
            }

            bufferedReader.close()
            inputStreamReader.close()

            val exitCode = javaProcess.waitFor()
            if (exitCode != 0) {
                throw Exception("Java process failed with exit code: $exitCode")
            }
        }
        dependsOn("assembleDebug")
    }
}
