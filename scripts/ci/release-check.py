#!/usr/bin/env python3
"""Read-only release admission and public-repository consumer verification."""

from __future__ import annotations

import argparse
import concurrent.futures
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path


CENTRAL = "https://repo.maven.apache.org/maven2"
SNAPSHOTS = "https://central.sonatype.com/repository/maven-snapshots"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
VERSION = r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)"
ROOT = Path(__file__).resolve().parents[2]


class CheckError(Exception):
    """A failed release invariant, not a reason to retry publication."""


class PublicationPending(CheckError):
    """An unavailable public artifact which may still propagate."""


@dataclass(frozen=True)
class Project:
    group: str
    artifact: str
    version: str
    packaging: str
    parent: tuple[str, str, str] | None

    def directory(self, version: str) -> str:
        return f"{self.group.replace('.', '/')}/{self.artifact}/{version}"


def text(element: ET.Element, path: str) -> str:
    return element.findtext(path, default="", namespaces=NS).strip()


def model(element: ET.Element) -> Project:
    parent = (
        text(element, "m:parent/m:groupId"),
        text(element, "m:parent/m:artifactId"),
        text(element, "m:parent/m:version"),
    )
    project = Project(
        text(element, "m:groupId") or parent[0],
        text(element, "m:artifactId"),
        text(element, "m:version") or parent[2],
        text(element, "m:packaging") or "jar",
        parent if any(parent) else None,
    )
    for value in (project.group, project.artifact, project.version, *(project.parent or ())):
        if not re.fullmatch(r"[A-Za-z0-9_.-]+", value):
            raise CheckError(f"Unresolved or invalid POM coordinate: {value!r}")
    if project.packaging not in {"pom", "jar"}:
        raise CheckError(f"Unsupported publication packaging for {project.artifact}: {project.packaging}")
    return project


def reactor(root: Path, ref: str | None = None) -> list[Project]:
    projects: list[Project] = []
    visited: set[Path] = set()

    def visit(pom: Path) -> None:
        pom = pom.resolve()
        if not pom.is_relative_to(root.resolve()) or pom in visited:
            raise CheckError(f"Module escapes the reactor or is repeated: {pom}")
        visited.add(pom)
        if ref:
            source = f"{ref}:{pom.relative_to(root.resolve()).as_posix()}"
            result = subprocess.run(["git", "show", source], cwd=root, capture_output=True, check=False)
            if result.returncode:
                raise CheckError(f"Cannot read canonical release POM {source}; fetch the release tag from origin first")
            element = ET.fromstring(result.stdout)
        else:
            element = ET.parse(pom).getroot()
        projects.append(model(element))
        for module in element.findall("m:modules/m:module", NS):
            visit(pom.parent / (module.text or "").strip() / "pom.xml")

    visit(root / "pom.xml")
    coordinates = {(project.group, project.artifact) for project in projects}
    if len(coordinates) != len(projects):
        raise CheckError("Duplicate reactor coordinates")
    for project in projects:
        if project.group != projects[0].group or project.version != projects[0].version:
            raise CheckError(f"Inconsistent reactor version or group: {project}")
        if project.parent and project.parent[0] == projects[0].group:
            if project.parent[:2] not in coordinates or project.parent[2] != projects[0].version:
                raise CheckError(f"Inconsistent reactor parent: {project.artifact}: {project.parent}")
    return projects


def fetch(url: str, method: str = "GET") -> bytes:
    request = urllib.request.Request(url, method=method, headers={"User-Agent": "XChange-release-check"})
    with urllib.request.urlopen(request, timeout=30) as response:
        if response.status != 200:
            raise CheckError(f"Unexpected HTTP {response.status}: {url}")
        return response.read()


def release_versions(release: str, snapshot: str) -> None:
    if not re.fullmatch(VERSION, release) or not re.fullmatch(VERSION + r"-SNAPSHOT", snapshot):
        raise CheckError("Expected release X.Y.Z and next snapshot X.Y.Z-SNAPSHOT")
    if tuple(map(int, snapshot.removesuffix("-SNAPSHOT").split("."))) <= tuple(map(int, release.split("."))):
        raise CheckError("Next snapshot version must be newer than the release")


def documented_versions(root: Path, release: str, snapshot: str) -> None:
    readme = (root / "README.md").read_text(encoding="utf-8")
    examples = re.findall(r"<xchange.version>([^<]+)</xchange.version>", readme)
    latest = re.findall(r"latest release \(`([^`]+)`\)", readme)
    snapshots = re.findall(r"`(" + VERSION + r"-SNAPSHOT)`", readme)
    if not examples or not latest or set(examples + latest) != {release} or set(snapshots) != {snapshot}:
        raise CheckError(f"Update README stable examples to {release} and snapshot instructions to {snapshot} before release")


def preflight(root: Path, release: str, snapshot: str) -> None:
    release_versions(release, snapshot)
    projects = reactor(root)
    if projects[0].version != release + "-SNAPSHOT":
        raise CheckError(f"Reactor is {projects[0].version}; expected {release}-SNAPSHOT")
    documented_versions(root, release, snapshot)
    tag = f"{projects[0].artifact}-{release}"
    result = subprocess.run(
        ["git", "ls-remote", "--exit-code", "origin", f"refs/tags/{tag}"],
        cwd=root, capture_output=True, text=True, check=False,
    )
    if result.returncode == 0:
        raise CheckError(f"Release tag already exists: {tag}; inspect the original release, do not rerun preparation")
    if result.returncode != 2:
        raise CheckError(f"Cannot determine whether remote tag {tag} exists (git exit {result.returncode})")

    def absent(project: Project) -> None:
        url = f"{CENTRAL}/{project.directory(release)}/{project.artifact}-{release}.pom"
        try:
            fetch(url, "HEAD")
        except urllib.error.HTTPError as error:
            with error:
                if error.code == 404:
                    return
                raise CheckError(f"Cannot establish artifact absence: HTTP {error.code}: {url}") from error
        except (urllib.error.URLError, TimeoutError, OSError) as error:
            raise CheckError(f"Cannot establish artifact absence: {url}: {error}") from error
        raise CheckError(f"Release artifact already exists: {url}; published versions are immutable")

    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(absent, projects))
    print(f"Preflight passed: {tag}, {len(projects)} unused artifact coordinates, README and reactor versions aligned")


def published(url: str, method: str = "GET") -> bytes:
    try:
        return fetch(url, method)
    except urllib.error.HTTPError as error:
        with error:
            message = f"HTTP {error.code}: {url}"
            if error.code in {404, 429} or 500 <= error.code < 600:
                raise PublicationPending(message) from error
            raise CheckError(message) from error
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        raise PublicationPending(f"Cannot fetch {url}: {error}") from error


def published_xml(url: str) -> ET.Element:
    try:
        return ET.fromstring(published(url))
    except ET.ParseError as error:
        raise CheckError(f"Malformed published XML: {url}: {error}") from error


def snapshot_files(project: Project, version: str, directory: str) -> dict[tuple[str, str], str]:
    url = directory + "/maven-metadata.xml"
    metadata = published_xml(url)
    if tuple(metadata.findtext(key) for key in ("groupId", "artifactId", "version")) != (project.group, project.artifact, version):
        raise CheckError(f"Snapshot metadata coordinates do not match {project.artifact}:{version}: {url}")
    required = {("pom", "")} if project.packaging == "pom" else {("pom", ""), ("jar", ""), ("jar", "sources"), ("jar", "javadoc")}
    entries: dict[tuple[str, str], str] = {}
    for entry in metadata.findall("versioning/snapshotVersions/snapshotVersion"):
        key = (entry.findtext("extension", ""), entry.findtext("classifier", ""))
        if key in required:
            value = entry.findtext("value", "")
            if key in entries or not re.fullmatch(re.escape(version.removesuffix("-SNAPSHOT")) + r"-\d{8}\.\d{6}-[1-9]\d*", value):
                raise CheckError(f"Invalid or duplicate snapshot entry for {project.artifact}: {key}: {url}")
            entries[key] = value
    if entries.keys() != required or len(set(entries.values())) != 1:
        raise CheckError(f"Missing or mixed snapshot artifacts for {project.artifact}: {url}")
    return entries


def verify_project(project: Project, version: str) -> None:
    snapshot = version.endswith("-SNAPSHOT")
    directory = f"{SNAPSHOTS if snapshot else CENTRAL}/{project.directory(version)}"
    files = snapshot_files(project, version, directory) if snapshot else {
        key: version for key in ({("pom", "")} if project.packaging == "pom" else {("pom", ""), ("jar", ""), ("jar", "sources"), ("jar", "javadoc")})
    }
    for (extension, classifier), resolved in sorted(files.items()):
        name = f"{project.artifact}-{resolved}{'-' + classifier if classifier else ''}.{extension}"
        url = f"{directory}/{name}"
        if extension == "pom":
            element = published_xml(url)
            actual = model(element)
            expected_parent = (*project.parent[:2], version) if project.parent and project.parent[0] == project.group else project.parent
            if actual != Project(project.group, project.artifact, version, project.packaging, expected_parent):
                raise CheckError(f"Published POM mismatch: {url}: {actual}")
            versions = element.findall(".//m:version", NS) + element.findall("m:properties/*", NS)
            if not snapshot and any((node.text or "").strip().endswith("-SNAPSHOT") for node in versions):
                raise CheckError(f"Snapshot dependency, property or parent in release POM: {url}")
        else:
            published(url, "HEAD")
        if not snapshot:
            signature = published(url + ".asc")
            if not signature.startswith(b"-----BEGIN PGP SIGNATURE-----"):
                raise CheckError(f"Invalid detached signature envelope: {url}.asc")


def verify_artifacts(projects: list[Project], version: str, wait_seconds: float) -> None:
    deadline = time.monotonic() + wait_seconds
    pending = projects
    while pending:
        def check(project: Project) -> tuple[Project, str] | None:
            try:
                verify_project(project, version)
            except PublicationPending as error:
                return project, str(error)
            return None

        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            failures = [failure for failure in pool.map(check, pending) if failure is not None]
        if not failures:
            return
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise CheckError("Publication verification timed out:\n" + "\n".join(message for _, message in failures))
        print(f"Waiting for {len(failures)} artifact publications; {max(0, int(remaining))}s remain", flush=True)
        time.sleep(min(10, remaining))
        pending = [project for project, _ in failures]


def consumer(root: Path, version: str) -> None:
    maven = shutil.which("mvn.cmd" if os.name == "nt" else "mvn")
    if not maven:
        raise CheckError("Maven is required for the isolated published-artifact consumer")
    with tempfile.TemporaryDirectory(prefix="xchange-published-consumer-") as temporary:
        directory = Path(temporary)
        project = directory / "consumer"
        shutil.copytree(root / "scripts/ci/release-consumer", project, ignore=shutil.ignore_patterns("target"))
        subprocess.run(
            [maven, "--batch-mode", "--no-transfer-progress", f"-Dmaven.repo.local={directory / 'repository'}",
             f"-Dxchange.version={version}", f"-Dexec.args={version}", "compile", "exec:java"],
            cwd=project, check=True,
        )
    print(f"Isolated consumer passed: core + Bitstamp {version}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT, help="Reactor checkout (defaults to this script's repository)")
    commands = parser.add_subparsers(dest="command", required=True)
    admission = commands.add_parser("preflight", help="Reject unsafe versions, tag reuse and artifact collisions; no mutations")
    admission.add_argument("--release-version", required=True)
    admission.add_argument("--next-snapshot-version", required=True)
    verification = commands.add_parser("verify", help="Verify every public reactor artifact, then compile and run an isolated consumer")
    verification.add_argument("--version", required=True)
    verification.add_argument("--wait-seconds", type=float, default=600, help="Propagation wait budget; individual HTTP requests time out after 30s")
    args = parser.parse_args()
    try:
        if args.command == "preflight":
            preflight(args.root, args.release_version, args.next_snapshot_version)
        else:
            if not re.fullmatch(VERSION + r"(?:-SNAPSHOT)?", args.version) or not 0 <= args.wait_seconds <= 3600:
                raise CheckError("Expected version X.Y.Z[-SNAPSHOT] and wait-seconds between 0 and 3600")
            if args.version.endswith("-SNAPSHOT"):
                projects = reactor(args.root)
            else:
                parent = model(ET.parse(args.root / "pom.xml").getroot())
                projects = reactor(args.root, f"refs/tags/{parent.artifact}-{args.version}")
                if projects[0].version != args.version:
                    raise CheckError(f"Release tag reactor is {projects[0].version}; expected {args.version}")
            verify_artifacts(projects, args.version, args.wait_seconds)
            print(f"Verified {len(projects)} published POMs and their binaries, sources, Javadocs{' and signatures' if not args.version.endswith('-SNAPSHOT') else ''}", flush=True)
            consumer(args.root, args.version)
    except (CheckError, ET.ParseError, OSError, subprocess.CalledProcessError) as error:
        print(f"Release check failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
