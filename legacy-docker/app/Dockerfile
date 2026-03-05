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
        sysvmsg \
        sysvsem \
        sysvshm \
        pcntl \
        pdo_mysql \
        mysqli \
        zip;
RUN pecl install ds;

RUN curl -fsSL https://deb.nodesource.com/setup_lts.x | bash - && \
    apt install -yq nodejs
RUN npm install -g npm

#from nextcloud setting
RUN mkdir -p /var/www/html/;  \
    mkdir -p /var/www/.ssh;

COPY entrypoint.sh /usr/local/bin/
COPY id_ecdsa known_hosts /var/www/.ssh/
VOLUME /var/www/html

ARG UID=33
ARG GID=33

RUN if [ "$UID" != 33 ]; then  \
      usermod -u $UID -o www-data; \
    fi; \
    if [ "$GID" != 33 ]; then  \
      groupmod -g $GID -o www-data; \
    fi; \
    echo $UID > /var/tmp.txt ; \
    chmod +x /usr/local/bin/entrypoint.sh; \
    mkdir -p /var/www/opcache; \
    chown -R www-data:www-data /var/www; \
    chmod -R g=u /var/www; \
    mkdir -p /var/www/.ssh; \
    chown www-data:www-data /var/www/.ssh; \
    chmod 700 /var/www/.ssh; \
    chmod 600 /var/www/.ssh/*;

COPY sam_conf.ini /usr/local/etc/php/conf.d/sam_conf.ini

# SETUP PHP-FPM CONFIG SETTINGS (max_children / max_requests)
RUN echo 'pm.max_children = 15' >> /usr/local/etc/php-fpm.d/zz-docker.conf && \
    echo 'pm.max_requests = 500' >> /usr/local/etc/php-fpm.d/zz-docker.conf && \
     echo 'request_terminate_timeout = 600' >> /usr/local/etc/php-fpm.d/zz-docker.conf

ENTRYPOINT ["entrypoint.sh"]
CMD ["php-fpm"]