"""Reject private paths, oversized Git blobs and recognizable credentials without printing values."""
from pathlib import Path, PurePosixPath
import argparse
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PRIVATE_PARTS = {'.gradle', 'build', 'local-backups', 'release-staging', 'releases', 'verification', 'handoff'}
PRIVATE_SUFFIXES = {'.dpapi', '.key', '.pem', '.jks', '.keystore', '.p12', '.pfx', '.apk', '.zip'}
BINARY_SUFFIXES = {'.png', '.jpg', '.jpeg', '.webp', '.ttf', '.woff2', '.wav', '.ogg', '.m4a', '.mp4', '.jar'}
SECRETS = [
    re.compile(rb'gh[pousr]_[A-Za-z0-9_]{20,}'),
    re.compile(rb'github_pat_[A-Za-z0-9_]{30,}'),
    re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
]


def git(*args, data=None):
    return subprocess.check_output(['git', '-c', 'core.quotePath=false', '-C', str(ROOT), *args], input=data)


def forbidden(path):
    p = PurePosixPath(path)
    return bool(
        set(p.parts) & PRIVATE_PARTS
        or p.suffix.lower() in PRIVATE_SUFFIXES
        or p.name in {'.env', '.mcp.json', 'local.properties'}
        or path.endswith('/settings.local.json')
    )


def tree(revision):
    pairs = []
    for entry in git('ls-tree', '-rz', '--full-tree', revision).split(b'\0'):
        if not entry:
            continue
        head, path = entry.split(b'\t', 1)
        _, kind, oid = head.split()
        if kind == b'blob':
            pairs.append((oid.decode('ascii'), path.decode('utf-8')))
    return pairs


def audit(pairs):
    failures = set()
    for _, path in pairs:
        if forbidden(path):
            failures.add((path, 'private path'))
    objects = sorted({oid for oid, _ in pairs})
    request = ''.join(oid + '\n' for oid in objects).encode('ascii')
    sizes = {}
    if objects:
        for line in git('cat-file', '--batch-check=%(objectname) %(objectsize)', data=request).splitlines():
            oid, size = line.split()
            sizes[oid.decode('ascii')] = int(size)
    scanned = set()
    for oid, path in pairs:
        size = sizes[oid]
        if size > 100 * 1024 * 1024:
            failures.add((path, 'GitHub blob size limit'))
        if oid in scanned or PurePosixPath(path).suffix.lower() in BINARY_SUFFIXES:
            continue
        scanned.add(oid)
        if size <= 2 * 1024 * 1024:
            data = git('cat-file', 'blob', oid)
            if any(pattern.search(data) for pattern in SECRETS):
                failures.add((path, 'credential pattern'))
    if failures:
        for path, rule in sorted(failures):
            print(f'BLOCK: {path} ({rule})')
        raise SystemExit(1)
    print(f'PASS: {len(pairs)} path/blob pairs; {len(objects)} unique blobs; no private paths or recognized credentials')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--history', action='store_true')
    parser.add_argument('--cached', action='store_true')
    parser.add_argument('--self-test', action='store_true')
    args = parser.parse_args()
    if args.self_test:
        assert all(forbidden(p) for p in ['verification/app-data.dpapi', 'signing/release.jks', 'releases/GhibliValley-1.0.0.apk'])
        assert not forbidden('app/src/main/java/ua/ghibli/tv/launcher/MainActivity.java')
        assert not forbidden('.release-certificate.sha256')
        assert any(p.search(('ghp_' + 'A' * 40).encode()) for p in SECRETS)
        print('PASS: guard rejects reintroduced private paths and token fixtures')
        return
    pairs = set()
    if args.cached:
        for entry in git('ls-files', '--stage', '-z').split(b'\0'):
            if entry:
                head, path = entry.split(b'\t', 1)
                pairs.add((head.split()[1].decode('ascii'), path.decode('utf-8')))
    if args.history:
        for revision in git('rev-list', 'HEAD').decode('ascii').splitlines():
            pairs.update(tree(revision))
    if not args.cached and not args.history:
        pairs.update(tree('HEAD'))
    audit(sorted(pairs))


if __name__ == '__main__':
    main()
