#!/usr/bin/python3 -I
import fcntl
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import pwd
import re
import stat
import subprocess
import sys
import tempfile
import time
from urllib.request import ProxyHandler, build_opener


ROOT = Path('/srv/shoe-factory-backend')
QUEUE = Path('/srv/shoe-factory-backend-queue')
PROXY = ROOT / 'nginx/upstream.conf'
RUNTIME = 'localhost/shoe-factory-backend-runtime:java17'
MAX_JAR_SIZE = 160 * 1024 * 1024
API = '/api/customer-configs'
SITE = 'http://100.107.45.39'
CONFIG_NETWORK = 'shoe-factory-config_default'
DATABASE_NETWORK = 'shoe-factory-data'
DATABASE = 'shoe_factory_assistant_prod'
BASE_ENV = {
    'PATH': '/usr/sbin:/usr/bin:/sbin:/bin', 'LANG': 'C.UTF-8',
    'DOCKER_CONFIG': str(ROOT / 'docker-cli'),
}
HTTP = build_opener(ProxyHandler({}))


def docker(*args, capture=False, data=None, env=BASE_ENV, timeout=45, check=True):
    result = subprocess.run(
        ('/usr/bin/docker', *args), cwd=ROOT, env=env, input=data, text=True,
        capture_output=capture, timeout=timeout, check=check,
    )
    return result.stdout.strip() if capture else result.returncode


def check_api(url, digest=None):
    with HTTP.open(url + API, timeout=5) as response:
        return json.load(response).get('success') is True and (
            digest is None or response.headers.get('X-Shoe-Backend-Jar') == digest
        )


def await_check(probe, message, seconds):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            if probe():
                return
        except (OSError, ValueError, subprocess.SubprocessError):
            pass
        time.sleep(2)
    raise RuntimeError(message)


def verified_db_session(ip):
    query = f"""
        SELECT EXISTS (
          SELECT 1 FROM performance_schema.threads t
          JOIN performance_schema.status_by_thread s ON s.THREAD_ID = t.THREAD_ID
          WHERE t.PROCESSLIST_USER = 'shoe_factory_app'
            AND t.PROCESSLIST_DB = '{DATABASE}'
            AND t.PROCESSLIST_HOST = '{ip}'
            AND s.VARIABLE_NAME = 'Ssl_cipher' AND s.VARIABLE_VALUE <> ''
        );
    """
    return docker(
        'exec', '-i', 'shoe-factory-mysql', 'sh', '-ceu',
        'export MYSQL_PWD="$(cat /run/secrets/mysql-root-password)"; '
        'exec mysql --user=root --batch --skip-column-names',
        capture=True, data=query, timeout=10,
    ) == '1'


def atomic_write(path, content, mode=0o600):
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as output:
        temp = Path(output.name)
        output.write(content)
    try:
        temp.chmod(mode)
        temp.replace(path)
    finally:
        temp.unlink(missing_ok=True)


def reload_proxy():
    docker('exec', 'nginx', 'nginx', '-t')
    docker('exec', 'nginx', 'nginx', '-s', 'reload')


def receive_jar(path, request, expected):
    header = request.readline(66)
    if re.fullmatch(rb'[0-9a-f]{64}\n', header) is None:
        raise ValueError('Expected the SHA-256 header followed by the build JAR.')
    if header.strip().decode('ascii') != expected:
        raise ValueError('The JAR checksum header did not match the release request.')
    written = 0
    actual = hashlib.sha256()
    with path.open('xb') as output:
        while chunk := request.read(1024 * 1024):
            written += len(chunk)
            if written > MAX_JAR_SIZE:
                raise ValueError('Backend JAR exceeded the release size limit.')
            output.write(chunk)
            actual.update(chunk)
    if not written or actual.hexdigest() != expected:
        raise ValueError('Backend JAR checksum did not match the submitted build.')


def release(stage, digest):
    previous = PROXY.read_bytes()
    match = re.match(rb'# jar-sha256 ([0-9a-f]{64})\n', previous)
    previous_digest = match[1].decode('ascii') if match else None
    container = 'shoe-factory-backend-' + digest[:12]
    image = 'shoe-factory-backend:jar-' + digest
    proxy_changed = False
    owns_candidate = False

    try:
        if previous_digest != digest:
            existing = docker('ps', '-aq', '--filter', 'name=^/' + container + '$', capture=True)
            if existing and docker('inspect', '-f', '{{index .Config.Labels "org.shoe-factory.backend-jar"}}',
                                   container, capture=True) != digest:
                raise RuntimeError('Backend container name is already in use by a different release.')
            owns_candidate = True
            (stage / 'Dockerfile').write_text(
                f'FROM {RUNTIME}\n'
                'RUN groupadd --gid 10001 shoeapp && '
                'useradd --uid 10001 --gid 10001 --create-home --shell /usr/sbin/nologin shoeapp\n'
                'COPY app.jar /app/app.jar\n'
                'USER 10001:10001\n',
                encoding='ascii',
            )
            (stage / 'app.jar').chmod(0o444)
            docker('build', '--quiet', '--pull=false', '--network=none', '-t', image, str(stage), timeout=240)
            docker('compose', '-f', str(ROOT / 'compose.yml'), '-p', 'shoe-backend-' + digest[:12],
                   'up', '-d', '--no-build', '--pull', 'never', 'backend', env={
                       **BASE_ENV, 'BACKEND_IMAGE': image, 'BACKEND_CONTAINER': container,
                       'BACKEND_JAR_SHA256': digest,
                   }, timeout=120)

        if docker('exec', container, 'sha256sum', '/app/app.jar', capture=True).split()[0] != digest:
            raise RuntimeError('The running backend does not contain the submitted JAR.')
        networks = json.loads(docker('inspect', '-f', '{{json .NetworkSettings.Networks}}', container, capture=True))
        api_ip = ipaddress.IPv4Address(networks[CONFIG_NETWORK]['IPAddress'])
        db_ip = ipaddress.IPv4Address(networks[DATABASE_NETWORK]['IPAddress'])
        await_check(lambda: check_api(f'http://{api_ip}:8080') and verified_db_session(db_ip),
                    f'Backend did not become ready with a TLS session on {DATABASE}.', 180)
        print(f'Verified backend API and authenticated TLS connection to {DATABASE}.', flush=True)

        if previous_digest != digest:
            config = f'''# jar-sha256 {digest}
resolver 127.0.0.11 valid=5s ipv6=off;
set $shoe_backend {container}:8080;
proxy_pass http://$shoe_backend;
proxy_http_version 1.1;
proxy_set_header Host $http_host;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-For $remote_addr;
proxy_read_timeout 150s;
add_header X-Shoe-Backend-Jar {digest} always;
'''
            atomic_write(PROXY, config.encode('ascii'))
            proxy_changed = True
        reload_proxy()
        await_check(lambda: check_api(SITE, digest), 'Nginx did not serve the verified backend release.', 45)
    except Exception:
        if proxy_changed:
            atomic_write(PROXY, previous)
            reload_proxy()
            print('Restored the previous Nginx upstream.', file=sys.stderr)
        if owns_candidate:
            docker('rm', '-f', container, check=False, capture=True)
        raise

    if previous_digest and previous_digest != digest:
        docker('stop', 'shoe-factory-backend-' + previous_digest[:12], check=False)
    print(f'Published backend JAR SHA-256 {digest} at {SITE}{API}', flush=True)


def process_request(path, jenkins_uid):
    match = re.fullmatch(r'([0-9]+-([0-9a-f]{64}))\.request', path.name)
    with tempfile.TemporaryDirectory(prefix='claimed.', dir=QUEUE / 'processing') as directory:
        claimed = Path(directory) / 'request'
        try:
            path.replace(claimed)
        except FileNotFoundError:
            return
        if not match:
            print('Discarded a malformed backend release request.', file=sys.stderr)
            return
        try:
            with tempfile.TemporaryDirectory(prefix='staging.', dir=ROOT) as staging:
                stage = Path(staging)
                descriptor = os.open(claimed, os.O_RDONLY | os.O_NONBLOCK | os.O_NOFOLLOW)
                with os.fdopen(descriptor, 'rb') as request:
                    info = os.fstat(request.fileno())
                    if not stat.S_ISREG(info.st_mode) or info.st_uid != jenkins_uid or info.st_nlink != 1:
                        raise ValueError('Expected a regular release request file owned by Jenkins.')
                    receive_jar(stage / 'app.jar', request, match[2])
                claimed.unlink()
                release(stage, match[2])
        except Exception as error:
            status = f'FAILURE\nBackend release failed: {error}\n'
        else:
            status = f'SUCCESS\nPublished backend JAR SHA-256 {match[2]}\n'
        atomic_write(QUEUE / 'results' / match[1], status.encode(), mode=0o640)
        print(status, end='', flush=True)


def main():
    if os.geteuid() != 0 or len(sys.argv) != 1:
        raise ValueError('The backend release command must run as root without arguments.')
    os.umask(0o077)
    with (ROOT / 'release.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        jenkins_uid = pwd.getpwnam('jenkins').pw_uid
        for request in sorted((QUEUE / 'requests').glob('*.request')):
            process_request(request, jenkins_uid)


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        sys.exit(f'Backend release failed: {error}')
