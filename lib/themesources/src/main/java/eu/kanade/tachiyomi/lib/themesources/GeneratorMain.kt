package eu.kanade.tachiyomi.lib.themesources

import java.io.File

class GeneratorMain {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val userDir = System.getProperty("user.dir")!!
            val sourcesDirPath = "$userDir/lib/themesources/src/main/java/eu/kanade/tachiyomi/lib/themesources"
            val sourcesDir = File(sourcesDirPath)

            val directories = sourcesDir.list().filter {
                File(sourcesDir, it).isDirectory
            }

            directories.forEach { themeSource ->
                val generatorClasses = File("$sourcesDirPath/$themeSource").list().filter {
                    it.endsWith("Generator.kt")
                }

                generatorClasses.forEach {
                    val generatorClassPath = "eu/kanade/tachiyomi/lib/themesources/$themeSource/$it".replace("/", ".").substringBefore(".kt")
                    Class.forName(generatorClassPath).methods.forEach {
                        if (it.name == "main")
                            it.invoke(null, emptyArray<String>())
                    }
                }
            }
        }
    }
}
