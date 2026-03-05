#!/usr/bin/env python3

import os
import os.path
from subprocess import Popen, PIPE, run as subprocess_run
from datetime import datetime
from multiprocessing.dummy import Pool as ThreadPool

def backup_table(db_name, table_name, output_prefix):
    sql_path = '{}/{}/{}.sql.gz'.format(output_prefix, db_name, table_name)
    #print(sql_path)
    with open(sql_path, 'wb') as sql_out:
        sqldump = Popen(['mysqldump', '-f', '--skip-comments', '-h', 'db', '-u', 'root', '--password={}'.format(root_pw), db_name, table_name], stdout=PIPE)
        gzdump = Popen(['gzip', '-c'], stdin=sqldump.stdout, stdout=sql_out)
        sqldump.wait()
        gzdump.wait()
    os.chmod(sql_path, 0o644)
    print('sql done {}, {}'.format(db_name, table_name))

def backup_files(src_path, output_path):
    subprocess_run(['rsync', '-au', src_path, output_path])
    print('file done {}'.format(src_path))

now = datetime.now()
print('> backup begin', now.isoformat())
weekday = now.weekday()
hour = now.hour

store_path = '/var/backup'
output_prefix = '{}/hidche_backup_w{}-{:02}'.format(store_path, weekday, hour)

os.makedirs(output_prefix, exist_ok=True)

db_prefix = os.environ['HIDCHE_DB_PREFIX']
server_list = os.environ['HIDCHE_SERVER_LIST'].strip(' ,').split(',')
server_list.append('root')

board_db_name = os.environ['HIDCHE_DB_BBS_USER']
server_list.append(board_db_name)
#print(server_list)

root_pw = os.environ['MARIADB_ROOT_PASSWORD']

board_files_path = os.environ.get('HIDCHE_BOARD_FILES_PATH', '/var/www/board/files')

with ThreadPool(4) as pool:
    waiters = []
    waiters.append(pool.apply_async(backup_files, ('/var/www/html/sam/d_pic', '{}/sam_image'.format(output_prefix))))
    print(board_files_path)
    if os.path.exists(board_files_path):
        waiters.append(pool.apply_async(backup_files, (board_files_path, '{}/board_files'.format(output_prefix))))

    for server_name in server_list:
        db_name = '{}_{}'.format(db_prefix, server_name)
        raw_tables = subprocess_run(['mysql', '-NBA' , '-h' ,'db', '-u', 'root' ,'--password={}'.format(root_pw), '-D', db_name, '-e', 'show tables'], stdout=PIPE)
        raw_tables = raw_tables.stdout.decode('utf-8').strip(' \n\t')
        if raw_tables == '':
            continue

        tables = raw_tables.split('\n')
        d_setting_path = '/var/www/html/sam/{}/d_setting'.format(server_name)

        if os.path.exists(d_setting_path):
            waiters.append(pool.apply_async(backup_files, (d_setting_path, '{}/{}'.format(output_prefix, db_name))))

        os.makedirs('{}/{}'.format(output_prefix, db_name), exist_ok=True)
        for table_name in tables:
            waiters.append(pool.apply_async(backup_table, (db_name, table_name, output_prefix)))
    for waiter in waiters:
        waiter.wait()

print('> backup finish', datetime.now().isoformat())