FROM php:8.3-fpm-bookworm
#debian

ENV LC_ALL=C.UTF-8

RUN apt -y update ;\
    apt -y install \
    bzip2 zip unzip p7zip-full git rsync \
    libmemcached-dev libcurl4-openssl-dev \
    libicu-dev libjpeg-dev libpng-dev libwebp-dev libavif-dev libfreetype6-dev \
    libzip-dev libxml2-dev libsqlite3-dev gosu build-essential;

RUN debMultiarch="$(dpkg-architecture --query DEB_BUILD_MULTIARCH)"; \
    docker-php-ext-configure gd --with-freetype --with-webp --with-avif --with-jpeg --with-png ; \
    docker-php-ext-configure pdo_mysql --with-pdo-mysql=mysqlnd; \
    docker-php-ext-configure mysqli --with-mysqli=mysqlnd; \
    docker-php-ext-install -j "$(nproc)" \
        exif \
        gd \
        intl \
        opcache \
        pcntl \
        pdo_mysql \
        mysqli \
        zip;
RUN pecl install ds;
#from nextcloud setting
RUN mkdir -p /var/www/board; \
    mkdir -p /var/www/.ssh;

COPY entrypoint.sh /usr/local/bin/

VOLUME /var/www/board

ARG UID=33
ARG GID=33

RUN if [ "$UID" != 33 ]; then  \
      usermod -u $UID -o www-data; \
    fi; \
    if [ "$GID" != 33 ]; then  \
      groupmod -g $GID -o www-data; \
    fi; \
    chmod +x /usr/local/bin/entrypoint.sh; \
    chown -R www-data:www-data /var/www; \
    chmod -R g=u /var/www; \
    mkdir -p /var/www/.ssh; \
    chown www-data:www-data /var/www/.ssh;

COPY sam_conf.ini /usr/local/etc/php/conf.d/sam_conf.ini

ENTRYPOINT ["entrypoint.sh"]
CMD ["php-fpm"]