#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Release gate for the packaged Spring Boot jar (stdlib only, no build, no network).

Why this exists
  On 2026-11-02 the backend would not start. The log said:

      NoClassDefFoundError: com/baomidou/mybatisplus/spring/MybatisSqlSessionFactoryBean
        at java.lang.Class.getDeclaredMethods0(Native Method)
        ...
        Error processing condition on MybatisPlusAutoConfiguration.sqlSessionFactory

  The build was green, the unit tests were green and CI was green. The cause was a
  *split family* in the BOM: 3 of MyBatis-Plus's 6 coordinates were pinned at
  3.5.18.1 while the other 3 stayed at 3.5.16. Maven's dependencyManagement
  overrides a transitive version silently, so nothing warned. Upstream, 3.5.17 had
  moved classes between packages, so the auto-configuration was compiled against
  one package name while the jar on the classpath carried another.

  The published jar is the last place that defect can still be caught before a
  user downloads it, and CI deliberately skips the fat jar on every push (it is
  ~109 MB), so the artifact itself was never inspected. This script inspects it.

What it checks
  A. **The packaged artifact agrees with the project's own BOM.**
     A1. Every coordinate the BOM manages that IS packaged must be packaged at
         the BOM's version. (Catches a resolution that did not match the
         declaration: a stale lock, a dependency override, an override in a
         profile that never shows up in the pom.)
     A2. The BOM-managed coordinates of one groupId must not ship at two
         different versions. This is the incident shape itself: the BOM split
         the family and the jar faithfully reproduced the split.

     The scope matters. "One version per groupId" across the WHOLE jar is not
     a usable rule -- org.apache.commons alone ships 6 artifacts at 6 different
     versions (commons-lang3, commons-text, commons-pool2 ...), Guava ships
     guava + failureaccess + listenablefuture, Jackson ships jackson-core
     separately from jackson-annotations. Those are independent artifacts with
     independent release trains, not a split family. Only the coordinates the
     project itself declared are the project's responsibility, so only those
     are judged.

  B. **Every auto-configuration class is present.** The class names come from the
     META-INF/spring/...AutoConfiguration.imports files of the packaged jars, and
     each one must exist as a .class somewhere on the packaged classpath. Nested
     classes are matched in their binary form (Outer$Inner).

  This is a *shape* check on the artifact: it needs no database, no network and no
  JVM. The full startup smoke in .github/workflows/release.yml is the layer above
  it.

Usage
  python .github/scripts/release-gate.py check-jar jffast-boot/target/jffast-boot.jar
  python .github/scripts/release-gate.py check-jar <jar> --bom jffast-bom/pom.xml
  python .github/scripts/release-gate.py --self-test

Exit codes
  0 = the packaged jar is internally consistent
  1 = a split family, a version that contradicts the BOM, or a missing
      auto-configuration class
  2 = the check could not actually run (jar missing / unreadable / BOM missing)
      -- a check that did not run is NOT a clean check
"""
from __future__ import annotations

import argparse
import io
import os
import re
import sys
import xml.etree.ElementTree as ET
import zipfile
from collections import defaultdict
from pathlib import Path

EXIT_OK = 0
EXIT_FAIL = 1
EXIT_PREREQ = 2

AUTO_CONFIG_IMPORTS = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
POM_PROPERTIES = "META-INF/maven/"
MAVEN_DIR = re.compile(r"^META-INF/maven/([^/]+)/([^/]+)/$")
NS = "{http://maven.apache.org/POM/4.0.0}"


class GateError(Exception):
    """The gate could not trust its own inputs."""


# --------------------------------------------------------------------------
# coordinates
# --------------------------------------------------------------------------

def _jar_coordinates(payload: bytes, label: str):
    """(groupId|None, artifactId, version) of a nested jar.

    Three sources, in decreasing authority, because the obvious one is missing
    surprisingly often:

      1. META-INF/maven/<gid>/<aid>/pom.properties  -- authoritative
      2. META-INF/maven/<gid>/<aid>/               -- directory only; the
         version then comes from the file name, which Spring Boot writes as
         <artifactId>-<version>.jar
      3. the file name alone                         -- no groupId

    Source 2 is not a corner case: 105 of the 263 jars in the fat jar ship
    without a pom.properties -- including every single mybatis-plus jar, which
    is precisely the family that caused the incident. A gate that reads
    versions only from pom.properties is blind on exactly the thing it was
    written for.
    """
    try:
        with zipfile.ZipFile(io.BytesIO(payload)) as archive:
            names = archive.namelist()
            group = artifact = version = None
            for name in names:
                if name.startswith(POM_PROPERTIES) and name.endswith("/pom.properties"):
                    properties = {}
                    for line in archive.read(name).decode("utf-8", "replace").splitlines():
                        if "=" not in line:
                            continue
                        key, _, value = line.partition("=")
                        properties[key.strip()] = value.strip()
                    group = properties.get("groupId") or group
                    artifact = properties.get("artifactId") or artifact
                    version = properties.get("version") or version
                    break
            if not artifact:
                for name in names:
                    match = MAVEN_DIR.match(name)
                    if match:
                        group = group or match.group(1)
                        artifact = match.group(2)
                        break
            if artifact and not version:
                base = label.rsplit("/", 1)[-1]
                if base.endswith(".jar"):
                    base = base[:-4]
                prefix = artifact + "-"
                if base.startswith(prefix):
                    version = base[len(prefix):]
            if not artifact:
                # Last resort: the file name Spring Boot writes as
                # <artifactId>-<version>.jar. The version is the trailing segment
                # that starts with a digit (guava-33.4.8-jre -> 33.4.8-jre).
                base = label.rsplit("/", 1)[-1]
                if base.endswith(".jar"):
                    base = base[:-4]
                for index in range(base.rfind("-"), 0, -1):
                    tail = base[index + 1:]
                    if tail[:1].isdigit():
                        artifact = base[:index]
                        version = version or tail
                        break
            if artifact:
                return group, artifact, version or "(unknown)"
            return None
    except (OSError, zipfile.BadZipFile) as error:
        raise GateError("cannot read %s: %s" % (label, error))


def _read_bom(bom_path: str):
    """({(groupId, artifactId): version}, {groupId}) from a BOM pom.

    ${project.version} is resolved against the BOM's own version, because that
    is what Maven resolves it to.
    """
    if not os.path.isfile(bom_path):
        raise GateError("BOM not found: %s -- the gate compares the artifact "
                        "against the project's own BOM, so it needs it" % bom_path)
    try:
        root = ET.parse(bom_path).getroot()
    except (OSError, ET.ParseError) as error:
        raise GateError("cannot read %s: %s" % (bom_path, error))

    project_version = (root.findtext(NS + "version") or "").strip()
    parent = root.find(NS + "parent")
    if not project_version and parent is not None:
        project_version = (parent.findtext(NS + "version") or "").strip()

    managed = {}
    section = root.find(NS + "dependencyManagement")
    if section is None:
        raise GateError("%s carries no dependencyManagement -- nothing to compare "
                        "the artifact against" % bom_path)
    container = section.find(NS + "dependencies")
    if container is None:
        raise GateError("%s has no <dependencies> inside <dependencyManagement>"
                        % bom_path)
    for dep in container.findall(NS + "dependency"):
        group = (dep.findtext(NS + "groupId") or "").strip()
        artifact = (dep.findtext(NS + "artifactId") or "").strip()
        version = (dep.findtext(NS + "version") or "").strip()
        if not group or not artifact:
            continue
        version = version.replace("${project.version}", project_version)
        if "${" in version:
            # An unresolved property: we cannot compare it, so say so instead of
            # pretending we checked it.
            continue
        managed[(group, artifact)] = version
    if not managed:
        raise GateError("%s manages no coordinate -- the comparison would be vacuous"
                        % bom_path)
    groups = {group for group, _ in managed}
    return managed, groups


# --------------------------------------------------------------------------
# classpath inspection
# --------------------------------------------------------------------------

def _nested_jars(outer_jar: str):
    try:
        with zipfile.ZipFile(outer_jar) as archive:
            return [n for n in archive.namelist()
                    if n.startswith("BOOT-INF/lib/") and n.endswith(".jar")]
    except (OSError, zipfile.BadZipFile) as error:
        raise GateError("cannot read %s: %s" % (outer_jar, error))


def _nested_entries(outer_jar: str):
    """class file path -> True, for everything packaged in the fat jar."""
    entries = set()
    with zipfile.ZipFile(outer_jar) as archive:
        for name in archive.namelist():
            if name.startswith("BOOT-INF/classes/") and name.endswith(".class"):
                entries.add(name[len("BOOT-INF/classes/"):])
            elif name.startswith("BOOT-INF/lib/") and name.endswith(".jar"):
                with zipfile.ZipFile(io.BytesIO(archive.read(name))) as inner:
                    for entry in inner.namelist():
                        if entry.endswith(".class"):
                            entries.add(entry)
    return entries


def _auto_configuration_classes(outer_jar: str):
    """The auto-configuration class names the packaged jars register."""
    names = set()
    with zipfile.ZipFile(outer_jar) as archive:
        for name in archive.namelist():
            if not (name.startswith("BOOT-INF/lib/") and name.endswith(".jar")):
                continue
            with zipfile.ZipFile(io.BytesIO(archive.read(name))) as inner:
                if AUTO_CONFIG_IMPORTS not in inner.namelist():
                    continue
                text = inner.read(AUTO_CONFIG_IMPORTS).decode("utf-8", "replace")
                for line in text.splitlines():
                    line = line.strip()
                    if line and not line.startswith("#"):
                        names.add(line)
    return names


def _class_candidates(class_name: str):
    """Every binary name a registered class could have.

    Spring Boot's imports file may list a nested class with dots
    (org.example.Outer.Inner); the class file is Outer$Inner.class. Both shapes
    are generated so a nested registration is not reported as missing.
    """
    parts = class_name.split(".")
    candidates = []
    for depth in range(1, len(parts) + 1):
        package = parts[:-depth]
        classes = parts[-depth:]
        candidates.append("/".join(package + ["$".join(classes)]) + ".class")
    return candidates


# --------------------------------------------------------------------------
# the gate
# --------------------------------------------------------------------------

def check_jar(jar_path: str, bom_path: str) -> int:
    if not os.path.isfile(jar_path):
        raise GateError("jar not found: %s" % jar_path)

    nested = _nested_jars(jar_path)
    if not nested:
        raise GateError("%s contains no BOOT-INF/lib jars - is it a Spring Boot fat jar?"
                        % jar_path)

    managed, managed_groups = _read_bom(bom_path)

    print("jar: %s" % jar_path)
    print("bom: %s (%d managed coordinate(s))" % (bom_path, len(managed)))
    print("     %d packaged jar(s)" % len(nested))
    print("")

    failures = 0

    # --- inventory --------------------------------------------------------
    versions = defaultdict(set)
    artifacts = defaultdict(set)
    unversioned = []
    packaged = []
    with zipfile.ZipFile(jar_path) as outer:
        for name in nested:
            coords = _jar_coordinates(outer.read(name), name)
            if not coords:
                unversioned.append(name)
                continue
            group, artifact, version = coords
            packaged.append((group, artifact, version, name))
            if group:
                versions[group].add(version)
                artifacts[group].add(artifact)
            if version == "(unknown)":
                unversioned.append(name)

    # --- A1. packaged coordinates agree with the BOM ----------------------
    print("A) the packaged artifact agrees with the BOM")
    by_artifact = defaultdict(list)
    for group, artifact, version, name in packaged:
        by_artifact[artifact].append((group, version, name))

    def matches(group, artifact):
        """The packaged jars that can be this BOM coordinate.

        A jar whose groupId could not be resolved (no pom.properties, no
        META-INF/maven directory) is matched by artifactId alone, but only
        when that artifactId names exactly one BOM-managed coordinate -- an
        ambiguous match would be a guess, not a check.
        """
        found = [entry for entry in by_artifact.get(artifact, [])
                 if entry[0] == group]
        if found:
            return found
        if not group:
            return []
        ambiguous = [entry for entry in by_artifact.get(artifact, []) if entry[0] is None]
        if ambiguous and len({a for a, _ in managed.items() if a[1] == artifact}) == 1:
            return ambiguous
        return []

    mismatches = []
    for (group, artifact), expected in sorted(managed.items()):
        for found_group, version, name in matches(group, artifact):
            if version != expected:
                mismatches.append((group, artifact, expected, version, name))
    if mismatches:
        failures += 1
        print("  [FAIL] %d packaged coordinate(s) contradict the BOM:"
              % len(mismatches))
        for group, artifact, expected, version, name in mismatches:
            print("           %s:%s is packaged at %s, the BOM says %s"
                  % (group, artifact, version, expected))
            print("             (%s)" % name.rsplit("/", 1)[-1])
        print("           The jar a user downloads is not the one the pom declares.")
    else:
        checked = sum(1 for (group, artifact) in managed if matches(group, artifact))
        print("  [OK  ] all %d packaged BOM-managed coordinate(s) ship at the "
              "BOM's version" % checked)

    # --- A2. no split family among the BOM-managed coordinates -------------
    print("")
    print("A2) no BOM-managed family ships at two versions")
    family_versions = defaultdict(set)
    family_members = defaultdict(set)
    for group, artifact in managed:
        for found_group, version, name in matches(group, artifact):
            family_versions[group].add(version)
            family_members[group].add(artifact)
    split_groups = []
    for group in sorted(family_versions):
        found = family_versions[group]
        if len(found) == 1:
            version = next(iter(found))
            print("  [OK  ] %-44s %s (%d artifact(s))"
                  % (group, version, len(family_members[group])))
            continue
        failures += 1
        split_groups.append(group)
        print("  [FAIL] %s ships at %d different versions -- a split family."
              % (group, len(found)))
        for version in sorted(found):
            print("           %s" % version)
        print("           This is the shape of the 2026-11-02 incident: upstream moved a")
        print("           class between packages, the BOM pinned the family at two")
        print("           versions, and the app died with NoClassDefFoundError at startup.")
    if not split_groups:
        print("")

    # --- inventory (informational) ----------------------------------------
    other = [g for g in sorted(versions) if g not in managed_groups]
    if other:
        print("")
        print("INFO) other families in the artifact (not managed by this BOM, so")
        print("     not judged: independent artifacts with independent release")
        print("     trains are normal, e.g. org.apache.commons ships 6 artifacts")
        print("     at 6 versions):")
        for group in other:
            found = versions[group]
            print("     %-44s %s (%d artifact(s))"
                  % (group, ", ".join(sorted(found)), len(artifacts[group])))
    if unversioned:
        print("")
        print("  [NOTE] %d packaged jar(s) carry no resolvable coordinates and were "
              "not version-checked" % len(set(unversioned)))

    # --- B. every auto-configuration class is on the packaged classpath --
    print("")
    print("B) every registered auto-configuration class is packaged")
    classes = _auto_configuration_classes(jar_path)
    entries = _nested_entries(jar_path)
    if not classes:
        raise GateError("no AutoConfiguration.imports found in the packaged jars - "
                        "the check did not actually run")
    missing = []
    for class_name in sorted(classes):
        if not any(candidate in entries for candidate in _class_candidates(class_name)):
            missing.append(class_name)
    if missing:
        failures += 1
        print("  [FAIL] %d auto-configuration class(es) are registered but not packaged:"
              % len(missing))
        for name in missing:
            print("           %s" % name)
    else:
        print("  [OK  ] %d auto-configuration class(es) are packaged" % len(classes))

    print("")
    if failures:
        print("FAIL - %d problem(s) in the packaged jar" % failures)
        return EXIT_FAIL
    print("PASS - the packaged jar is internally consistent")
    return EXIT_OK


def _default_bom() -> str:
    """The BOM next to this script's repository root (.github/scripts/ -> root)."""
    return str(Path(__file__).resolve().parents[2] / "jffast-bom" / "pom.xml")


def self_test() -> int:
    """Prove the gate rejects a jar that really is inconsistent."""
    import tempfile

    checks = []

    def make_fat_jar(path: str, nested: dict) -> None:
        with zipfile.ZipFile(path, "w") as outer:
            for name, payload in nested.items():
                outer.writestr(name, payload)

    def make_nested_jar(coordinates, packaged_classes=(), registered=(),
                        with_pom_properties=True, maven_dir=True) -> bytes:
        """A nested jar. `packaged_classes` land as .class files, `registered` only
        in the auto-configuration manifest (so a class can be registered but absent)."""
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w") as inner:
            if coordinates:
                group, artifact, version = coordinates
                base = "META-INF/maven/%s/%s/" % (group, artifact)
                if maven_dir:
                    inner.writestr(base, "")
                if with_pom_properties:
                    inner.writestr(base + "pom.properties",
                                   "groupId=%s\nartifactId=%s\nversion=%s\n"
                                   % (group, artifact, version))
            for class_name in packaged_classes:
                inner.writestr(class_name.replace(".", "/") + ".class", b"\xca\xfe\xba\xbe")
            if registered:
                inner.writestr(AUTO_CONFIG_IMPORTS, "\n".join(registered) + "\n")
        return buffer.getvalue()

    def make_bom(path: str, entries) -> str:
        body = "".join(
            "      <dependency><groupId>%s</groupId><artifactId>%s</artifactId>"
            "<version>%s</version></dependency>\n" % (group, artifact, version)
            for group, artifact, version in entries)
        Path(path).write_text(
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<project xmlns="http://maven.apache.org/POM/4.0.0">\n'
            '  <modelVersion>4.0.0</modelVersion>\n'
            '  <groupId>cn.jfspeed</groupId>\n'
            '  <artifactId>jffast-bom</artifactId>\n'
            '  <version>1.0.0</version>\n'
            '  <dependencyManagement>\n    <dependencies>\n'
            + body +
            '    </dependencies>\n  </dependencyManagement>\n</project>\n',
            encoding="utf-8")
        return path

    tmp = tempfile.mkdtemp(prefix="release-gate-")
    autoconfig = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
    mp_core = ("com.baomidou", "mybatis-plus-core", "3.5.18.1")
    mp_spring = ("com.baomidou", "mybatis-plus-spring", "3.5.18.1")

    bom_ok = make_bom(os.path.join(tmp, "bom-ok.xml"),
                       [mp_core, mp_spring,
                        ("cn.jfspeed", "jffast-system", "1.0.0")])
    bom_split = make_bom(os.path.join(tmp, "bom-split.xml"),
                         [mp_core, ("com.baomidou", "mybatis-plus-spring", "3.5.16"),
                          ("cn.jfspeed", "jffast-system", "1.0.0")])

    # a consistent jar must pass
    good = os.path.join(tmp, "good.jar")
    make_fat_jar(good, {
        "BOOT-INF/lib/mybatis-plus-spring-boot-autoconfigure-3.5.18.1.jar":
            make_nested_jar(("com.baomidou", "mybatis-plus-spring-boot-autoconfigure", "3.5.18.1"),
                            packaged_classes=[autoconfig], registered=[autoconfig]),
        "BOOT-INF/lib/mybatis-plus-core-3.5.18.1.jar":
            make_nested_jar(mp_core),
        "BOOT-INF/lib/mybatis-plus-spring-3.5.18.1.jar":
            make_nested_jar(mp_spring),
        "BOOT-INF/lib/jffast-system-1.0.0.jar":
            make_nested_jar(("cn.jfspeed", "jffast-system", "1.0.0")),
    })
    code = check_jar(good, bom_ok)
    checks.append(("a consistent jar is accepted (exit 0)", code == EXIT_OK))

    # a split family must be rejected -- the incident shape
    split = os.path.join(tmp, "split.jar")
    make_fat_jar(split, {
        "BOOT-INF/lib/mybatis-plus-core-3.5.18.1.jar":
            make_nested_jar(mp_core, packaged_classes=[autoconfig], registered=[autoconfig]),
        "BOOT-INF/lib/mybatis-plus-spring-3.5.16.jar":
            make_nested_jar(("com.baomidou", "mybatis-plus-spring", "3.5.16")),
    })
    code = check_jar(split, bom_split)
    checks.append(("a split family is rejected (exit 1)", code == EXIT_FAIL))

    # a packaged version that contradicts the BOM must be rejected
    contradicting = os.path.join(tmp, "contradicting.jar")
    make_fat_jar(contradicting, {
        "BOOT-INF/lib/mybatis-plus-core-3.5.16.jar":
            make_nested_jar(("com.baomidou", "mybatis-plus-core", "3.5.16"),
                            packaged_classes=[autoconfig], registered=[autoconfig]),
    })
    code = check_jar(contradicting, bom_ok)
    checks.append(("a packaged version that contradicts the BOM is rejected (exit 1)",
                   code == EXIT_FAIL))

    # a jar whose versions are only recoverable from the file name (no
    # pom.properties) must still be checked -- this is the real fat jar's shape
    no_props = os.path.join(tmp, "no-props.jar")
    make_fat_jar(no_props, {
        "BOOT-INF/lib/mybatis-plus-core-3.5.16.jar":
            make_nested_jar(("com.baomidou", "mybatis-plus-core", "3.5.16"),
                            packaged_classes=[autoconfig], registered=[autoconfig],
                            with_pom_properties=False),
    })
    code = check_jar(no_props, bom_ok)
    checks.append(("a version without pom.properties is still checked (exit 1)",
                   code == EXIT_FAIL))

    # a registered but unpackaged auto-configuration must be rejected
    missing_class = os.path.join(tmp, "missing.jar")
    make_fat_jar(missing_class, {
        "BOOT-INF/lib/mybatis-plus-core-3.5.18.1.jar":
            make_nested_jar(mp_core,
                            registered=["com.baomidou.mybatisplus.autoconfigure.MissingOnPurpose"]),
    })
    code = check_jar(missing_class, bom_ok)
    checks.append(("a registered but unpackaged auto-configuration is rejected (exit 1)",
                   code == EXIT_FAIL))

    # a nested auto-configuration class must be found in its binary form
    nested_class = os.path.join(tmp, "nested.jar")
    make_fat_jar(nested_class, {
        "BOOT-INF/lib/springdoc-3.1.1.jar":
            make_nested_jar(("org.springdoc", "springdoc", "3.1.1"),
                            packaged_classes=["org.springdoc.core.configuration."
                                              "SpringDocWebServerConfiguration$"
                                              "DeployedWebServerConfiguration"],
                            registered=["org.springdoc.core.configuration."
                                        "SpringDocWebServerConfiguration."
                                        "DeployedWebServerConfiguration"]),
    })
    code = check_jar(nested_class, bom_ok)
    checks.append(("a nested registered class is found in binary form (exit 0)",
                   code == EXIT_OK))

    # a jar that is not a Spring Boot fat jar must fail closed
    plain = os.path.join(tmp, "plain.jar")
    make_fat_jar(plain, {"META-INF/MANIFEST.MF": "Manifest-Version: 1.0\n"})
    try:
        check_jar(plain, bom_ok)
        checks.append(("a non-fat jar fails closed (exit 2)", False))
    except GateError:
        checks.append(("a non-fat jar fails closed (exit 2)", True))

    # a missing jar must fail closed
    try:
        check_jar(os.path.join(tmp, "does-not-exist.jar"), bom_ok)
        checks.append(("a missing jar fails closed (exit 2)", False))
    except GateError:
        checks.append(("a missing jar fails closed (exit 2)", True))

    # a missing BOM must fail closed -- otherwise the comparison is vacuous
    try:
        check_jar(good, os.path.join(tmp, "no-such-bom.xml"))
        checks.append(("a missing BOM fails closed (exit 2)", False))
    except GateError:
        checks.append(("a missing BOM fails closed (exit 2)", True))

    failed = [name for name, ok in checks if not ok]
    for name, ok in checks:
        print("  [%s] %s" % ("OK  " if ok else "FAIL", name))
    if failed:
        print("FAIL - %d self-test assertion(s)" % len(failed))
        return EXIT_FAIL
    print("PASS - the gate rejects what it claims to reject")
    return EXIT_OK


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("command", choices=("check-jar",), nargs="?")
    parser.add_argument("jar", nargs="?", help="path to the packaged fat jar")
    parser.add_argument("--bom", default=_default_bom(),
                        help="the BOM to compare the artifact against "
                             "(default: jffast-bom/pom.xml next to this script's repo root)")
    parser.add_argument("--self-test", action="store_true",
                        help="prove the gate rejects a really inconsistent jar")
    args = parser.parse_args()
    if args.self_test:
        return self_test()
    if not args.command or not args.jar:
        parser.error("check-jar needs the path to the fat jar")
    return check_jar(args.jar, args.bom)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except GateError as error:
        print("  [ERROR] %s" % error)
        print("FAIL - the release gate could not trust its own inputs; an unreadable "
              "check is never a clean check")
        sys.exit(EXIT_PREREQ)
