rm -rf tachiyomi.wiki
git clone https://github.com/inorichi/tachiyomi.wiki.git

bash scripts/genwiki.sh
cp Extensions.md tachiyomi.wiki/
cd tachiyomi.wiki

git add .
git commit -m "Updated The extensions wiki"
git push

# cd ..
# rm -rf tachiyomi.wiki