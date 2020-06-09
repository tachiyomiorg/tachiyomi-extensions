# Contributing

Before you start, please note that the ability to use following technologies is **required** and it's not possible for us to teach you any of them.

* [Kotlin](https://kotlinlang.org/)
* [JSoup](https://jsoup.org/)
* [HTML](https://developer.mozilla.org/en-US/docs/Web/HTML)
* [CSS selectors](https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_Selectors)


## Writing an extension

The quickest way to get started is to copy an existing extension's folder structure and renaming it as needed. Of course, that also means that there's plenty of existing extensions that you can reference as you go!

### Setting up a module

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
| `pkgNameSuffix` | A unique suffix added to `eu.kanade.tachiyomi.extension`. The language and the site name should be enough. Remember your catalogue code implementation must be placed in this package. |
| `extClass` | Points to the catalogue class. You can use a relative path starting with a dot (the package name is the base path). This is required for Tachiyomi to instantiate the catalogue. |
| `extVersionCode` | The version code of the catalogue. This must be increased with any change to the implementation and cannot be `0`. |
| `libVersion` | The version of the [extensions library](https://github.com/inorichi/tachiyomi-extensions-lib)* used. |

The catalogue's version name is based off of `libVersion` and `extVersionCode`. With the example used above, the version of the catalogue would be `1.2.1`.

\* Note: this library only contains the method definitions so that the compiler can resolve them. The actual implementation is written in Tachiyomi.

### Additional dependencies

You may find yourself needing additional functionality and wanting to add more dependencies to your `build.gradle` file. Since extensions are run within the main Tachiyomi app, you can make use of [its dependencies](https://github.com/inorichi/tachiyomi/blob/master/app/build.gradle).

For example, an extension that needs Gson could add the following:

```
dependencies {
    compileOnly 'com.google.code.gson:gson:2.8.2'
}
```

Notice that we're using `compileOnly` instead of `implementation`, since the app already contains it. You could use `implementation` instead, if it's a new dependency, or you prefer not to rely on whatever the main app has (at the expense of app size).

### Core stubs and libraries

#### Extensions library

Extensions rely on stubs defined in [tachiyomi-extensions-lib](https://github.com/tachiyomiorg/extensions-lib), which simply provides some interfaces for compiling extensions. These interfaces match what's found in the main Tachiyomi app. The exact version used is configured with `libVersion`. The latest version should be preferred.


#### Duktape stub

[`duktape-stub`](https://github.com/inorichi/tachiyomi-extensions/tree/master/lib/duktape-stub) provides stubs for using Duktape functionality without pulling in the full library. Functionality is bundled into the main Tachiyomi app.

```
dependencies {
    compileOnly project(':duktape-stub')
}
```

#### Rate limiting library

[`lib-ratelimit`](https://github.com/inorichi/tachiyomi-extensions/tree/master/lib/ratelimit) is a library for adding rate limiting functionality.

```
dependencies {
    implementation project(':lib-ratelimit')
}
```

### Useful knowledge
- The bridge between the app and you extension is the [extension-lib](https://github.com/tachiyomiorg/extensions-lib), but it only contains stubs and the actual implementations are in the [app](https://github.com/inorichi/tachiyomi) inside `eu.kanade.tachiyomi.source` package which you can find [here](https://github.com/inorichi/tachiyomi/tree/dev/app/src/main/java/eu/kanade/tachiyomi/source), reading the code inside there will help you in writing your extension.
- You are encouraged to clone the app itself and make your own debug build so you can attach android-studio to the app, then you can print logs in the code and debug your code from inside `Logcat`, directly debugging your extension(steeping through your extension code) is not possible, but if you keep both projects open in android studio, you can debug the app itself.
- Your `extClass`(inside `build.gradle`) class should be inherited from either `SourceFactory` or one of `Source` children: `CatalogueSource` or `HttpSource` or `ParsedHttpSource`. you shouldn't inherit from `CatalogueSource` unless you know what you are doing.
- `HttpSource` as in it's name is for a online http(s) source, but `ParsedHttpSource` has a good model of work which makes writing scrapers for normal aggregator websites much easier and streamlined. (again, you can find the implementation of the stubs in the app as mentioned above)  

### Important disclaimer before you continue!
The structure for an extension is very strict.  In the future 1.x release this will be less strict but until then this has caused some issues when some sites don't quite fit the model.  There are required overrides but you can override the calling methods if you need more general control. This will go from the highest level method to the lowest level for browse/popular, it is the same but different method names for search and latest.

## general guidelines and extension workflow
- The app starts by finding your extension and reads these variables:

| Field | Description |
| ----- | ----------- |
| `name` | Name of the source as displayed in the `sources` tab inside the app |
| `id` | identifier of your source, automatically set from `HttpSource`, don't touch it | 
| `supportsLatest` | if `true` the app adds a `latest` button to your extension |
| `baseUrl` | base URL of the target source without any trailing slashes |
| `lang` | as the documentation says "An ISO 639-1 compliant language code (two letters in lower case).", it will be used to catalog you extension |
 
- **Notes**
    - Some time during the code of you may find yourself finding no use for some inherited methods, if so just override them and throw exceptions: `throw Exception("Not used")`
    - You probably will find `getUrlWithoutDomain` useful when parsing the target source URLs.
    - If possible try to stick to the general workflow from`ParsedHttpSource` and `HttpSource` breaking them may cause you more headache than necessary.
    -  When reading the code documentation it helps to follow the subsequent called methods in the the default implementation from the `app`, while trying to grasp the general workflow.
    - Set the thumbnail cover when possible.  When parsing the list of manga during latest, search, browse.  If not the site will get a new request for every manga that doesn't have a cover shown,  even if the user doesnt click into the manga.
- **Popular Manga**
    - When user presses on the source name or the `Browse` button on the sources tab, the app calls `fetchPopularManga` with `page=1`,  and it returns a `MangasPage` and will continue to call it for next pages, when the user scrolls the manga list and more results must be fetched(until you pass `MangasPage.hasNextPage` as `false` which marks the end of the found manga list)
    - While passing magnas here you should at least set `url`, `title` and `thumbnail_url`; `url` must be unique since it's used to index mangas in the DataBase.(this information will be cached and you will have a chance to update them when `fetchMangaDetails` is called later).
- **Latest Manga**
    - If `supportsLatest` is set to true the app shows a `Latest` button in front for your extension `name` and when the user taps on it, the app will call `fetchLatestUpdates` and the rest of the flow is similar to what happens with `fetchPopularManga`.
    - If `supportsLatest` is set to false no `Latest` button will be shown and `fetchLatestUpdates` and subsequent methods will never be called.
- **Manga Search**
    - `getFilterList` will be called to get all filters and filter types. **TODO: explain more about `Filter`**
    - when the user searches inside the app, `fetchSearchManga` will be called and the rest of the flow is similar to what happens with `fetchPopularManga`.
- **Manga Details**
    - When user taps on a manga and opens it's information Activity `fetchMangaDetails` and `fetchChapterList` will be called the resulting information will be cached.
    - `fetchMangaDetails` is called to update a manga's details from when it vas initialized earlier(you may want to parse a manga details page here and fill the rest of the fields here)
   - `fetchChapterList` is called to display the chapter list, you want to return a reversed list here(last chapter, first index in the list)
- **Chapter**
    - After a chapter list for the manga is fetched, `prepareNewChapter` will be called, after that the chapter will be saved in the app's DataBase and later if the chapter list changes the app will loose any references to the chapter(but chapter files will still be in the device storage)
- **Chapter Pages**
    - When user opens a chapter, `fetchPageList` will be called and it will return a list of `Page`
    - While a chapter is open the reader will call `fetchImageUrl` to get URLs for each page of the manga


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
