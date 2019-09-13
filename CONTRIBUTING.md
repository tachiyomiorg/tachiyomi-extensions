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

Notice that we're using `compileOnly` instead of `implementation`, since the app already contains it. You could use `implementation` instead, if it's a new dependency, or you prefer not to rely on whatever the main app has (at the expensive of app size).

### Core stubs and libraries

#### Extensions library

Extensions rely on stubs defined in [tachiyomi-extensions-lib](https://github.com/inorichi/tachiyomi-extensions-lib), which simply provides some interfaces for compiling extensions. These interfaces match what's found in the main Tachiyomi app. The exact version used is configured with `libVersion`. The latest version should be preferred.

#### Preference stub

[`preference-stub`](https://github.com/inorichi/tachiyomi-extensions/tree/master/lib/preference-stub) provides the [`ConfigurableSource` interface](https://github.com/inorichi/tachiyomi-extensions/blob/master/lib/preference-stub/src/main/java/eu/kanade/tachiyomi/source/ConfigurableSource.java) for extensions, as well as stubs for Android preferences.

```
dependencies {
    compileOnly project(':preference-stub')
}
```

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

- An extension should at least extend the [`ParsedHttpSource`](https://github.com/inorichi/tachiyomi-extensions-lib/blob/master/library/src/main/java/eu/kanade/tachiyomi/source/online/ParsedHttpSource.kt) class.
- Do not override id!!  it will auto generate based on the name of the extension and the language
- set the thumbnail cover when possible.  when parsing the list of manga during latest, search, browse.  if not the site will get a new request for every manga that doesnt have a cover shown.  even if the user doesnt click into the manga.



#### Flow of the extensions
The structure for an extension is very strict.  In the future 1.x release this will be less strict but until then this has caused some issues when some sites don't quite fit the model.  There are required overrides but you can override the calling methods if you need more general control. This will go from highest level method to lowest level for browse/popular, it is the same but different method names for search and latest.
##### Browse (Aka Popular Manga)
- fetchPopularManga (Optional to override)
    - This method takes the results from a manga listing page and parses it.
- popularMangaRequest (Must be overridden)
   - The GET/POST for the HTML Page of the manga listings  
- popularMangaParse (Optional to override)
   - parses the manga listing page returns boolean if has another page, and the manga objects as MangasPage
- popularMangaSelector (must be overridden)
    - jsoup css selector to select the list of the manga
- popularMangaFromElement (must be overriden)
    - jsoup selectors to parse the individual manga html on the page (most sites this is just link, title, cover url)
- popularMangaNextPageSelector (must be overridden)
   - jsoup css selector to see if there is a another page after current one
       
 This will provide the initial viewing once a user clicks into a manga you will need to override mangaDetailsParse and this is where you need to parse the actual manga site's manga page and parse the standard info (title, author, description etc etc)
 
 ###### Note: 
 Must be overriden are required to be overridden even if you override the parent method and its not being called anymore.  (for example i override popularMangaParse and dont need popularMangaNextPage selector  I would just override in the extension and throw a not used exception)


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
