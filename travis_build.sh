#! /usr/bin/env sh

git submodule update --init
cd async/
git checkout de3c0b61184b37525ca8dca829290fa19d2aca3a
cd ..
sbt ";project SprawlerDeadLinksDemo ;test"