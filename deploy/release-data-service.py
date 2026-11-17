#!/usr/bin/python3 -I
import fcntl
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import pwd
import re
import shutil
import stat
import subprocess
import sys
import tarfile
from tempfile import NamedTemporaryFile, TemporaryDirectory
import time


QUEUE = Path('/srv/shoe-factory-data-service-queue')
SERVICE = 'data-service'
CANDIDATE = 'shoe-factory-data-service-check'
BACKUP = 'shoe-factory-data-service-rollback'
NETWORK = 'shoe-factory-config_default'
REPOSITORY = 'localhost/shoe-factory-data-service'
ROLLBACK_IMAGE = REPOSITORY + ':previous'
LEGACY_IMAGE = 'shoe-factory-data-service:homeserver-prod'
MANAGED_LABEL = 'org.shoe-factory.data-service-release'
RELEASE_ID = r'[0-9a-f]{12}-[0-9]+'
MAX_ARCHIVE_SIZE = 640 * 1024 * 1024
PROBE_CODE = '''
from io import BytesIO
import json
import sys
from urllib.request import Request, urlopen

from PIL import Image

payload = {
    'reportDate': '2026-11-13',
    'orderGroups': [{
        'orderNo': 'CI-TEST', 'deliveryDate': '2026-11-15',
        'styles': [{'styleNo': '验证样例', 'pendingPairs': 16}],
    }],
}
request = Request('http://' + sys.argv[1] + ':8000/reports/delivery/render',
                  data=json.dumps(payload).encode(), headers={'Content-Type': 'application/json'})
with urlopen(request, timeout=5) as response:
    if response.headers.get_content_type() != 'image/png':
        raise ValueError('Expected a PNG response')
    with Image.open(BytesIO(response.read(1024 * 1024))) as image:
        if image.format != 'PNG':
            raise ValueError('Expected PNG image data')
        image.verify()
'''


def docker(*args, timeout=45, check=True):
    result = subprocess.run(('/usr/bin/docker', *args), text=True, capture_output=True, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError(f'Docker {args[0]} failed: {result.stderr.strip()}')
    return result


def container(name):
    instance = docker('container', 'ls', '-aq', '--filter', f'name=^/{name}$').stdout.strip()
    if not instance:
        return None
    return json.loads(docker('container', 'inspect', instance).stdout)[0]


def smoke_test(name):
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        probe = docker('exec', name, 'python', '-c', PROBE_CODE, name, timeout=8, check=False)
        if probe.returncode == 0:
            return
        time.sleep(2)
    raise RuntimeError(f'{name} did not produce a valid PNG on the Docker network.')


def run_container(name, image, restart):
    docker('run', '-d', '--pull=never', '--name', name, '--network', NETWORK,
           '--restart', restart, '--memory', '384m', '--user', '10001', '--cap-drop', 'ALL',
           '--security-opt', 'no-new-privileges', '--label', MANAGED_LABEL + '=true',
           '--log-driver', 'local', '--log-opt', 'max-size=5m', '--log-opt', 'max-file=3',
           '--health-cmd', 'python -c "from urllib.request import urlopen; '
           "urlopen('http://127.0.0.1:8000/openapi.json', timeout=4).close()\"",
           '--health-interval', '30s', '--health-timeout', '5s', '--health-start-period', '10s', image)


def remove_candidate(name):
    instance = container(name)
    if instance:
        if instance['Config']['Labels'].get(MANAGED_LABEL) != 'true':
            raise RuntimeError(f'The container name {name} is in use outside this deployment.')
        docker('rm', '-fv', instance['Id'])


def restore_backup():
    backup = container(BACKUP)
    if backup:
        remove_candidate(SERVICE)
        docker('rename', backup['Id'], SERVICE)
        docker('start', backup['Id'])
        smoke_test(SERVICE)
        print('Restored and verified the previous data-service container.', flush=True)


def deploy(image):
    remove_candidate(CANDIDATE)
    try:
        run_container(CANDIDATE, image, 'no')
        smoke_test(CANDIDATE)
    finally:
        remove_candidate(CANDIDATE)

    previous = container(SERVICE)
    if not previous or not previous['State']['Running']:
        raise RuntimeError('Expected a running production data-service container.')

    try:
        docker('rename', previous['Id'], BACKUP)
        docker('stop', previous['Id'])
        run_container(SERVICE, image, 'unless-stopped')
        smoke_test(SERVICE)
        docker('image', 'tag', previous['Image'], ROLLBACK_IMAGE)
        docker('rm', '-v', previous['Id'])
    except Exception:
        restore_backup()
        raise


def prune_images():
    current = container(SERVICE)
    if not current or not current['State']['Running'] or container(BACKUP):
        return
    keep = {current['Config']['Image'], ROLLBACK_IMAGE}
    rollback = docker('image', 'ls', '-q', '--filter', f'reference={ROLLBACK_IMAGE}').stdout.strip()
    legacy = docker('image', 'ls', '-q', '--filter', f'reference={LEGACY_IMAGE}').stdout.strip()
    if not rollback or legacy == rollback:
        keep.add(LEGACY_IMAGE)
    images = docker('image', 'ls', '--format', '{{.Repository}}:{{.Tag}}').stdout.splitlines()
    managed = re.compile(re.escape(REPOSITORY) + ':ci-' + RELEASE_ID)
    for image in images:
        if image not in keep and (managed.fullmatch(image) or image == LEGACY_IMAGE):
            result = docker('image', 'rm', image, check=False)
            if result.returncode:
                print(f'Could not clean up {image}: {result.stderr.strip()}', file=sys.stderr)


def receive_archive(claimed, archive, jenkins_uid, checksum):
    descriptor = os.open(claimed, os.O_RDONLY | os.O_NONBLOCK | os.O_NOFOLLOW)
    with os.fdopen(descriptor, 'rb') as request, archive.open('xb') as output:
        info = os.fstat(request.fileno())
        if (not stat.S_ISREG(info.st_mode) or info.st_uid != jenkins_uid or info.st_nlink != 1
                or not 0 < info.st_size <= MAX_ARCHIVE_SIZE):
            raise ValueError('Expected a regular image archive of at most 640 MiB owned by Jenkins.')
        digest = hashlib.sha256()
        total = 0
        while chunk := request.read(1024 * 1024):
            total += len(chunk)
            if total > MAX_ARCHIVE_SIZE:
                raise ValueError('Image archive exceeded the size limit.')
            digest.update(chunk)
            output.write(chunk)
        if digest.hexdigest() != checksum:
            raise ValueError('Image archive did not match the submitted SHA-256.')
    claimed.unlink()


def validate_archive(archive, image):
    with tarfile.open(archive, mode='r:') as tar:
        names = {}
        for member in tar:
            path = PurePosixPath(member.name)
            legacy_layer = (member.issym() and re.fullmatch(r'[0-9a-f]{64}/layer\.tar', member.name)
                            and re.fullmatch(r'\.\./[0-9a-f]{64}\.tar', member.linkname))
            if (member.name in names or member.name != path.as_posix() or path.is_absolute()
                    or '..' in path.parts or not (member.isreg() or member.isdir() or legacy_layer)):
                raise ValueError('Unexpected entry in the Docker archive.')
            names[member.name] = member

        for name in ('manifest.json', 'repositories'):
            if name not in names and name == 'repositories':
                continue
            member = tar.getmember(name)
            if not member.isreg() or member.size > 64 * 1024:
                raise ValueError('Unexpected image archive metadata.')
            with tar.extractfile(member) as metadata_file:
                metadata = json.load(metadata_file)
            if name == 'manifest.json':
                if not isinstance(metadata, list) or len(metadata) != 1 or metadata[0].get('RepoTags') != [image]:
                    raise ValueError('Docker archive must contain exactly the expected image tag.')
                config = metadata[0]['Config']
                layers = metadata[0]['Layers']
                if (not re.fullmatch(r'[0-9a-f]{64}\.json', config) or not names[config].isreg()
                        or not layers or any(not re.fullmatch(r'[0-9a-f]{64}\.tar', layer)
                                            or not names[layer].isreg() for layer in layers)):
                    raise ValueError('Docker archive contains invalid image layers.')
            elif list(metadata) != [REPOSITORY] or list(metadata[REPOSITORY]) != [image.rsplit(':', 1)[1]]:
                raise ValueError('Docker archive included an unexpected legacy image tag.')


def process_request(request, jenkins_uid):
    with TemporaryDirectory(dir=QUEUE / 'processing') as directory:
        claimed = Path(directory) / 'request'
        request.replace(claimed)
        match = re.fullmatch(f'({RELEASE_ID})-([0-9a-f]{{64}})\\.request', request.name)
        if not match:
            print('Discarded an invalid data-service release request.', file=sys.stderr)
            return
        image = f'{REPOSITORY}:ci-{match[1]}'
        try:
            restore_backup()
            archive = claimed.with_name('image.tar')
            receive_archive(claimed, archive, jenkins_uid, match[2])
            validate_archive(archive, image)
            if docker('image', 'ls', '-q', '--filter', f'reference={image}').stdout.strip():
                raise ValueError('The requested image tag already exists.')
            try:
                docker('image', 'load', '--input', str(archive), timeout=180)
            finally:
                archive.unlink()
            if docker('image', 'inspect', '-f', '{{len .Config.Volumes}}', image).stdout.strip() != '0':
                raise ValueError('The data-service image must not create volumes.')
            deploy(image)
        except Exception as error:
            status = f'FAILURE\nData-service release failed: {error}\n'
        else:
            status = f'SUCCESS\nPublished and verified {image}\n'

    try:
        prune_images()
    except (OSError, RuntimeError, subprocess.SubprocessError) as error:
        print(f'Image cleanup failed: {error}', file=sys.stderr)
    results = QUEUE / 'results'
    with NamedTemporaryFile(dir=results, mode='w', delete=False) as output:
        output.write(status)
        temporary = Path(output.name)
    temporary.chmod(0o640)
    temporary.replace(results / request.stem)
    retained = sorted(results.iterdir(), key=lambda path: path.stat().st_mtime_ns, reverse=True)
    for old_result in retained[10:]:
        old_result.unlink()
    print(status, end='', flush=True)


def main():
    if os.geteuid() != 0 or len(sys.argv) != 1:
        raise ValueError('The data-service publisher must run as root without arguments.')
    os.umask(0o077)
    with (QUEUE / 'release.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        for old_stage in (QUEUE / 'processing').iterdir():
            shutil.rmtree(old_stage)
        uid = pwd.getpwnam('jenkins').pw_uid
        for request in sorted((QUEUE / 'requests').glob('*.request')):
            process_request(request, uid)


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        sys.exit(f'Data-service publisher failed: {error}')
