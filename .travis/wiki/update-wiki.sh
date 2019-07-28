#!/bin/bash

set -e

# Skip for PRs
# if [ ! "$TRAVIS_PULL_REQUEST" = "false" ]; then
#     echo "Skipping wiki generation"
#     exit 0
# fi

# Clone wiki repo
rm -rf tachiyomi-extensions.wiki
git clone "https://${GITHUB_TOKEN}@github.com/inorichi/tachiyomi-extensions.wiki.git"

# Generate file
echo -n "" > tachiyomi-extensions.wiki/Extensions.md
bash ./.travis/wiki/generator.sh >> tachiyomi-extensions.wiki/Extensions.md

# Commit updated file
cd tachiyomi-extensions.wiki
git add Extensions.md
git commit -m "Travis build: $TRAVIS_BUILD_NUMBER"

# Push to wiki repo
git remote add origin-wiki "https://${GITHUB_TOKEN}@github.com/inorichi/tachiyomi-extensions.wiki.git"
git push --quiet --set-upstream origin-wiki master