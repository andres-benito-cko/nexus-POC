#!/usr/bin/env python3
"""
Seed the default engine config into nexus-api if none exists yet.
Run on the API instance after nexus-api is up:
  python3 /home/ec2-user/nexus-POC/scripts/seed-config.py
"""
import json
import urllib.request
import urllib.error
import os
import sys

YAML_PATH = os.path.join(
    os.path.dirname(__file__),
    '../nexus-transformer/src/main/resources/nexus-engine-config.yaml'
)
API = 'http://localhost:8083'


def get(path):
    req = urllib.request.Request(f'{API}{path}')
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        return e.code, {}


def post(path, body=None):
    data = json.dumps(body).encode() if body else b''
    req = urllib.request.Request(
        f'{API}{path}', data=data,
        headers={'Content-Type': 'application/json'},
        method='POST'
    )
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read()) if e.read else {}


status, _ = get('/configs/active')
if status == 200:
    print('Active config already exists — nothing to do.')
    sys.exit(0)

with open(YAML_PATH) as f:
    content = f.read()

status, body = post('/configs', {
    'version': '1.0',
    'content': content,
    'createdBy': 'seed',
})
if status != 200:
    print(f'ERROR creating config: {status} {body}')
    sys.exit(1)

config_id = body['id']
print(f'Created config id={config_id}')

status, body = post(f'/configs/{config_id}/activate')
if status != 200:
    print(f'ERROR activating config: {status} {body}')
    sys.exit(1)

print(f'Activated config id={config_id} version={body.get("version")} active={body.get("active")}')
