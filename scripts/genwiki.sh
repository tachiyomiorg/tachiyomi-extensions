# Programatically generated Extensions wiki
# Github @phanirithvij (https://github.com/phanirithvij)

# NOTE: Run this from tachiyomi-extensions/

# backup wikifile (just in case)
cp Extensions.md Extensions_backup.md
# delete current Extensions.md
echo '' > Extensions.md

TreeURL="https://github.com/inorichi/tachiyomi-extensions/tree/master/src"
BlobURL="https://github.com/inorichi/tachiyomi-extensions/blob/master"

lang_from_langcode(){
    case "$1" in
    "all")
        echo "All Languages"
    ;;
    "en")
        echo "English"
    ;;
    "vi")
        echo "Vietnamese (Tiếng Việt)"
    ;;
    "ru")
        echo "Russian (Pусский)"
    ;;
    "it")
        echo "Italian (Italiano)"
    ;;
    "es")
        echo "Spanish (Español)"
    ;;
    "ko")
        echo "Korean (한국어)"
    ;;
    "ja")
        echo "Japanese (日本語)"
    ;;
    "pt")
        echo "Portuguese (Português)"
    ;;
    "fr")
        echo "French (Français)"
    ;;
    "de")
        echo "German (Deutsch)"
    ;;
    "zh")
        echo "Chinese (中文)"
    ;;
    *)
        echo "NONE"
    ;;
    esac
}

echo -e "# Extensions\n" >> Extensions.md
echo -e "All known issues with available sources and extensions\n" >> Extensions.md

echo -e "**Page under construction**" >> Extensions.md

for langcode in $(ls src/)
do 
    lang=$(lang_from_langcode $langcode)
    echo Language $lang
    echo -e "\n## $lang\n" >> Extensions.md
    echo -e "<table border=\"0\">" >> Extensions.md
    for shrtname in $(ls src/$langcode/)
    do
        # Language
        echo short name $shrtname
        # Title
        name=$(grep 'appName' src/$langcode/$shrtname/build.gradle | cut -d '=' -f2 | awk '{split($0,a,"Tachiyomi:"); print a[2]}')
        name=${name::-1}
        name=${name:1}
        echo Name is $name
        # Version
        code=$(grep 'extVersionCode' src/$langcode/$shrtname/build.gradle | cut -d '=' -f2)
        version=$(grep 'libVersion' src/$langcode/$shrtname/build.gradle | cut -d '=' -f2)
        version=${version::-1}
        version=${version:2}
        version=$version.${code:1}
        echo Version $version
        # cat src/$langcode/$shrtname/build.gradle
        # Image
        image=src/$langcode/$shrtname/res/web_hi_res_512.png
        if [ -f $image ]; then
            echo Image Exists
        else
            echo Using Default Image
            image="res/mipmap-xxxhdpi/ic_launcher.png"
        fi

        # echo -e "**${name:1}** *$version*" >> Extensions.md
        echo -e "\t<tr>" >> Extensions.md
        echo -e "\t\t<td rowspan=\"2\">" >> Extensions.md
        echo -e "\t\t\t<a href=\"$TreeURL/$langcode/$shrtname\">" >> Extensions.md
        echo -e "\t\t\t\t<img src=\"$BlobURL/$image?raw=true\" width=\"60\" alt=\"$name\"></img>" >> Extensions.md
        echo -e "\t\t\t</a>" >> Extensions.md
        echo -e "\t\t</td>" >> Extensions.md
        echo -e "\t\t<td>" >> Extensions.md
        echo -e "\t\t\t<a href=\"$TreeURL/$langcode/$shrtname\"><b>${name}</b></a>" >> Extensions.md
        echo -e "\t\t</td>" >> Extensions.md
        echo -e "\t</tr>" >> Extensions.md
        echo -e "\t<tr>" >> Extensions.md
        echo -e "\t\t<td>" >> Extensions.md
        echo -e "\t\t\t<i>"${version}"</i>" >> Extensions.md
        echo -e "\t\t</td>" >> Extensions.md
        echo -e "\t</tr>" >> Extensions.md
    done
    echo -e "</table>" >> Extensions.md
done
