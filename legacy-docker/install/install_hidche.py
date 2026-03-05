#!/usr/bin/env python3

import os
import os.path
import requests
import time
import subprocess
import json
from hashlib import sha512

def hash_password(globalSalt, password):
    globalSalt = globalSalt.encode('utf-8')
    password = password.encode('utf-8')
    return sha512(globalSalt+password+globalSalt).hexdigest()

env = os.environ
wwwRoot = '/var/www/html'
samRoot = '%s/sam'%wwwRoot
indexPHP = '%s/index.php'%wwwRoot
if os.path.exists('%s/hwe/d_setting/DB.php'):
    exit(0)

headers = {
    "X-Requested-With": "XMLHttpRequest",
}

useWaitIndex = False
if not os.path.exists(indexPHP):
    useWaitIndex = True
    fp = open(indexPHP, 'wt', encoding='utf-8')
    fp.write("""
<!doctype html>
<html>
<head>
<title>HIDCHE - Auto Install Process</title>
<meta http-equiv="refresh" content="5"/>
</head>
<body>
<h1>Please wait for installation.</h1>
</body>
</html>
""")
    fp.close()

while True:
    try:
        r = requests.head('http://web/sam/f_install/j_install_status.php')
        if r.status_code == 503 or r.status_code == 500:
            b = requests.get('http://web/sam/install.php', timeout=60000)
        elif r.status_code == 200:
            break
    except Exception:
        pass
    print("Waiting for web connection...", flush=True)
    time.sleep(2)

requests.get('http://web/sam/install.php', timeout=60000) #NPM

while True:
    result = subprocess.call('nc -z -v -w30 db 3306'.split(' '), stderr=subprocess.STDOUT)
    if result == 0:
        break
    print("Waiting for database connection...", flush=True)
    time.sleep(2)

session = requests.session()

db_root_pw = hash_password('root'+env['HIDCHE_PW_SALT'], env['MARIADB_ROOT_PASSWORD'])[:32]
db_root_name = '%s_root'%env['HIDCHE_DB_PREFIX']

print("Setup DB...", flush=True)
setupDBResult = session.post('http://web/sam/f_install/j_setup_db.php', {
    'db_host':'db',
    'db_port':3306,
    'db_id':db_root_name,
    'db_pw':db_root_pw,
    'db_name':db_root_name,
    'serv_host':env['HIDCHE_GAME_PATH'],
    'shared_icon_path':'%s/icons'%env['HIDCHE_IMAGE_PATH'],
    'game_image_path':'%s/game'%env['HIDCHE_IMAGE_PATH'],
    'kakao_rest_key':env['HIDCHE_KAKAO_REST_KEY'],
    'kakao_admin_key':env['HIDCHE_KAKAO_ADMIN_KEY'],
}, headers=headers)
if not setupDBResult.ok:
    print(setupDBResult, flush=True)
setupDBJsonResult = json.loads(setupDBResult.text)
print(setupDBJsonResult, flush=True)

globalSalt = setupDBJsonResult['globalSalt']

hashPassword = hash_password(globalSalt, env['HIDCHE_PASSWORD'])

query = {
    'username':env['HIDCHE_ADMIN'],
    'password':hashPassword,
    'nickname':'운영자'
}

print("Create Admin...", flush=True)
setupAdminResult = session.post('http://web/sam/f_install/j_create_admin.php', query, headers=headers)
setupAdminJsonResult = json.loads(setupAdminResult.text)
print(setupAdminJsonResult, flush=True)

print("Login Admin...", flush=True)
loginResult = session.post('http://web/sam/api.php?path=Login%2FLoginByID', json=query, headers=headers)
print(loginResult.text, flush=True)

serverList = str(env['HIDCHE_SERVER_LIST']).split(',')
serverList.reverse()
for serverName in serverList:
    updateResult = session.post('http://web/sam/j_updateServer.php', {
        'server':serverName,
        'target':'origin/'+env['gameGitBranch']
    }, headers=headers, timeout=60000)
    print(serverName, updateResult.text, flush=True)

    db_pw = hash_password(serverName+env['HIDCHE_PW_SALT'], env['MARIADB_ROOT_PASSWORD'])[:32]
    db_name = '%s_%s'%(env['HIDCHE_DB_PREFIX'], serverName)
    resetResult = session.post('http://web/sam/%s/j_install_db.php'%serverName, {
        'db_host':'db',
        'db_port':3306,
        'db_id':db_name,
        'db_pw':db_pw,
        'db_name':db_name,
    }, headers=headers)
    print(serverName, resetResult.text, flush=True)

if useWaitIndex:
    fp = open(indexPHP, 'wt', encoding='utf-8')
    fp.write("<?php header('location:sam/',TRUE,301);")
    fp.close()
    os.chown(indexPHP, 33, 33)

print('Welcome to HIDCHE', flush=True)
