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
        private fun pkgNameSuffix(source: ThemeSourceData, separator: String): String {
            return if (source is SingleLangThemeSourceData)
                listOf(source.lang, source.pkgName).joinToString(separator)
            else
                listOf("all", source.pkgName).joinToString(separator)
        }

        private fun writeGradle(gradle: File, source: ThemeSourceData, version: Int = 1) {
            gradle.writeText("apply plugin: 'com.android.application'\n" +
                "apply plugin: 'kotlin-android'\n" +
                "\n" +
                "ext {\n" +
                "    extName = '${source.name}'\n" +
                "    pkgNameSuffix = '${pkgNameSuffix(source, ".")}'\n" +
                "    extClass = '.${source.className}'\n" +
                "    extVersionCode = $version\n" +
                "    libVersion = '1.2'\n" +
                if (source.isNsfw) "    containsNsfw = true\n" else "" +
                    "}\n" +
                    "\n" +
                    "apply from: \"\$rootDir/common.gradle\"\n")
        }

        fun createOrUpdateSource(source: ThemeSourceData, themeName: String, userDir: String) {
            val gradlePath = userDir + "/generated-src/${pkgNameSuffix(source, "/")}/"
            val gradleFile = File("$gradlePath/build.gradle")
            val classPath = File("$gradlePath/src/eu/kanade/tachiyomi/extension/${pkgNameSuffix(source, "/")}")
            File(gradlePath).let { file ->
                println("Working on $source")
                // new source
                if (!file.exists()) {
                    file.mkdirs()
                    writeGradle(gradleFile, source)
                    classPath.mkdirs()

                    val srcOverride = File("$userDir/lib/themesources/src/main/java/eu/kanade/tachiyomi/lib/themesources/${themeName.toLowerCase(Locale.ENGLISH)}/src-override/${source.pkgName}")
                    if (srcOverride.exists())
                        srcOverride.copyRecursively(File("$classPath"))
                    else {
                        val classFile = File("$classPath/${source.className}.kt")

                        var classText =
                            "package eu.kanade.tachiyomi.extension.${pkgNameSuffix(source, ".")}\n" +
                                "\n" +
                                "import eu.kanade.tachiyomi.lib.themesources.${themeName.toLowerCase(Locale.ENGLISH)}.$themeName\n"

                        if (source is MultiLangThemeSourceData) {
                            classText += "import eu.kanade.tachiyomi.source.Source\n" +
                                "import eu.kanade.tachiyomi.source.SourceFactory\n"
                        }

                        classText += "\n"

                        if (source is SingleLangThemeSourceData) {
                            classText += "class ${source.className} : $themeName(\"${source.name}\", \"${source.baseUrl}\", \"${source.lang}\")\n"
                        } else {
                            classText +=
                                "class ${source.className} : SourceFactory { \n" +
                                    "    override fun createSources(): List<Source> = listOf(\n"
                            for (lang in (source as MultiLangThemeSourceData).lang)
                                classText += "        $themeName(\"${source.name}\", \"${source.baseUrl}\", \"$lang\"),\n"
                            classText +=
                                "    )\n" +
                                    "}"
                        }

                        classFile.writeText(classText)
                    }

                    // copy res files
                    // check if res override exists if not copy default res
                    val resOverride = File("$userDir/lib/themesources/src/main/java/eu/kanade/tachiyomi/lib/themesources/${themeName.toLowerCase(Locale.ENGLISH)}/res-override/${source.pkgName}")
                    if (resOverride.exists())
                        resOverride.copyRecursively(File("$gradlePath/res"))
                    else

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

        abstract class ThemeSourceData {
            abstract val name: String
            abstract val baseUrl: String
            val isNsfw: Boolean = false
            abstract val className: String
            abstract val pkgName: String
        }

        data class SingleLangThemeSourceData(
            override val name: String,
            override val baseUrl: String,
            val lang: String,
            override val className: String = name.replace(" ", ""),
            override val pkgName: String = className.toLowerCase(Locale.ENGLISH)
        ) : ThemeSourceData()

        data class MultiLangThemeSourceData(
            override val name: String,
            override val baseUrl: String,
            val lang: List<String>,
            override val className: String = name.replace(" ", "") + "Factory",
            override val pkgName: String = name.replace(" ", "").toLowerCase(Locale.ENGLISH)
        ) : ThemeSourceData()
    }
}





