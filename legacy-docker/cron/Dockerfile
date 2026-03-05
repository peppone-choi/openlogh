FROM python:3-alpine
#alpine + python3 + cron(from alpine)

#only cron this
COPY        entrypoint.sh /
RUN         chmod +x /entrypoint.sh
ENTRYPOINT  ["/entrypoint.sh"]