#!/bin/bash
set -euxo pipefail
shopt -s nullglob

cp -n /usr/local/etc/php/php.ini-production /usr/local/etc/php/php.ini
ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
printf "[PHP]\ndate.timezone = \"$TZ\"\n" > /usr/local/etc/php/conf.d/tzone.ini

wwwRoot="/var/www"
samRoot="$wwwRoot/html/sam"

# Source is mounted via docker-compose volume, skip git clone
if [ -f "$samRoot/index.php" ]; then
    echo "Source already mounted at $samRoot"
    # Install PHP dependencies if needed
    if [ ! -d "$samRoot/vendor" ]; then
        pushd $samRoot
        gosu www-data php composer.phar install --no-dev
        popd
    fi
    # Install Node dependencies and build if needed
    if [ ! -d "$samRoot/node_modules" ]; then
        pushd $samRoot
        gosu www-data npm install
        gosu www-data npx webpack --mode=production
        popd
    fi
fi

exec "$@"
