#!/bin/bash

set -e

TreeURL="https://github.com/inorichi/tachiyomi-extensions/tree/master/src"
BlobURL="https://github.com/inorichi/tachiyomi-extensions/blob/master"

echoerr() { echo -e "$@" 1>&2; }

lang_from_langcode(){
    case "$1" in
    "all")
        echo -e "Multiple languages"
    ;;
    "de")
        echo -e "German (Deutsch)"
    ;;
    "en")
        echo -e "English"
    ;;
    "es")
        echo -e "Spanish (Español)"
    ;;
    "fr")
        echo -e "French (Français)"
    ;;
    "id")
        echo -e "Indonesian (bahasa Indonesia)"
    ;;
    "it")
        echo -e "Italian (Italiano)"
    ;;
    "ja")
        echo -e "Japanese (日本語)"
    ;;
    "ko")
        echo -e "Korean (한국어)"
    ;;
    "pt")
        echo -e "Portuguese (Português)"
    ;;
    "ru")
        echo -e "Russian (Pусский)"
    ;;
    "vi")
        echo -e "Vietnamese (Tiếng Việt)"
    ;;
    "zh")
        echo -e "Chinese (中文)"
    ;;
    *)
        echo -e ""
    ;;
    esac
}

for langcode in $(ls src/ | sort)
do 
    # Language
    lang=$(lang_from_langcode $langcode)
    echoerr "Language $lang"

    echo -e "<details>"
    echo -e "<summary>$langcode - <strong>$lang</strong></summary>"
    echo -e "<table>"

    for pkg in $(ls src/$langcode/)
    do
        # Title
        name=$(grep 'appName' src/$langcode/$pkg/build.gradle | cut -d '=' -f2 | awk '{split($0,a,"Tachiyomi:"); print a[2]}')
        name=${name%?}
        name=${name:1}

        # Version
        code=$(grep 'extVersionCode' src/$langcode/$pkg/build.gradle | cut -d '=' -f2)
        version=$(grep 'libVersion' src/$langcode/$pkg/build.gradle | cut -d '=' -f2)
        version=${version%?}
        version=${version:2}
        version=$version.${code:1}

        # Icon
        icon=src/$langcode/$pkg/res/mipmap-xxxhdpi/ic_launcher.png
        if [ ! -f $icon ]; then
            icon="res/mipmap-xxxhdpi/ic_launcher.png"
        fi

        echoerr "$name $version"

        echo -e "  <tr>"
        echo -e "    <td rowspan=\"2\">"
        echo -e "      <a href=\"$TreeURL/$langcode/$pkg\">"
        echo -e "        <img src=\"$BlobURL/$icon?raw=true\" width=\"60\" alt=\"$name\">"
        echo -e "      </a>"
        echo -e "    </td>"
        echo -e "    <td>"
        echo -e "      <a href=\"$TreeURL/$langcode/$pkg\"><strong>${name}</strong></a>"
        echo -e "    </td>"
        echo -e "  </tr>"
        echo -e "  <tr>"
        echo -e "    <td>"
        echo -e "      <em>"${version}"</em>"
        echo -e "    </td>"
        echo -e "  </tr>"
    done

    echo -e "</table>"
    echo -e "</details>"
done
