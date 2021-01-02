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

    directories.forEach { themeSource ->
        // find all XxxGenerator.kt fgailes
        val generatorClasses = File("$sourcesDirPath/$themeSource").list()!!.filter {
            it.endsWith("Generator.kt")
        }

        // invoke main methods
        generatorClasses.forEach {
            val generatorClassPath = "eu/kanade/tachiyomi/multisrc/$themeSource/$it".replace("/", ".").substringBefore(".kt")
            Class.forName(generatorClassPath).methods.forEach {
                if (it.name == "main")
                    it.invoke(null, emptyArray<String>())
            }
        }
    }
}

