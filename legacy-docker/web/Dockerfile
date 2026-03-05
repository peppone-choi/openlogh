FROM nginx:alpine
#debian

ARG UID=33
ARG GID=33

RUN if [ "$UID" != 33 ]; then  \
      apk add shadow; \
      usermod -u $UID -o nginx; \
    fi; \
    if [ "$GID" != 33 ]; then  \
      apk add shadow; \
      groupmod -g $GID -o nginx; \
    fi;

COPY ./default.conf /etc/nginx/conf.d/default.conf
COPY ./sam.conf /etc/nginx/snippets/sam.conf
COPY ./rhymix.conf /etc/nginx/snippets/rhymix.conf

VOLUME /var/www/html
VOLUME /var/www/board
EXPOSE 80