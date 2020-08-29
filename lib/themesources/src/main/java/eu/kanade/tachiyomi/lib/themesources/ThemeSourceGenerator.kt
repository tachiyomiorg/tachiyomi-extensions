package eu.kanade.tachiyomi.lib.themesources

import java.io.File
import java.util.Locale

/**
 * This is meant to be used in place of a factory extension, specifically for what would be a multi-source extension.
 * A multi-lang (but not multi-source) extension should still be made as a factory extensiion.
 * Use a generator for initial setup of a theme source or when all of the inheritors need a version bump.
 * Source list (val sources) should be kept up to date.
 */

interface ThemeSourceGenerator {

    /**
     * The class that the sources inherit from.
     */
    val themeName: String

    /**
     * The list of sources to be created or updated.
     */
    val sources: List<ThemeSourceData>

    fun createOrUpdateAll() {
        val userDir = System.getProperty("user.dir")!!

        sources.forEach { source ->
            createOrUpdateSource(source, themeName, userDir)
        }
    }

    companion object {
        fun pkgNameSuffix(source: ThemeSourceData, separator: String) = listOf(source.lang, source.className.toLowerCase(Locale.ENGLISH)).joinToString(separator)

        private fun writeGradle(gradle: File, source: ThemeSourceData, version: Int = 1) {
            gradle.writeText("apply plugin: 'com.android.application'\n" +
                "apply plugin: 'kotlin-android'\n" +
                "\n" +
                "ext {\n" +
                "    extName = '${source.className}'\n" +
                "    pkgNameSuffix = '${pkgNameSuffix(source, ".")}'\n" +
                "    extClass = '.${source.className}'\n" +
                "    extVersionCode = $version\n" +
                "    libVersion = '1.2'\n" +
                if (source.isNsfw) "    containsNsfw = true\n" else "" +
                    "}\n" +
                    "\n" +
                    "apply from: \"\$rootDir/common.gradle\"")
        }

        fun createOrUpdateSource(source: ThemeSourceData, themeName: String, userDir: String) {
            val gradlePath = userDir + "/src/${pkgNameSuffix(source, "/")}/"
            val gradleFile = File("$gradlePath/build.gradle")
            val classPath = File("$gradlePath/src/eu/kanade/tachiyomi/extension/${pkgNameSuffix(source, "/")}")
            val classFile = File("$classPath/${source.className}.kt")
            File(gradlePath).let { file ->
                println("Working on $source")
                // new source
                if (!file.exists()) {
                    file.mkdirs()
                    writeGradle(gradleFile, source)
                    classPath.mkdirs()
                    classFile.writeText("package eu.kanade.tachiyomi.extension.${pkgNameSuffix(source, ".")}\n" +
                        "\n" +
                        "import eu.kanade.tachiyomi.lib.themesources.$themeName\n" +
                        "\n" +
                        "class ${source.className} : $themeName() {\n" +
                        "\n" +
                        "    override val name = \"${source.name}\"\n" +
                        "    \n" +
                        "    override val baseUrl = \"${source.baseUrl}\"\n" +
                        "    \n" +
                        "    override val lang = \"${source.lang}\"\n" +
                        "}")
                    File("$userDir/lib/themesources/src/main/java/eu/kanade/tachiyomi/lib/themesources/${themeName.toLowerCase(Locale.ENGLISH)}/res").let { res ->
                        if (res.exists()) res.copyRecursively(File("$gradlePath/res"))
                    }
                // update current source
                } else {
                    val version = Regex("""extVersionCode = (\d+)""").find(gradleFile.readText())!!.groupValues[1].toInt() + 1
                    writeGradle(gradleFile, source, version)
                }
            }
        }

        data class ThemeSourceData(
            val name: String,
            val baseUrl: String,
            val lang: String,
            val className: String = name.replace(" ", ""),
            val isNsfw: Boolean = false
        )
    }
}





