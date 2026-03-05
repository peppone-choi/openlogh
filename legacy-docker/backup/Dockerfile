FROM python:3-alpine
#alpine + python3 + cron(from alpine) + mariadb-client
RUN apk add --no-cache mariadb-client rsync

COPY run_backup.py /usr/local/bin/
RUN chmod +x /usr/local/bin/run_backup.py && \
    echo '0 5 * * * python3 /usr/local/bin/run_backup.py' > /etc/crontabs/root

ENTRYPOINT []
CMD ["crond", "-f" , "-L", "/dev/stdout"]