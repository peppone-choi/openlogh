#!/bin/bash
set -euxo pipefail
shopt -s nullglob

cp -n /usr/local/etc/php/php.ini-production /usr/local/etc/php/php.ini
ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
printf "[PHP]\ndate.timezone = \"$TZ\"\n" > /usr/local/etc/php/conf.d/tzone.ini


wwwRoot="/var/www"
bbsRoot="$wwwRoot/board"

if [ ! -e "$bbsRoot/index.php" ]; then
    mkdir -p $bbsRoot
    chown -R www-data:www-data $bbsRoot
    gosu www-data git clone https://github.com/rhymix/rhymix.git $bbsRoot
fi

exec "$@"