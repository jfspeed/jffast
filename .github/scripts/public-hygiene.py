#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Fail-closed hygiene gate for this published repository (static: no DB, no network).

Why this runs in CI and not only in a maintainer-side commit hook
  The hook maintained by the vendor reads a vocabulary that lives outside this
  repository, so it does not cover a contribution made through the GitHub web UI,
  a push made with the hook bypassed, or a machine path pasted out of someone
  else's stack trace. This file is the part of that gate which can live in public.

Design rule: nothing here names an internal product. Refusals are expressed by
*shape* - an identifier that is not published by this repository, a developer
machine path, a personal mail address, a host on a top-level domain the vendor no
longer uses, a port in the band reserved for a companion service. A new public
artifact needs no edit here: it is picked up from the build files it is declared in.

Exit codes
  0  clean
  1  a hit was found (file:line listed)
  2  the check itself could not run (not a git checkout, no file in scope) - never
     reported as clean, because an unrun scan looks exactly like a passing one.
"""
import argparse
import os
import re
import subprocess
import sys

# Text/code files in scope. Build noise and vendored trees are skipped by path part.
SCAN_EXT = ('.java', '.md', '.yml', '.yaml', '.xml', '.sql', '.properties', '.html',
            '.js', '.ts', '.vue', '.json', '.ps1', '.sh', '.bat', '.txt', '.cfg',
            '.ini', '.toml', '.py', '.cjs', '.mjs')
SKIP_DIRS = ('node_modules', 'target', 'dist', 'build', '.git', '.idea', '.vscode')
# Only these file types carry a port the way a leak does (bind/forward configuration).
CONFIG_EXT = ('.yml', '.yaml', '.properties', '.conf', '.cfg', '.ini', '.toml',
              '.env', '.sh', '.bat', '.ps1', '.xml', '.ts', '.js', '.md')

# Vendor-family identifier shape: the product root followed by a hyphenated suffix.
# `j(?:f|p)fast` covers both product roots (the current one and the pre-rename one);
# the suffix must resolve against what this repository's build files publish.
FAMILY = re.compile(r'\bj(?:f|p)fast(-[a-z0-9]+)+\b', re.I)
# Names that are public but not declared in *this* repository's build files, so the
# derivation above cannot see them: the companion published repository, the modules of
# the published backend that this front-end refers to by name, the API context path,
# the CSS / storage key prefix this app sets, and the deployment object names
# (compose services, networks, volumes, buckets) and doc examples declared here.
# Keep this list to names that actually occur: an entry nobody uses is a name a future
# leak could reuse without being reported.
COMPANION = {
    # published companion
    'jffast-ui', 'jffast-ui-dist', 'jffast-api',
    # backend modules referred to by name from the front end
    'jffast-biz', 'jffast-starter', 'jffast-starter-web', 'jffast-starter-locale',
    'jffast-system-service',
    # identifiers owned by this project's own style / config / examples
    'jffast-breadcrumb', 'jffast-logo', 'jffast-setting', 'jffast-setting-close',
    'jffast-setting-text', 'jffast-wrapper', 'jffast-token',
    # deployment object names and documentation examples
    'jffast-net', 'jffast-mysql', 'jffast-redis', 'jffast-redis-master',
    'jffast-mysql-data', 'jffast-redis-data', 'jffast-upload', 'jffast-secret',
    'jffast-tls', 'jffast-k8s', 'jffast-backend', 'jffast-web', 'jffast-order',
    'jffast-module-order', 'jffast-file-test', 'jffast-job-refresher',
    'jffast-1250000000',
}

# Free mail providers that carry no meaning for this project beyond "a person's
# inbox". The contact route this project publishes is the GitHub issue tracker.
PERSONAL_MAIL_HOSTS = ('qq', '163', '126', 'gmail', 'outlook', 'hotmail', 'foxmail',
                       '139', '189', 'yeah', 'sina', 'sohu')

RULES = (
    ('developer-machine-path',
     re.compile(r'(?i)\b[a-z]:[\\/](?:users|work|soft|repo|dev|code)[\\/]'
                r'|/Users/[A-Za-z0-9._-]+/')),
    # Pattern source is assembled: this file is itself in scan scope, so a literal of a
    # refused shape spelled out in a rule definition would be reported by that rule.
    ('personal-mail-channel',
     re.compile(r'(?i)' + 'mail' + 'to:' +
                r'|(?:@|\b)(?:' + '|'.join(PERSONAL_MAIL_HOSTS) + r')\.(?:com|cn)\b')),
    ('retired-domain-host',
     re.compile(r'(?i)\bhttps?://[^\s"\'<>)\]]*\.(?:cc|tk|ml|ga|cf)\b')),
    # The 73xx band is reserved for a companion deployment; this one binds 72xx.
    # The lookbehind keeps a decimal coordinate (an SVG path point) out of the band.
    ('reserved-port-band', re.compile(r'(?<![\d.])73\d{2}(?![\d.])')),
)


class GateError(Exception):
    """The scan could not be trusted -> exit 2."""


def tracked_files(root):
    try:
        proc = subprocess.run(['git', '-C', root, 'ls-files', '-z'],
                              capture_output=True, text=True, encoding='utf-8',
                              errors='replace')
    except OSError as error:
        raise GateError('git ls-files could not run (%s)' % error)
    if proc.returncode != 0:
        raise GateError('not a git checkout, or ls-files failed (rc=%d): %s'
                        % (proc.returncode, (proc.stderr or '').strip()[:200]))
    return [rel for rel in proc.stdout.split('\0') if rel]


def published_names(root, files):
    """Artifact / package names this repository itself publishes (lower-cased)."""
    names = set()
    for rel in files:
        low = rel.lower()
        if 'node_modules' in rel:
            continue
        try:
            with open(os.path.join(root, rel.replace('/', os.sep)), 'rb') as handle:
                text = handle.read().decode('utf-8', 'replace')
        except OSError:
            continue
        if low.endswith('pom.xml'):
            names.update(m.group(1).lower() for m in re.finditer(
                r'<artifactId>\s*([^<\s]+)\s*</artifactId>', text))
        elif low.endswith('package.json'):
            m = re.search(r'"name"\s*:\s*"([^"]+)"', text)
            if m:
                names.add(m.group(1).replace('@', '').replace('/', '-').lower())
    if not names:
        raise GateError('no published artifact/package name found in %s - the allowlist '
                        'cannot be empty or every family token becomes a false positive' % root)
    return names


def scan(root):
    files = tracked_files(root)
    scope = [rel for rel in files
             if not any(part in SKIP_DIRS for part in rel.split('/'))
             and rel.lower().endswith(SCAN_EXT)]
    if not scope:
        raise GateError('nothing in scope (%d tracked file(s)); a collapsed scope is not a PASS'
                        % len(files))
    allowed = published_names(root, files) | COMPANION
    hits = []
    for rel in scope:
        path = os.path.join(root, rel.replace('/', os.sep))
        try:
            with open(path, 'rb') as handle:
                text = handle.read().decode('utf-8', 'replace')
        except OSError as error:
            raise GateError('%s unreadable (%s) - a file the gate cannot read is not a clean file'
                            % (rel, error))
        config_scoped = rel.lower().endswith(CONFIG_EXT)
        for number, line in enumerate(text.splitlines(), 1):
            for name, rule in RULES:
                if name == 'reserved-port-band' and not config_scoped:
                    continue
                if rule.search(line):
                    hits.append('%s:%d: [%s] %s' % (rel, number, name, line.strip()[:160]))
            for match in FAMILY.finditer(line):
                if match.group(0).lower() not in allowed:
                    hits.append('%s:%d: [unpublished-artifact-name] %s'
                                % (rel, number, line.strip()[:160]))
                    break
    return hits, len(scope)


def self_test():
    """The gate must fire on each shape it refuses, stay quiet on the ordinary public
    text that looks similar, and stay quiet on its own source.

    The probe fixtures are assembled from fragments because this file is itself in
    scan scope: a fixture written as a literal would be the very thing the gate
    refuses, which would either fail the gate or need an in-file exemption marker -
    and a marker is something a careless commit could use to switch the gate off.
    """
    import tempfile
    sep = chr(92)                                    # a path separator
    win_path = 'workdir: C:' + sep + 'Users' + sep + 'someone' + sep + 'repo'
    posix_path = 'clone into /' + 'Users' + '/someone/projects/app'
    mail = 'contact us at someone@' + 'qq' + '.' + 'com please'
    retired = 'docs live at https://example-old' + '.' + 'cc' + '/guide'
    reserved = 'port: 7' + '301'
    family = 'depends on jffast-' + 'pro-ui for build'
    allowed_shape = 'the published module jffast-' + 'probe is used here'
    probes = (
        (win_path, True),
        (posix_path, True),
        ('see <a href="mail' + 'to:ops@example.com">ops</a>', True),
        (mail, True),
        (retired, True),
        (reserved, True),
        ('path d="M 18.7' + '301 4.2 L 9 9"', False),   # SVG coordinate, not a port
        ('server.port: 7' + '200', False),
        (family, True),
        (allowed_shape, False),
        ('copy this into myprivate-svc before build', False),
    )
    results = []
    with tempfile.TemporaryDirectory() as tmp:
        for text, expect in probes:
            repo = os.path.join(tmp, 'probe-%d' % len(results))
            os.makedirs(repo)
            for leaf in ('probe.md', 'probe.yml', 'probe.java'):
                with open(os.path.join(repo, leaf), 'w', encoding='utf-8', newline='') as h:
                    h.write(text + '\r\n')
            with open(os.path.join(repo, 'pom.xml'), 'w', encoding='utf-8', newline='') as h:
                h.write('<project><artifactId>jffast-' + 'probe</artifactId></project>')
            _commit(repo)
            hits, _scope = scan(repo)
            results.append((text[:58], bool(hits) == expect, hits))
        # A checkout whose build files declare no name must fail closed, not pass.
        empty = os.path.join(tmp, 'empty')
        os.makedirs(empty)
        with open(os.path.join(empty, 'a.md'), 'w', encoding='utf-8') as h:
            h.write('nothing here\r\n')
        _commit(empty)
        results.append(('no declared published name -> error, not clean',
                        _raises(scan, empty), []))
    # This file's own text must be clean under its own rules - otherwise the gate
    # would be the thing it reports, and CI could never go green.
    own = open(os.path.abspath(__file__), encoding='utf-8', errors='replace').read()
    own_hits = [n for n, rule in RULES if rule.search(own)]
    own_hits += [m.group(0) for m in FAMILY.finditer(own)
                 if m.group(0).lower() not in COMPANION | {'jffast-' + 'probe'}]
    results.append(('this gate\'s own source carries no refused literal',
                    not own_hits, own_hits))
    failed = [name for name, ok, _ in results if not ok]
    for name, ok, detail in results:
        print('  [%s] %s%s' % ('ok' if ok else 'FAIL', name,
                               '' if ok else ' -> %s' % (detail or 'no hit')))
    if failed:
        raise GateError('self-test failed for: %s' % ', '.join(failed))


def _commit(repo):
    identity = ['-c', 'user.email=t@t', '-c', 'user.name=t', '-c', 'commit.gpgsign=false']
    for args in (['init', '-q'], ['add', '-A'], identity + ['commit', '-q', '-m', 'probe']):
        subprocess.run(['git', '-C', repo] + args, capture_output=True)


def _raises(func, *args):
    try:
        func(*args)
    except GateError:
        return True
    return False


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--self-test', action='store_true')
    parser.add_argument('--root', default=os.getcwd())
    args = parser.parse_args()
    try:
        if args.self_test:
            self_test()
            print('OK - public hygiene self-test passed')
            return 0
        hits, scope = scan(args.root)
    except GateError as error:
        print('ERROR - the hygiene check did not run: %s' % error)
        return 2
    print('scope = %d tracked text file(s)' % scope)
    if hits:
        for hit in hits[:200]:
            print('  %s' % hit)
        print('FAIL - %d hit(s): vendor-internal or personal data must not be published here'
              % len(hits))
        return 1
    print('PASS - public hygiene clean')
    return 0


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    sys.exit(main())
