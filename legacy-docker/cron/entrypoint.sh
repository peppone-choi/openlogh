#!/bin/sh
set -e
echo '*/2  *  *  *  *  python3 /var/www/html/sam/src/run_daemon.py
1-59/2  *  *  *  *  python3 /var/www/html/sam/src/run_daemon.py' | crontab - && crond -f -L /dev/stdout