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

- Join [the Discord server](https://discord.gg/tachiyomi) for online help and to ask questions while developing your extension.
- There are some features and tricks that are not explored in this document. Refer to existing extension code for examples.

## Writing an extension

The quickest way to get started is to copy an existing extension's folder structure and renaming it as needed. We also recommend reading through a few existing extensions' code before you start.

### Setting up a new Gradle module

Each extension should reside in `src/<lang>/<mysourcename>`. Use `all` as `<lang>` if your target source supports multiple languages or if it could support multiple sources.

#### Extension file structure

The simplest extension structure looks like this:

```console
$ tree src/<lang>/<mysourcename>/
src/<lang>/<mysourcename>/
├── build.gradle
├── res
│   ├── mipmap-hdpi
│   │   └── ic_launcher.png
│   ├── mipmap-mdpi
│   │   └── ic_launcher.png
│   ├── mipmap-xhdpi
│   │   └── ic_launcher.png
│   ├── mipmap-xxhdpi
│   │   └── ic_launcher.png
│   ├── mipmap-xxxhdpi
│   │   └── ic_launcher.png
│   └── web_hi_res_512.png
└── src
    └── eu
        └── kanade
            └── tachiyomi
                └── extension
                    └── <lang>
                        └── <mysourcename>
                            └── <MySourceName>.kt

13 directories, 8 files
```

#### build.gradle
Make sure that your new extension's `build.gradle` file follows the following structure:

```gradle
apply plugin: 'com.android.application'
apply plugin: 'kotlin-android'

ext {
    extName = '<My source name>'
    pkgNameSuffix = '<lang>.<mysourcename>'
    extClass = '.<MySourceName>'
    extVersionCode = 1
    libVersion = '1.2'
    containsNsfw = true
}

apply from: "$rootDir/common.gradle"
```

| Field | Description |
| ----- | ----------- |
| `extName` | The name of the extension. |
| `pkgNameSuffix` | A unique suffix added to `eu.kanade.tachiyomi.extension`. The language and the site name should be enough. Remember your extension code implementation must be placed in this package. |
| `extClass` | Points to the class that implements `Source`. You can use a relative path starting with a dot (the package name is the base path). This is used to find and instantiate the source(s). |
| `extVersionCode` | The extension version code. This must be a positive integer and incremented with any change to the code. |
| `libVersion` | The version of the [extensions library](https://github.com/tachiyomiorg/extensions-lib) used. |
| `containsNsfw` | (Optional, defaults to `false`) Flag to indicate that a source contains NSFW content. |

The extension's version name is generated automatically by concatenating `libVersion` and `extVersionCode`. With the example used above, the version would be `1.2.1`.

### Core dependencies

#### Extension API

Extensions rely on [extensions-lib](https://github.com/tachiyomiorg/extensions-lib), which provides some interfaces and stubs from the [app](https://github.com/inorichi/tachiyomi) for compilation purposes. The actual implementations can be found [here](https://github.com/inorichi/tachiyomi/tree/dev/app/src/main/java/eu/kanade/tachiyomi/source). Referencing the actual implementation will help with understanding extensions' call flow.

#### Duktape stub

[`duktape-stub`](https://github.com/inorichi/tachiyomi-extensions/tree/master/lib/duktape-stub) provides stubs for using Duktape functionality without pulling in the full library. Functionality is bundled into the main Tachiyomi app.

```gradle
dependencies {
    compileOnly project(':duktape-stub')
}
```

#### Rate limiting library

[`lib-ratelimit`](https://github.com/inorichi/tachiyomi-extensions/tree/master/lib/ratelimit) is a library for adding rate limiting functionality as an [OkHttp interceptor](https://square.github.io/okhttp/interceptors/).

```gradle
dependencies {
    implementation project(':lib-ratelimit')
}
```

#### DataImage library

[`lib-dataimage`](https://github.com/inorichi/tachiyomi-extensions/tree/master/lib/dataimage) is a library for handling [base 64 encoded image data](https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/Data_URIs) using an [OkHttp interceptor](https://square.github.io/okhttp/interceptors/).

```gradle
dependencies {
    implementation project(':lib-dataimage')
}
```

#### Additional dependencies

You may find yourself needing additional functionality and wanting to add more dependencies to your `build.gradle` file. Since extensions are run within the main Tachiyomi app, you can make use of [its dependencies](https://github.com/inorichi/tachiyomi/blob/master/app/build.gradle).

For example, an extension that needs Gson could add the following:

```gradle
dependencies {
    compileOnly 'com.google.code.gson:gson:2.8.2'
}
```

(Note that Gson, and several other dependencies, are already exposed to all extensions via `common.gradle`.)

Notice that we're using `compileOnly` instead of `implementation`, since the app already contains it. You could use `implementation` instead for a new dependency, or you prefer not to rely on whatever the main app has at the expense of app size.

Note that using `compileOnly` restricts you to versions that must be compatible with those used in [Tachiyomi v0.8.5+](https://github.com/inorichi/tachiyomi/blob/82141cec6e612885fef4fa70092e29e99d60adbb/app/build.gradle#L104) for proper backwards compatibility.

### Extension main class

The class which is refrenced and defined by `extClass` in `build.gradle`. This class should implement either `SourceFactory` or one of the `Source` implementations: `HttpSource` or `ParsedHttpSource`.

| Class | Description |
| ----- | ----------- |
|`SourceFactory`| Used to expose multiple `Source`s. Use it when there's minor differences between your target sources or they are essentially mirrors to the same website. |
| `HttpSource`| For online source, where requests are made using HTTP. |
| `ParsedHttpSource`| Similar to `HttpSource`, but has methods useful for scraping pages. |

#### Main class key variables

| Field | Description |
| ----- | ----------- |
| `name` | Name displayed in the "Sources" tab in Tachiyomi. |
| `baseUrl` | Base URL of the source without any trailing slashes. |
| `lang` | An ISO 639-1 compliant language code (two letters in lower case). |
| `id` | Identifier of your source, automatically set in `HttpSource`. It should only be manually overriden if you need to copy an existing autogenerated ID. |


### Extension call flow

#### Popular Manga

a.k.a. the Browse source entry point in the app (invoked by tapping on the source name).

- The app calls `fetchPopularManga` which should return a `MangasPage` containing the first batch of found `SManga` entries.
    - This method supports pagination. When user scrolls the manga list and more results must be fetched, the app calls it again with increasing `page` values(starting with `page=1`). This continues until you pass `MangasPage.hasNextPage` as `false` which marks the end of the found manga list.
- To show the list properly, the app needs `url`, `title` and `thumbnail_url`. You must set them here. The rest of the fields could be filled later.(refer to Manga Details below)
    - If `thumbnail_url` is not avialable, you could not set it, in this case `fetchMangaDetails` will be **immediately** called. To fetch the info.

#### Latest Manga

a.k.a. the Latest source entry point in the app (invoked by tapping on the "Latest" button beside the source name).

- Used if `supportsLatest` is `true` for a source
- Similar to popular manga, but should be fetching the latest entries from a source.

#### Manga Search

- When the user searches inside the app, `fetchSearchManga` will be called and the rest of the flow is similar to what happens with `fetchPopularManga`.
    - If search functionality is not available, return `Observable.just(MangasPage(emptyList(), false))`
- `getFilterList` will be called to get all filters and filter types. **TODO: explain more about `Filter`**

#### Manga Details

- When user taps on a manga, `fetchMangaDetails` and `fetchChapterList` will be called and the results will be cached.
    - A `SManga` entry is identified by it's `url`.
- `fetchMangaDetails` is called to update a manga's details from when it was initialized earlier.
    - During a backup, only `url` and `title` are stored. To restore the rest of the manga data, the app calls `fetchMangaDetails`, so all fields should be (re)filled in if possible.
    - `SManga.initialized` tells the app if it should call `fetchMangaDetails`. If you are overriding `fetchMangaDetails`, make sure to pass it as `true`.
    - If a `SManga` is cached and you update the details in `fetchPopularManga`(or similar methods), details it won't change in the database(and thumbnails won't update). updates to database are only done after `fetchMangaDetails` is called.
    - If a `SManga` is cached `fetchMangaDetails` will be only called when the user does a manual update(Swipe-to-Refresh).
- `fetchChapterList` is called to display the chapter list.
    - The list should be sorted descending by date/chapter number.
    - If `Page.imageUrl`s are available immediately, you should pass them here. Otherwise, you should set `page.url` to a page that contains them and override `imageUrlParse` to fill those `imageUrl`s.

#### Chapter

- After a chapter list for the manga is fetched and the app is going to cache the data, `prepareNewChapter` will be called.

#### Chapter Pages

- When user opens a chapter, `fetchPageList` will be called and it will return a list of `Page`s.
- While a chapter is open in the reader or is being downloaded, `fetchImageUrl` will be called to get URLs for each page of the manga.
- Chapter pages numbers start from `0`.

### Misc notes

- Sometimes you may find no use for some inherited methods. If so just override them and throw exceptions: `throw UnsupportedOperationException("Not used.")`
- You probably will find `getUrlWithoutDomain` useful when parsing the target source URLs.
- If possible try to stick to the general workflow from `HttpSource`/`ParsedHttpSource`; breaking them may cause you more headache than necessary.
- By implementing `ConfigurableSource` you can add settings to your source, which is backed by [`SharedPreferences`](https://developer.android.com/reference/android/content/SharedPreferences).

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

## Debugging

Directly debugging your extension (i.e stepping through the extension code) is not possible due to the way that extension code is loaded into the app. However, logs printed from extensions (via [`Logcat`](https://developer.android.com/studio/debug/am-logcat)) do work.


## Building

APKs can be created in Android Studio via `Build > Build Bundle(s) / APK(s) > Build APK(s)` or `Build > Generate Signed Bundle / APK`.
