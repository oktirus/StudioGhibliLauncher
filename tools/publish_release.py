"""Validate a locally signed StudioGhibliLauncher APK; publish only with explicit --publish."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
REPO = 'oktirus/StudioGhibliLauncher'
PACKAGE = 'ua.ghibli.tv.valley'


def command(args):
    return subprocess.check_output([str(a) for a in args], text=True, encoding='utf-8', errors='strict')


def apk_metadata(apk, sdk):
    tools = sdk / 'build-tools' / '34.0.0'
    aapt = tools / ('aapt.exe' if os.name == 'nt' else 'aapt')
    # Windows aapt cannot reliably open Cyrillic workspace paths.
    with tempfile.TemporaryDirectory(prefix='ghibli-apk-metadata-') as temporary:
        inspected = Path(temporary) / 'candidate.apk'
        shutil.copyfile(apk, inspected)
        badging = command([aapt, 'dump', 'badging', inspected])
        certificate = command(['java', '-jar', tools / 'lib' / 'apksigner.jar', 'verify', '--print-certs', inspected])
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not match or match[1] != PACKAGE:
        raise ValueError('APK package does not match StudioGhibliLauncher')
    digests = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)', certificate)
    expected = (ROOT / '.release-certificate.sha256').read_text(encoding='ascii').strip().lower()
    if len(digests) != 1 or digests[0].lower() != expected:
        raise ValueError('APK signing key differs from .release-certificate.sha256')
    return int(match[2]), match[3]


def prepare(apk, notes_file, sdk):
    code, version = apk_metadata(apk, sdk)
    gradle = (ROOT / 'app/build.gradle').read_text(encoding='utf-8-sig')
    declared_code = re.search(r'versionCode\s+(\d+)', gradle)
    declared_name = re.search(r"versionName\s+'([^']+)'", gradle)
    if not declared_code or not declared_name or code != int(declared_code[1]) or version != declared_name[1]:
        raise ValueError('APK version differs from app/build.gradle')
    if code <= 0 or not re.fullmatch(r'\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?', version):
        raise ValueError('Use a positive versionCode and a release versionName')
    size = apk.stat().st_size
    if not 0 < size <= 128 * 1024 * 1024:
        raise ValueError('APK exceeds updater size policy')
    folder = ROOT / 'release-staging' / ('v' + version)
    folder.mkdir(parents=True, exist_ok=True)
    output = folder / ('GhibliValley-' + version + '.apk')
    if apk.resolve() != output.resolve():
        shutil.copy2(apk, output)
    metadata = {
        'schemaVersion': 1,
        'applicationId': PACKAGE,
        'versionCode': code,
        'versionName': version,
        'apkName': output.name,
        'bytes': size,
        'sha256': hashlib.sha256(output.read_bytes()).hexdigest(),
        'notes': notes_file.read_text(encoding='utf-8-sig').strip(),
    }
    (folder / 'update.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding='utf-8')
    return folder, metadata


def publish(folder, metadata, notes_file):
    if command(['git', '-C', ROOT, 'status', '--porcelain']).strip():
        raise ValueError('Commit reviewed changes before publishing a release')
    repo = json.loads(command(['gh', 'repo', 'view', REPO, '--json', 'nameWithOwner,visibility']))
    if repo['nameWithOwner'].lower() != REPO.lower() or repo['visibility'] != 'PUBLIC':
        raise ValueError('Expected the approved public repository')
    tag = 'v' + metadata['versionName']
    exists = subprocess.run(['gh', 'release', 'view', tag, '--repo', REPO], capture_output=True)
    if exists.returncode == 0:
        raise ValueError('Release already exists; do not overwrite a distributed version')
    head = command(['git', '-C', ROOT, 'rev-parse', 'HEAD']).strip()
    runs = json.loads(
        command([
            'gh', 'run', 'list', '--repo', REPO, '--commit', head,
            '--workflow', 'android.yml', '--limit', '1', '--json', 'status,conclusion',
        ])
    )
    if not runs or runs[0]['status'] != 'completed' or runs[0]['conclusion'] != 'success':
        raise ValueError('The current commit must pass its GitHub Android workflow before publication')
    command([
        'gh', 'release', 'create', tag, folder / metadata['apkName'], folder / 'update.json',
        '--repo', REPO, '--target', head, '--title', 'Ghibli · Вітер над долиною ' + metadata['versionName'],
        '--notes-file', notes_file, '--draft',
    ])
    # A draft is reviewed by readback before it becomes a public updater source.
    with tempfile.TemporaryDirectory(prefix='ghibli-release-readback-') as temporary:
        command(['gh', 'release', 'download', tag, '--repo', REPO, '--dir', temporary])
        downloaded = Path(temporary)
        if hashlib.sha256((downloaded / metadata['apkName']).read_bytes()).hexdigest() != metadata['sha256']:
            raise ValueError('Uploaded APK readback does not match; draft left unpublished')
        actual = json.loads((downloaded / 'update.json').read_text(encoding='utf-8'))
        if actual != metadata:
            raise ValueError('Uploaded metadata readback does not match; draft left unpublished')
    command(['gh', 'release', 'edit', tag, '--repo', REPO, '--draft=false', '--latest'])
    latest = json.loads(command(['gh', 'api', f'repos/{REPO}/releases/latest']))
    if latest['tag_name'] != tag or latest['draft'] or latest['prerelease']:
        raise ValueError('Latest release verification failed')
    print(latest['html_url'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--notes-file', type=Path, required=True)
    default_sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not default_sdk and os.environ.get('LOCALAPPDATA'):
        default_sdk = str(Path(os.environ['LOCALAPPDATA']) / 'Android' / 'Sdk')
    parser.add_argument('--sdk', type=Path, default=default_sdk)
    parser.add_argument('--publish', action='store_true', help='Create and publish the approved GitHub release')
    args = parser.parse_args()
    if args.sdk is None:
        parser.error('Pass --sdk or configure ANDROID_HOME')
    folder, metadata = prepare(args.apk.resolve(), args.notes_file.resolve(), args.sdk.resolve())
    print(json.dumps({'prepared': str(folder), 'repository': REPO, **metadata}, ensure_ascii=False, indent=2))
    if args.publish:
        publish(folder, metadata, args.notes_file.resolve())


if __name__ == '__main__':
    main()
