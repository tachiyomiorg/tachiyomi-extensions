include(":annotations")
include(":core")

include(":lib-ratelimit")
project(":lib-ratelimit").projectDir = File("lib/ratelimit")

include(":duktape-stub")
project(":duktape-stub").projectDir = File("lib/duktape-stub")

include(":lib-dataimage")
project(":lib-dataimage").projectDir = File("lib/dataimage")

include(":multisrc")
project(":multisrc").projectDir = File("multisrc")

// Loads all extensions
File(rootDir, "src").eachDir { dir ->
    dir.eachDir { subdir ->
        val name = ":${dir.name}-${subdir.name}"
        include(name)
        project(name).projectDir = File("src/${dir.name}/${subdir.name}")
    }
}
// Loads generated extensions from multisrc
File(rootDir, "generated-src").eachDir { dir ->
    dir.eachDir { subdir ->
        val name = ":${dir.name}-${subdir.name}"
        include(name)
        project(name).projectDir = File("generated-src/${dir.name}/${subdir.name}")
    }
}

// Use this to load a single extension during development
// val lang = "all"
// val name = "mmrcms"
// include(":${lang}-${name}")
// project(":${lang}-${name}").projectDir = File("src/${lang}/${name}")

inline fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}
