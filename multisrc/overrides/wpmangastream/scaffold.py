import os, sys
from pathlib import Path

theme = Path(os.getcwd()).parts[-1]

print(f"Detected theme: {theme}")

if len(sys.argv) < 3:
    print("must be called with a class name and lang")
    exit(-1)

source = sys.argv[1]
package = source.lower()
lang = sys.argv[2]

if lang == "all": factory = "Factory"
else: factory = ""

print(f"working on {source} with lang {lang}")

os.makedirs(f"{package}/src")

with open(f"{package}/src/{source}{factory}.kt", "w") as f:
    f.write(f"package eu.kanade.tachiyomi.extension.{lang}.{package}\n\n")
    f.write(f"import eu.kanade.tachiyomi.multisrc.{theme}.{theme.capitalize()}\n")
    f.write(f"import eu.kanade.tachiyomi.network.GET\n")
    f.write(f"import eu.kanade.tachiyomi.network.POST\n")
    f.write(f"import eu.kanade.tachiyomi.source.Source\n")
    f.write(f"import eu.kanade.tachiyomi.source.SourceFactory\n")
    f.write(f"import eu.kanade.tachiyomi.source.model.FilterList\n")
    f.write(f"import eu.kanade.tachiyomi.source.model.Page\n")
    f.write(f"import eu.kanade.tachiyomi.source.model.SChapter\n")
    f.write(f"import eu.kanade.tachiyomi.source.model.SManga\n")
    f.write(f"import okhttp3.CacheControl\n")
    f.write(f"import okhttp3.Headers\n")
    f.write(f"import okhttp3.OkHttpClient\n")
    f.write(f"import okhttp3.Request\n")
    f.write(f"import okhttp3.Response\n")
    f.write(f"import org.jsoup.nodes.Document\n")
    f.write(f"import org.jsoup.nodes.Element\n")
    f.write(f"import java.text.SimpleDateFormat\n")
    f.write(f"import java.util.Locale\n")
    f.write(f"import java.util.concurrent.TimeUnit\n\n") 
