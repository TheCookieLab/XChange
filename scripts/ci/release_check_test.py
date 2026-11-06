#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import io
import subprocess
import sys
import tempfile
import unittest
import urllib.error
from contextlib import redirect_stdout
from email.message import Message
from pathlib import Path
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("release_check", Path(__file__).with_name("release-check.py"))
assert SPEC is not None
release = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = release
SPEC.loader.exec_module(release)

GROUP = "com.github.thecookielab.xchange"
STABLE = "1.2.3"
SNAPSHOT = STABLE + "-SNAPSHOT"
RESOLVED = "1.2.3-20261106.120000-1"
SIGNATURE = b"-----BEGIN PGP SIGNATURE-----\nfixture\n-----END PGP SIGNATURE-----\n"


def pom(artifact: str, version: str, *, parent: str | None = None,
        packaging: str = "jar", modules: tuple[str, ...] = (), extra: str = "") -> bytes:
    inheritance = (
        f"<parent><groupId>{GROUP}</groupId><artifactId>xchange-parent</artifactId><version>{parent}</version></parent>"
        if parent else ""
    )
    return (
        '<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>'
        f"{inheritance}<groupId>{GROUP}</groupId><artifactId>{artifact}</artifactId>"
        f"<version>{version}</version><packaging>{packaging}</packaging>"
        + ("<modules>" + "".join(f"<module>{module}</module>" for module in modules) + "</modules>" if modules else "")
        + extra + "</project>"
    ).encode()


def metadata(artifact: str, entries: list[tuple[str, str, str]], version: str = SNAPSHOT) -> bytes:
    return (
        f"<metadata><groupId>{GROUP}</groupId><artifactId>{artifact}</artifactId><version>{version}</version>"
        "<versioning><snapshotVersions>"
        + "".join(
            f"<snapshotVersion><extension>{extension}</extension><classifier>{classifier}</classifier>"
            f"<value>{value}</value></snapshotVersion>" for extension, classifier, value in entries
        ) + "</snapshotVersions></versioning></metadata>"
    ).encode()


class ReleaseCheckTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.write("pom.xml", pom("xchange-parent", SNAPSHOT, packaging="pom", modules=("core",)))
        self.write("core/pom.xml", pom("xchange-core", SNAPSHOT, parent=SNAPSHOT))
        self.write("README.md", (
            f"latest release (`{STABLE}`)\n<xchange.version>{STABLE}</xchange.version>\n`1.2.4-SNAPSHOT`\n"
        ).encode())
        self.parent = release.Project(GROUP, "xchange-parent", SNAPSHOT, "pom", None)
        self.core = release.Project(GROUP, "xchange-core", SNAPSHOT, "jar", (GROUP, "xchange-parent", SNAPSHOT))

    def write(self, path: str, content: bytes) -> None:
        destination = self.root / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(content)

    def admission(self, fetcher, git_status: int = 2) -> None:
        with patch.object(release, "fetch", side_effect=fetcher), patch.object(
            release.subprocess, "run", return_value=subprocess.CompletedProcess([], git_status)
        ), redirect_stdout(io.StringIO()):
            release.preflight(self.root, STABLE, "1.2.4-SNAPSHOT")

    @staticmethod
    def absent(url: str, method: str = "GET") -> bytes:
        raise urllib.error.HTTPError(url, 404, "not found", Message(), None)

    def test_release_inventory_comes_from_immutable_tag_not_later_checkout(self) -> None:
        self.write("pom.xml", pom("xchange-parent", STABLE, packaging="pom", modules=("core",)))
        self.write("core/pom.xml", pom("xchange-core", STABLE, parent=STABLE))
        commands = (
            ["init", "--quiet"],
            ["add", "."],
            ["-c", "user.name=Release Test", "-c", "user.email=release-test@example.invalid",
             "-c", "commit.gpgsign=false", "-c", f"core.hooksPath={self.root / 'no-hooks'}",
             "commit", "--quiet", "-m", "Published fixture"],
            ["-c", "tag.gpgSign=false", "tag", "xchange-parent-" + STABLE],
        )
        for command in commands:
            subprocess.run(["git", *command], cwd=self.root, capture_output=True, check=True)
        self.write("pom.xml", pom("xchange-parent", SNAPSHOT, packaging="pom", modules=("later",)))
        self.write("later/pom.xml", pom("xchange-later", SNAPSHOT, parent=SNAPSHOT))
        self.assertEqual(
            [("xchange-parent", STABLE), ("xchange-core", STABLE)],
            [(project.artifact, project.version) for project in release.reactor(self.root, "refs/tags/xchange-parent-" + STABLE)],
        )

    def test_reactor_discovers_nested_modules_and_inherits_coordinates(self) -> None:
        self.write("pom.xml", pom("xchange-parent", SNAPSHOT, packaging="pom", modules=("aggregate",)))
        self.write("aggregate/pom.xml", pom("aggregate", SNAPSHOT, parent=SNAPSHOT, packaging="pom", modules=("exchange",)))
        self.write("aggregate/exchange/pom.xml", (
            '<project xmlns="http://maven.apache.org/POM/4.0.0"><parent>'
            f'<groupId>{GROUP}</groupId><artifactId>xchange-parent</artifactId><version>{SNAPSHOT}</version>'
            '</parent><artifactId>exchange</artifactId></project>'
        ).encode())
        self.assertEqual(
            [(GROUP, "xchange-parent", SNAPSHOT, "pom"), (GROUP, "aggregate", SNAPSHOT, "pom"), (GROUP, "exchange", SNAPSHOT, "jar")],
            [(project.group, project.artifact, project.version, project.packaging) for project in release.reactor(self.root)],
        )

    def test_inconsistent_reactor_or_parent_cannot_pass_admission(self) -> None:
        for version, parent in (("1.2.2-SNAPSHOT", SNAPSHOT), (SNAPSHOT, "1.2.2-SNAPSHOT")):
            with self.subTest(version=version, parent=parent):
                self.write("core/pom.xml", pom("xchange-core", version, parent=parent))
                with self.assertRaisesRegex(release.CheckError, "Inconsistent reactor"):
                    self.admission(self.absent)

    def test_duplicate_modules_and_coordinates_are_rejected(self) -> None:
        for modules, artifact in ((("core", "core"), "xchange-core"), (("core",), "xchange-parent"), ((".",), "xchange-core")):
            with self.subTest(modules=modules, artifact=artifact):
                self.write("pom.xml", pom("xchange-parent", SNAPSHOT, packaging="pom", modules=modules))
                self.write("core/pom.xml", pom(artifact, SNAPSHOT, parent=SNAPSHOT))
                with self.assertRaisesRegex(release.CheckError, "repeated|Duplicate"):
                    release.reactor(self.root)

    def test_invalid_versions_and_nonadvancing_snapshot_are_rejected(self) -> None:
        for stable, snapshot in ((SNAPSHOT, "1.2.4-SNAPSHOT"), (STABLE, "1.2.4"), (STABLE, SNAPSHOT),
                                 (STABLE, "1.2.2-SNAPSHOT"), ("1.2.3;echo unsafe", "1.2.4-SNAPSHOT"),
                                 ("01.2.3", "1.2.4-SNAPSHOT")):
            with self.subTest(stable=stable, snapshot=snapshot):
                with self.assertRaises(release.CheckError):
                    release.release_versions(stable, snapshot)

    def test_already_advanced_reactor_cannot_repeat_release(self) -> None:
        self.write("pom.xml", pom("xchange-parent", "1.2.4-SNAPSHOT", packaging="pom"))
        with self.assertRaisesRegex(release.CheckError, "Reactor is"):
            self.admission(self.absent)

    def test_stale_documented_stable_or_snapshot_blocks_release(self) -> None:
        for stable, example, snapshot in (("1.2.2", STABLE, "1.2.4-SNAPSHOT"), (STABLE, "1.2.2", "1.2.4-SNAPSHOT"),
                                         (STABLE, STABLE, SNAPSHOT)):
            with self.subTest(stable=stable, example=example, snapshot=snapshot):
                self.write("README.md", f"latest release (`{stable}`)\n<xchange.version>{example}</xchange.version>\n`{snapshot}`\n".encode())
                with self.assertRaisesRegex(release.CheckError, "Update README"):
                    self.admission(self.absent)

    def test_existing_remote_tag_and_unknown_git_result_fail_closed(self) -> None:
        for status, message in ((0, "tag already exists"), (128, "Cannot determine")):
            with self.subTest(status=status), self.assertRaisesRegex(release.CheckError, message):
                self.admission(self.absent, git_status=status)

    def test_child_artifact_collision_blocks_release_without_changing_checkout(self) -> None:
        before = {path.relative_to(self.root): path.read_bytes() for path in self.root.rglob("*") if path.is_file()}

        def fetcher(url: str, method: str) -> bytes:
            if "/xchange-core/" in url:
                return b"existing publication"
            return self.absent(url, method)

        with self.assertRaisesRegex(release.CheckError, "artifact already exists.*xchange-core"):
            self.admission(fetcher)
        self.assertEqual(before, {path.relative_to(self.root): path.read_bytes() for path in self.root.rglob("*") if path.is_file()})

    def test_only_404_can_establish_artifact_absence(self) -> None:
        for status in (401, 403, 429, 500, 503):
            def fetcher(url: str, method: str) -> bytes:
                raise urllib.error.HTTPError(url, status, "unavailable", Message(), None)
            with self.subTest(status=status), self.assertRaisesRegex(release.CheckError, f"HTTP {status}"):
                self.admission(fetcher)
        with self.assertRaisesRegex(release.CheckError, "Cannot establish artifact absence"):
            self.admission(lambda url, method: (_ for _ in ()).throw(urllib.error.URLError("offline")))

    def release_fetcher(self, *, replacement: bytes | None = None, missing: str = "", signature: bytes = SIGNATURE):
        def fetcher(url: str, method: str = "GET") -> bytes:
            if missing and url.endswith(missing):
                return self.absent(url, method)
            if url.endswith(".asc"):
                return signature
            if url.endswith(".pom"):
                return replacement if replacement is not None else pom("xchange-core", STABLE, parent=STABLE)
            return b"jar"
        return fetcher

    def test_published_coordinate_parent_and_snapshot_dependencies_are_rejected(self) -> None:
        variants = (
            pom("different-artifact", STABLE, parent=STABLE),
            pom("xchange-core", "1.2.2", parent=STABLE),
            pom("xchange-core", STABLE, parent="1.2.2"),
            pom("xchange-core", STABLE, parent=STABLE, extra="<dependencies><dependency><version>2.0-SNAPSHOT</version></dependency></dependencies>"),
            pom("xchange-core", STABLE, parent=STABLE, extra="<properties><version.example>2.0-SNAPSHOT</version.example></properties>"),
        )
        for content in variants:
            with self.subTest(content=content), patch.object(release, "fetch", side_effect=self.release_fetcher(replacement=content)):
                with self.assertRaisesRegex(release.CheckError, "POM mismatch|Snapshot dependency"):
                    release.verify_project(self.core, STABLE)

    def test_missing_classifiers_and_signatures_cannot_pass_publication(self) -> None:
        for missing in ("-sources.jar", "-javadoc.jar", ".pom.asc", "-sources.jar.asc", "-javadoc.jar.asc"):
            with self.subTest(missing=missing), patch.object(release, "fetch", side_effect=self.release_fetcher(missing=missing)):
                with self.assertRaisesRegex(release.CheckError, "timed out.*|HTTP 404"):
                    release.verify_artifacts([self.core], STABLE, 0)

    def test_invalid_signature_envelope_is_not_treated_as_published(self) -> None:
        with patch.object(release, "fetch", side_effect=self.release_fetcher(signature=b"<html>missing</html>")):
            with self.assertRaisesRegex(release.CheckError, "Invalid detached signature"):
                release.verify_project(self.core, STABLE)

    def test_snapshot_metadata_rejects_missing_mixed_duplicate_or_wrong_versions(self) -> None:
        entries = [("pom", "", RESOLVED), ("jar", "", RESOLVED), ("jar", "sources", RESOLVED), ("jar", "javadoc", RESOLVED)]
        variants = (
            metadata("xchange-core", entries[:-1]),
            metadata("xchange-core", entries[:-1] + [("jar", "javadoc", "1.2.3-20261106.120001-2")]),
            metadata("xchange-core", entries + [entries[0]]),
            metadata("xchange-core", entries, version="1.2.2-SNAPSHOT"),
            metadata("different-artifact", entries),
            metadata("xchange-core", [(extension, classifier, "1.2.2-20261106.120000-1") for extension, classifier, _ in entries]),
        )
        for content in variants:
            with self.subTest(content=content), patch.object(release, "fetch", return_value=content):
                with self.assertRaisesRegex(release.CheckError, "Snapshot metadata|snapshot artifacts|snapshot entry"):
                    release.verify_project(self.core, SNAPSHOT)

    def test_publication_permission_failure_is_not_propagation(self) -> None:
        def denied(url: str, method: str = "GET") -> bytes:
            raise urllib.error.HTTPError(url, 403, "forbidden", Message(), None)
        with patch.object(release, "fetch", side_effect=denied):
            with self.assertRaises(release.CheckError) as failure:
                release.verify_artifacts([self.core], STABLE, 600)
        self.assertNotIsInstance(failure.exception, release.PublicationPending)
        self.assertIn("HTTP 403", str(failure.exception))

    def test_propagation_wait_expires_with_the_missing_artifact(self) -> None:
        with patch.object(release, "fetch", side_effect=self.absent), patch.object(
            release.time, "monotonic", side_effect=(0, 3)
        ):
            with self.assertRaisesRegex(release.CheckError, "timed out:[\\s\\S]*xchange-core"):
                release.verify_artifacts([self.core], STABLE, 2)


if __name__ == "__main__":
    unittest.main()
