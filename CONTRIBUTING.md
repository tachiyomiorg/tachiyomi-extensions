# Contributing

## Prerequisites

Before you start, please note that the ability to use following technologies is **required** and that existing contributors will not actively teach them to you.

- Basic [Android development](https://developer.android.com/)
- [Kotlin](https://kotlinlang.org/)
- Web scraping
    - [HTML](https://developer.mozilla.org/en-US/docs/Web/HTML)
    - [CSS selectors](https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_Selectors)
    - [OkHttp](https://square.github.io/okhttp/)
    - [JSoup](https://jsoup.org/)

### Tools

- [Android Studio](https://developer.android.com/studio)
- Emulator or phone with developer options enabled and a recent version of Tachiyomi installed

## Getting help
- Join [Tachiyomi's discord server](https://discord.gg/tachiyomi) for online help and to ask questions while developing your extension.
- There are some features and tricks that are not explored in this document. Refer to the [app's repository](https://github.com/inorichi/tachiyomi), [extensions library](https://github.com/tachiyomiorg/extensions-lib), [lib directory](https://github.com/inorichi/tachiyomi-extensions/tree/master/lib) and other exsiting extension code for more info.

## Writing an extension

The quickest way to get started is to copy an existing extension's folder structure and renaming it as needed. Of course, that also means that there's plenty of existing extensions that you can reference as you go!

### Setting up a new Gradle module

Make sure that your new extension's `build.gradle` file follows the following structure:

```gradle
apply plugin: 'com.android.application'
apply plugin: 'kotlin-android'

ext {
    appName = 'Tachiyomi: My source name'
    pkgNameSuffix = 'lang.mysourcename'
    extClass = '.MySourceName'
    extVersionCode = 1
    libVersion = '1.2'
}

apply from: "$rootDir/common.gradle"
```

| Field | Description |
| ----- | ----------- |
| `appName` | The name of the Android application. Should be prefixed with `Tachiyomi: `. |
| `pkgNameSuffix` | A unique suffix added to `eu.kanade.tachiyomi.extension`. The language and the site name should be enough. Remember your extension code implementation must be placed in this package. |
| `extClass` | Points to the class that implements `Source`. You can use a relative path starting with a dot (the package name is the base path). This is used to find and instantiate the source(s). |
| `extVersionCode` | The extension version code. This must be a positive integer and incremented with any change to the code. |
| `libVersion` | The version of the [extensions library](https://github.com/tachiyomiorg/extensions-lib) used. |

The extension's version name is based off of `libVersion` and `extVersionCode`. With the example used above, the version of the catalogue would be `1.2.1`.

### Core dependencies

#### Extension API

Extensions rely on [extensions-lib](https://github.com/tachiyomiorg/extensions-lib), which provides some interfaces and stubs from the [app](https://github.com/inorichi/tachiyomi) for compilation purposes. The actual implementations can be found [here](https://github.com/inorichi/tachiyomi/tree/dev/app/src/main/java/eu/kanade/tachiyomi/source). Referencing the actual implementation will help with understanding extensions' call flow.

#### Duktape stub

[`duktape-stub`](https://github.com/inorichi/tachiyomi-extensions/tree/master/lib/duktape-stub) provides stubs for using Duktape functionality without pulling in the full library. Functionality is bundled into the main Tachiyomi app.

```
dependencies {
    compileOnly project(':duktape-stub')
}
```

#### Rate limiting library

[`lib-ratelimit`](https://github.com/inorichi/tachiyomi-extensions/tree/master/lib/ratelimit) is a library for adding rate limiting functionality as an [OkHttp interceptor](https://square.github.io/okhttp/interceptors/).

```
dependencies {
    implementation project(':lib-ratelimit')
}
```

#### Additional dependencies

You may find yourself needing additional functionality and wanting to add more dependencies to your `build.gradle` file. Since extensions are run within the main Tachiyomi app, you can make use of [its dependencies](https://github.com/inorichi/tachiyomi/blob/master/app/build.gradle).

For example, an extension that needs Gson could add the following:

```
dependencies {
    compileOnly 'com.google.code.gson:gson:2.8.2'
}
```

(Note that Gson, and several other dependencies, are already exposed to all extensions via `common.gradle`.)

Notice that we're using `compileOnly` instead of `implementation`, since the app already contains it. You could use `implementation` instead for a new dependency, or you prefer not to rely on whatever the main app has at the expense of app size.

Note that using `compileOnly` restricts you to versions that must be compatible with those used in Tachiyomi v0.8.5+ for proper backwards compatibility.

### Extension call flow

#### Main class

The class which is refrenced and defined by `extClass` in `build.gradle`.

This class should implement either `SourceFactory` or one of the `Source` implementations: `HttpSource` or `ParsedHttpSource`.
    - `SourceFactory`: used to expose multiple `Source`s. Use it when there's minor differences between your target sources or they are essentially mirrors to the same website.
    - `HttpSource`: for online source, where requests are made using HTTP.
    - `ParsedHttpSource`: similar to `HttpSource`, but has methods useful for scraping pages.

Key variables:

| Field | Description |
| ----- | ----------- |
| `name` | Name displayed in the "Sources" tab in Tachiyomi. |
| `baseUrl` | Base URL of the source without any trailing slashes. |
| `lang` | An ISO 639-1 compliant language code (two letters in lower case). |
| `id` | Identifier of your source, automatically set in `HttpSource`. It should only be manually overriden if you need to copy an existing autogenerated ID. |
 
#### Popular Manga

a.k.a. the "Browse" source entry point in the app.

- The app calls `fetchPopularManga` with `page=1`, and it returns a `MangasPage` and will continue to call it for next pages, when the user scrolls the manga list and more results must be fetched (until you pass `MangasPage.hasNextPage` as `false` which marks the end of the found manga list).
- While passing magnas here you should at least set `url`, `title` and `thumbnail_url`.
    - If `thumbnail_url` is not set, `fetchMangaDetails` will be called.

#### Latest Manga

a.k.a. the "Latest" source entry point in the app.

- Used if `supportsLatest` is `true` for a source
- Similar to popular manga, but should be fetching the latest items from a source.

#### Manga Search

- `getFilterList` will be called to get all filters and filter types. **TODO: explain more about `Filter`**
- When the user searches inside the app, `fetchSearchManga` will be called and the rest of the flow is similar to what happens with `fetchPopularManga`.
- if search functionality is not available, return `Observable.just(MangasPage(emptyList(), false))`

#### Manga Details

- When user taps on a manga, `fetchMangaDetails` and `fetchChapterList` will be called and the results will be cached.
- `fetchMangaDetails` is called to update a manga's details from when it was initialized earlier
    - Note: During a backup, only `url` and `title` are stored, and to restore the rest of the manga data the app calls `fetchMangaDetails`, so all fields should be filled in if possible.
- `fetchChapterList` is called to display the chapter list. This should be sorted descending by date.
- `SManga.initialized` tells the app if it should call `fetchMangaDetails`, if you are overriding `fetchMangaDetails`, make sure to pass it as `true`.

#### Chapter

- After a chapter list for the manga is fetched, `prepareNewChapter` will be called.

#### Chapter Pages

- When user opens a chapter, `fetchPageList` will be called and it will return a list of `Page`s.
- While a chapter is open the reader will call `fetchImageUrl` to get URLs for each page of the manga.
- chapter pages are are started from `0`.
- if `Page.imageUrl`s are available immediately, you should pass them here, otherwise(and you can set `page.url` to a empty string. otherwise, you have to override `imageUrlParse` to fill those imageUrls.

### Misc notes

- Sometimes you may find no use for some inherited methods. If so just override them and throw exceptions: `throw UnsupportedOperationException("Not used.")`
- You probably will find `getUrlWithoutDomain` useful when parsing the target source URLs.
- If possible try to stick to the general workflow from `HttpSource`/`ParsedHttpSource`; breaking them may cause you more headache than necessary.
- by implementing `ConfigurableSource` you can add settings to your source, you'd probably want to use Android's `SharedPreferences`in the code to store your settings.(documentation [here](https://developer.android.com/reference/android/content/SharedPreferences)) 

## Running

To aid in local development, you can use the following run configuration to launch an extension:

![](https://i.imgur.com/STy0UFY.png)

If you're running a Preview or debug build of Tachiyomi:

```
-W -S -n eu.kanade.tachiyomi.debug/eu.kanade.tachiyomi.ui.main.MainActivity -a eu.kanade.tachiyomi.SHOW_CATALOGUES
```

And for a release build of Tachiyomi:

```
-W -S -n eu.kanade.tachiyomi/eu.kanade.tachiyomi.ui.main.MainActivity -a eu.kanade.tachiyomi.SHOW_CATALOGUES
```

### Debugging

Directly debugging your extension (i.e steping through the extension code) is not possible due to the way that extension code is loaded into the app. However, logs printed from extensions (via [`Logcat`](https://developer.android.com/studio/debug/am-logcat)) do work.


## Building

APKs can be created in Android Studio via `Build > Build Bundle(s) / APK(s) > Build APK(s)` or `Build > Generate Signed Bundle / APK`.
