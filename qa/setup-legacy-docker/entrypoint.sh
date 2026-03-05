#!/bin/bash
set -euo pipefail

echo "[entrypoint] MariaDB dependency is managed by docker-compose healthcheck."

# Create additional game databases if they don't exist (legacy expects up to 8)
echo "[entrypoint] Ensuring game databases exist..."
for i in $(seq 1 7); do
    mysql --ssl=0 -h "$DB_HOST" -P "$DB_PORT" -u root -p"$DB_ROOT_PASSWORD" -e \
        "CREATE DATABASE IF NOT EXISTS sammo_game${i} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null || true
    mysql --ssl=0 -h "$DB_HOST" -P "$DB_PORT" -u root -p"$DB_ROOT_PASSWORD" -e \
        "GRANT ALL PRIVILEGES ON sammo_game${i}.* TO '${DB_USER}'@'%';" 2>/dev/null || true
done
mysql --ssl=0 -h "$DB_HOST" -P "$DB_PORT" -u root -p"$DB_ROOT_PASSWORD" -e "FLUSH PRIVILEGES;" 2>/dev/null || true

table_count=$(mysql --ssl=0 -h "$DB_HOST" -P "$DB_PORT" -u root -p"$DB_ROOT_PASSWORD" -Nse \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}'")
if [ "${table_count:-0}" = "0" ]; then
    echo "[entrypoint] Initializing legacy schema into ${DB_NAME}..."
    if [ -f /var/www/html/f_install/sql/common_schema.sql ]; then
        mysql --ssl=0 -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" < /var/www/html/f_install/sql/common_schema.sql
    fi
    if [ -f /var/www/html/hwe/sql/schema.sql ]; then
        mysql --ssl=0 -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" < /var/www/html/hwe/sql/schema.sql
    fi
    table_count=$(mysql --ssl=0 -h "$DB_HOST" -P "$DB_PORT" -u root -p"$DB_ROOT_PASSWORD" -Nse \
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}'")
    echo "[entrypoint] Legacy schema initialized: ${table_count} tables in ${DB_NAME}."
fi

# Auto-generate d_setting/DB.php if missing (skip web installer)
if [ ! -f /var/www/html/d_setting/DB.php ]; then
    echo "[entrypoint] Generating d_setting/DB.php..."
    mkdir -p /var/www/html/d_setting
    cat > /var/www/html/d_setting/DB.php <<DBEOF
<?php
namespace sammo;
class RootDB {
    const \$serverList = [
        0 => ['host'=>'${DB_HOST}','port'=>${DB_PORT},'user'=>'${DB_USER}','password'=>'${DB_PASSWORD}','db'=>'${DB_NAME}'],
    ];
    public static function db() {
        \$s = self::\$serverList[0];
        return new \mysqli(\$s['host'], \$s['user'], \$s['password'], \$s['db'], \$s['port']);
    }
}
DBEOF
    chown www-data:www-data /var/www/html/d_setting/DB.php
fi

exec "$@"
