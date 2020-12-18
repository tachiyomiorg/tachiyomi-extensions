package eu.kanade.tachiyomi.lib.themesources

import java.io.File
import java.util.*

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
    val themeClass: String

    /**
     * The package that contains themeClass.
     */
    val themePkg: String


    /**
     * Base source version is added by source.overrideVersionCode to calculate extVersionCode for build.gradle
     */
    val baseVersionCode: Int

    /**
     * The list of sources to be created or updated.
     */
    val sources: List<ThemeSourceData>

    fun createAll() {
        val userDir = System.getProperty("user.dir")!!

        sources.forEach { source ->
            createSource(source, themePkg, themeClass, baseVersionCode, userDir)
        }
    }

    companion object {
        private fun pkgNameSuffix(source: ThemeSourceData, separator: String): String {
            return if (source is SingleLangThemeSourceData)
                listOf(source.lang, source.pkgName).joinToString(separator)
            else
                listOf("all", source.pkgName).joinToString(separator)
        }

        private fun writeGradle(gradle: File, source: ThemeSourceData, baseVersionCode: Int) {
            gradle.writeText("apply plugin: 'com.android.application'\n" +
                "apply plugin: 'kotlin-android'\n" +
                "\n" +
                "ext {\n" +
                "    extName = '${source.name}'\n" +
                "    pkgNameSuffix = '${pkgNameSuffix(source, ".")}'\n" +
                "    extClass = '.${source.className}'\n" +
                "    extVersionCode = ${baseVersionCode + source.overrideVersionCode}\n" +
                "    libVersion = '1.2'\n" +
                if (source.isNsfw) "    containsNsfw = true\n" else "" +
                    "}\n" +
                    "\n" +
                    "apply from: \"\$rootDir/common.gradle\"\n")
        }

        /**
         * Clears directory recursively
         */
        private fun purgeDirectory(dir: File) {
            for (file in dir.listFiles()!!) {
                if (file.isDirectory) purgeDirectory(file)
                file.delete()
            }
        }

        fun createSource(source: ThemeSourceData, themePkg: String, themeClass: String, baseVersionCode: Int, userDir: String) {
            val sourceRootPath = userDir + "/generated-src/${pkgNameSuffix(source, "/")}"
            val gradleFile = File("$sourceRootPath/build.gradle")
            val classPath = File("$sourceRootPath/src/eu/kanade/tachiyomi/extension/${pkgNameSuffix(source, "/")}")
            val overridesPath = "$userDir/lib/themesources/overrides"
            val resOverridePath = "$overridesPath/res/$themePkg"
            val srcOverridePath = "$overridesPath/src/$themePkg}"


            File(sourceRootPath).let { file ->
                println("Working on $source")

                file.mkdirs()
                purgeDirectory(file)

                writeGradle(gradleFile, source, baseVersionCode)
                classPath.mkdirs()

                val srcOverride = File("$srcOverridePath/${source.pkgName}")
                if (srcOverride.exists())
                    srcOverride.copyRecursively(File("$classPath"))
                else
                    writeSourceClass(classPath, source, themePkg, themeClass)


                // copy res files
                // check if res override exists if not copy default res
                val resOverride = File("$resOverridePath/${source.pkgName}")
                if (resOverride.exists())
                    resOverride.copyRecursively(File("$sourceRootPath/res"))
                else
                    File("$resOverridePath/default").let { res ->
                        if (res.exists()) res.copyRecursively(File("$sourceRootPath/res"))
                    }
            }
        }

        private fun writeSourceClass(classPath: File, source: ThemeSourceData, themePkg: String, themeClass: String) {
            val classFile = File("$classPath/${source.className}.kt")

            var classText =
                "package eu.kanade.tachiyomi.extension.${pkgNameSuffix(source, ".")}\n" +
                    "\n" +
                    "import eu.kanade.tachiyomi.lib.themesources.$themePkg.$themeClass\n"

            if (source is MultiLangThemeSourceData) {
                classText += "import eu.kanade.tachiyomi.source.Source\n" +
                    "import eu.kanade.tachiyomi.source.SourceFactory\n"
            }

            classText += "\n"

            if (source is SingleLangThemeSourceData) {
                classText += "class ${source.className} : $themeClass(\"${source.name}\", \"${source.baseUrl}\", \"${source.lang}\")\n"
            } else {
                classText +=
                    "class ${source.className} : SourceFactory { \n" +
                        "    override fun createSources(): List<Source> = listOf(\n"
                for (lang in (source as MultiLangThemeSourceData).lang)
                    classText += "        $themeClass(\"${source.name}\", \"${source.baseUrl}\", \"$lang\"),\n"
                classText +=
                    "    )\n" +
                        "}"
            }

            classFile.writeText(classText)
        }

        abstract class ThemeSourceData {
            abstract val name: String
            abstract val baseUrl: String
            abstract val isNsfw: Boolean
            abstract val className: String
            abstract val pkgName: String
            abstract val overrideVersionCode: Int
        }

        data class SingleLangThemeSourceData(
            override val name: String,
            override val baseUrl: String,
            val lang: String,
            override val isNsfw: Boolean = false,
            override val className: String = name.replace(" ", ""),
            override val pkgName: String = className.toLowerCase(Locale.ENGLISH),
            override val overrideVersionCode: Int = 0,
        ) : ThemeSourceData()

        data class MultiLangThemeSourceData(
            override val name: String,
            override val baseUrl: String,
            val lang: List<String>,
            override val isNsfw: Boolean = false,
            override val className: String = name.replace(" ", "") + "Factory",
            override val pkgName: String = name.replace(" ", "").toLowerCase(Locale.ENGLISH),
            override val overrideVersionCode: Int = 0,
        ) : ThemeSourceData()
    }
}





