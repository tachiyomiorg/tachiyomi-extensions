# Contributing

Before you start, please note that the ability to use following technologies is **required** and it's not possible for us to teach you any of them.

* Kotlin
* JSoup
* HTML
* CSS selectors


## Writing an extension

The quickest way to get started is to copy an existing extension's folder structure and renaming it as needed. Of course, that also means that there's plenty of existing extensions that you can reference as you go!

Make sure that your new extension's `build.gradle` file follows the following structure:

```gradle
apply plugin: 'com.android.application'
apply plugin: 'kotlin-android'

ext {
    appName = 'Tachiyomi: My catalogue'
    pkgNameSuffix = 'lang.mycatalogue'
    extClass = '.MyCatalogue'
    extVersionCode = 1
    libVersion = '1.2'
}

apply from: "$rootDir/common.gradle"
```

| Field | Description |
| ----- | ----------- |
| `appName` | The name of the Android application. By prefixing it with `Tachiyomi: `, it will be easier to locate with an Android package manager. |
| `pkgNameSuffix` | A unique suffic added to `eu.kanade.tachiyomi.extension`. The language and the site name should be enough. Remember your catalogue code implementation must be placed in this package. |
| `extClass` | Points to the catalogue class. You can use a relative path starting with a dot (the package name is the base path). This is required for Tachiyomi to instantiate the catalogue. |
| `extVersionCode` | The version code of the catalogue. This must be increased with any change to the implementation and cannot be `0`. |
| `libVersion` | The version of the [extensions library](https://github.com/inorichi/tachiyomi-extensions-lib)* used. |

The catalogue's version name is based off of `libVersion` and `extVersionCode`. With the example used above, the version of the catalogue would be `1.2.1`.

\* Note: this library only contains the method definitions so that the compiler can resolve them. The actual implementation is written in Tachiyomi.


## Running

To aid in local development, you can use the following run configuration to launch an extension:

![](https://i.imgur.com/STy0UFY.png)

If you're running a dev/debug build of Tachiyomi:

```
-W -S -n eu.kanade.tachiyomi.debug/eu.kanade.tachiyomi.ui.main.MainActivity -a eu.kanade.tachiyomi.SHOW_CATALOGUES
```

And for a release build of Tachiyomi:

```
-W -S -n eu.kanade.tachiyomi/eu.kanade.tachiyomi.ui.main.MainActivity -a eu.kanade.tachiyomi.SHOW_CATALOGUES
```


## Building

APKs can be created in Android Studio via `Build > Build Bundle(s) / APK(s) > Build APK(s)` or `Build > Generate Signed Bundle / APK`.
