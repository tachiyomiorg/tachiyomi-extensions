[![Travis](https://img.shields.io/travis/inorichi/tachiyomi-extensions.svg)](https://travis-ci.org/inorichi/tachiyomi-extensions)
[![fdroid dev](https://img.shields.io/badge/stable-wiki-blue.svg)](//github.com/inorichi/tachiyomi/wiki/FDroid-for-dev-versions)

This repository contains the available extension catalogues for the [Tachiyomi](https://github.com/inorichi/tachiyomi) app.

# Usage

Extension sources are considered pre-release. They are installed and uninstalled like apps, in .apk format. The plan is to have a UI in the main app, that will enable installing and updating extensions. If you want to try them now regardless, you can use the [Github Repo](//github.com/inorichi/tachiyomi-extensions/tree/repo/apk).

## Requests

Site requests here are meant as up-for-grabs, thus it's impossible to provide a time estimation for any of them. Furthermore, some sites are impossible to do, usually because of various technical reasons.

# Contributing

Before you start, please note that the ability to use following technologies is **required** and it's not possible for us to teach you any of them.
* Kotlin
* JSoup
* HTML
* CSS selectors

## Writing an extension

The easiest way to write and debug an extension is by directly hardcoding it in Tachiyomi's source code. Once it's working there, you have to clone this repository and create a new folder with a similar structure to the other catalogues. Then copy your catalogue implementation and make sure to change the package name if it was different in Tachiyomi. Finally, write the `build.gradle` file, which has the following structure:

```gradle
apply plugin: 'com.android.application'
apply plugin: 'kotlin-android'

ext {
    appName = "Tachiyomi: My catalogue"
    pkgNameSuffix = "lang.mycatalogue"
    extClass = ".MyCatalogue"
    extVersionCode = 1
    extVersionSuffix = 1
    libVersion = "1.0"
}

apply from: '../common.gradle'
```

* `appName` is the name of the Android application. By prefixing it with `Tachiyomi: `, it will be easier to locate with an Android package manager.
* `pkgNameSuffix` has to be unique, and it's added to `eu.kanade.tachiyomi.extension`. The language and the site should be enough. Remember your catalogue code implementation must be placed in this package.
* `extClass` points to the catalogue class. You can use a relative path starting with a dot (the package name is the base path). This is required for Tachiyomi to instantiate the catalogue.
* `extVersionCode` is the version code of the catalogue and should be increased with any change to the implementation.
* `extVersionSuffix` is the last part of the versioning.
* `libVersion` is the version of the [extensions library](https://github.com/inorichi/tachiyomi-extensions-lib)* used. When this value is changed, `extVersionSuffix` should be reset to `1`. With the example used above, the version of the catalogue would be `1.0.1`.

\* Note: this library only contains the method definitions so that the compiler can resolve them. The actual implementation is written in Tachiyomi.

When everything is done, you can create the apk in Android Studio with `Build > Build APK` or `Build > Generate Signed APK`.
