package eu.kanade.tachiyomi.multisrc

import java.io.File

/**
 * Finds and calls all `ThemeSourceGenerator`s
 */

fun main(args: Array<String>) {
    val userDir = System.getProperty("user.dir")!!
    val sourcesDirPath = "$userDir/multisrc/src/main/java/eu/kanade/tachiyomi/multisrc"
    val sourcesDir = File(sourcesDirPath)

    val directories = sourcesDir.list()!!.filter {
        File(sourcesDir, it).isDirectory
    }

    // find all theme packages
    directories.forEach { themeSource ->
        // find all XxxGenerator.kt files and invoke main from them
        File("$sourcesDirPath/$themeSource").list()!!
            .filter {
                it.endsWith("Generator.kt")
            }.map {// find java class and extract method lists
                Class.forName("eu/kanade/tachiyomi/multisrc/$themeSource/$it".replace("/", ".").substringBefore(".kt")).methods.asList()
            }
            .flatten()
            .filter { it.name == "main" }
            .forEach { it.invoke(null, emptyArray<String>()) }
    }
}


