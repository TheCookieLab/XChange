#!/usr/bin/env python3
"""Run one XChange integration module and classify transient CI failures."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path
from typing import Any
import xml.etree.ElementTree as ET


OUTPUT_DIR = Path("target/it-ci")
DEFAULT_STREAK_THRESHOLD = 3
SECRET_VALUE_PATTERN = re.compile(
        r"(?i)(authorization:\s*bearer\s+|"
        r"(?:api[_-]?key|secret|token|password|passphrase)\s*[=:]\s*)"
        r"([^\s,;]+)"
)


TRANSIENT_PATTERNS: tuple[tuple[str, str, str], ...] = (
        ("http_429", r"\b(?:HTTP(?: status code was not OK:| response code:| status)?\s*)429\b|Too Many Requests", "HTTP 429"),
        ("http_502", r"\b(?:HTTP(?: status code was not OK:| response code:| status)?\s*)502\b|Bad Gateway", "HTTP 502"),
        ("http_503", r"\b(?:HTTP(?: status code was not OK:| response code:| status)?\s*)503\b|Service Unavailable", "HTTP 503"),
        ("http_504", r"\b(?:HTTP(?: status code was not OK:| response code:| status)?\s*)504\b|Gateway Timeout", "HTTP 504"),
        ("timeout", r"SocketTimeoutException|Read timed out|connect timed out|timed out|OperationTimeoutException|\btimeout\b", "timeout"),
        ("dns", r"UnknownHostException|Name or service not known|Temporary failure in name resolution", "DNS failure"),
        ("connection", r"Connection reset|Connection refused|ConnectException|NoHttpResponseException|Remote host terminated|connection closed", "connection failure"),
        ("websocket_disconnect", r"websocket.*(?:disconnect|closed|handshake|timeout)|handshake.*(?:timed out|timeout)", "websocket disconnect"),
        ("rate_limit", r"\brate limit(?:ed| exceeded)?\b|FrequencyLimitExceededException|RateLimitExceededException", "rate limit"),
)

JACKSON_PATTERN = re.compile(
        r"com\.fasterxml\.jackson\.databind|JsonMappingException|InvalidFormatException|MismatchedInputException",
        re.IGNORECASE,
)
COMPILATION_PATTERN = re.compile(r"COMPILATION ERROR|Failed to execute goal .*maven-compiler-plugin", re.IGNORECASE)
REPO_NPE_PATTERN = re.compile(r"NullPointerException[\s\S]+org\.knowm\.xchange", re.IGNORECASE)


@dataclass(frozen=True)
class FailureRecord:
    kind: str
    test_class: str
    test_name: str
    type_name: str
    message: str
    text: str


def run_maven(module_name: str, log_path: Path, redacted_log_path: Path) -> int:
    command = [
            "mvn",
            "--batch-mode",
            "--no-transfer-progress",
            "--projects",
            f".,xchange-core,{module_name}",
            "--also-make",
            "-DskipTests",
            "-Dmaven.javadoc.skip",
            "-DskipIntegrationTests=false",
            "verify",
    ]

    log_path.parent.mkdir(parents=True, exist_ok=True)
    with (
            log_path.open("w", encoding="utf-8") as log_file,
            redacted_log_path.open("w", encoding="utf-8") as redacted_log_file,
    ):
        process = subprocess.Popen(
                command,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                encoding="utf-8",
                errors="replace",
        )
        assert process.stdout is not None
        for line in process.stdout:
            print(line, end="")
            log_file.write(line)
            redacted_log_file.write(redact_log_line(line))
        return process.wait()


def parse_failsafe_reports(module_name: str) -> list[FailureRecord]:
    report_dir = Path(module_name) / "target" / "failsafe-reports"
    if not report_dir.exists():
        return []

    records: list[FailureRecord] = []
    for report in sorted(report_dir.glob("TEST-*.xml")):
        try:
            root = ET.parse(report).getroot()
        except ET.ParseError:
            continue

        suite_class = root.attrib.get("name", report.stem.removeprefix("TEST-"))
        for testcase in root.iter():
            if _local_name(testcase.tag) != "testcase":
                continue
            test_class = testcase.attrib.get("classname") or suite_class
            test_name = testcase.attrib.get("name") or test_class
            for child in list(testcase):
                child_kind = _local_name(child.tag)
                if child_kind not in {"error", "failure"}:
                    continue
                records.append(
                        FailureRecord(
                                kind=child_kind,
                                test_class=test_class,
                                test_name=test_name,
                                type_name=child.attrib.get("type", ""),
                                message=child.attrib.get("message", ""),
                                text=child.text or "",
                        )
                )
    return records


def _local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def classify_results(
        module_name: str,
        exit_code: int,
        log_text: str,
        failures: list[FailureRecord],
        history: list[dict[str, Any]] | None = None,
        streak_threshold: int = DEFAULT_STREAK_THRESHOLD,
) -> dict[str, Any]:
    hard_failures: list[dict[str, Any]] = []
    transient_failures: list[dict[str, Any]] = []

    if failures:
        for failure in failures:
            classification = classify_failure(module_name, failure)
            if classification["transient"]:
                transient_failures.append(classification)
            else:
                hard_failures.append(classification)
    elif exit_code != 0:
        classification = classify_log_failure(module_name, log_text)
        if classification["transient"]:
            transient_failures.append(classification)
        else:
            hard_failures.append(classification)

    fingerprints = sorted({failure["fingerprint"] for failure in transient_failures})
    streak = calculate_streak(fingerprints, history or [], streak_threshold)

    if exit_code == 0 and not hard_failures and not transient_failures:
        status = "success"
        summary = "Integration tests passed."
    elif hard_failures:
        status = "failure"
        summary = "Integration tests failed with non-transient failures."
    elif streak["max_count"] >= streak_threshold:
        status = "persistent_transient_failure"
        summary = "Transient failure repeated enough times to require investigation."
    else:
        status = "transient_warning"
        summary = "Only allowlisted transient failures were detected."

    return {
            "module": module_name,
            "exit_code": exit_code,
            "status": status,
            "summary": summary,
            "fingerprints": fingerprints,
            "transient_failures": transient_failures,
            "hard_failures": hard_failures,
            "streak": streak,
    }


def classify_failure(module_name: str, failure: FailureRecord) -> dict[str, Any]:
    combined_text = "\n".join(
            value for value in [failure.type_name, failure.message, failure.text] if value
    )
    location = f"{failure.test_class}.{failure.test_name}"

    if failure.kind == "failure":
        return hard_classification(module_name, location, "assertion_failure", "JUnit assertion failure", combined_text)

    if JACKSON_PATTERN.search(combined_text):
        return hard_classification(module_name, location, "jackson_mapping", "Jackson mapping failure", combined_text)

    if REPO_NPE_PATTERN.search(combined_text):
        return hard_classification(module_name, location, "repo_null_pointer", "Repo-owned NullPointerException", combined_text)

    transient = match_transient(combined_text)
    if transient is not None:
        category, reason = transient
        return transient_classification(module_name, location, category, reason, combined_text)

    return hard_classification(module_name, location, "test_error", root_cause(combined_text), combined_text)


def classify_log_failure(module_name: str, log_text: str) -> dict[str, Any]:
    if COMPILATION_PATTERN.search(log_text):
        return hard_classification(module_name, "maven", "compilation_error", "Compilation failure", log_text)

    if JACKSON_PATTERN.search(log_text):
        return hard_classification(module_name, "maven", "jackson_mapping", "Jackson mapping failure", log_text)

    if REPO_NPE_PATTERN.search(log_text):
        return hard_classification(module_name, "maven", "repo_null_pointer", "Repo-owned NullPointerException", log_text)

    transient = match_transient(log_text)
    if transient is not None:
        category, reason = transient
        return transient_classification(module_name, "maven", category, reason, log_text)

    return hard_classification(module_name, "maven", "build_failure", "Maven build failure", log_text)


def match_transient(text: str) -> tuple[str, str] | None:
    for category, pattern, reason in TRANSIENT_PATTERNS:
        if re.search(pattern, text, re.IGNORECASE):
            return category, reason
    return None


def transient_classification(
        module_name: str, location: str, category: str, reason: str, text: str
) -> dict[str, Any]:
    return build_classification(module_name, location, category, reason, text, transient=True)


def hard_classification(
        module_name: str, location: str, category: str, reason: str, text: str
) -> dict[str, Any]:
    return build_classification(module_name, location, category, reason, text, transient=False)


def build_classification(
        module_name: str,
        location: str,
        category: str,
        reason: str,
        text: str,
        transient: bool,
) -> dict[str, Any]:
    normalized = normalize_message(reason or root_cause(text))
    fingerprint_key = f"{module_name}|{location}|{category}|{normalized}"
    return {
            "transient": transient,
            "category": category,
            "reason": reason,
            "location": location,
            "normalized_message": normalized,
            "fingerprint": hashlib.sha256(fingerprint_key.encode("utf-8")).hexdigest()[:16],
    }


def root_cause(text: str) -> str:
    for line in reversed(text.splitlines()):
        stripped = line.strip()
        if stripped and not stripped.startswith("at "):
            return stripped[:240]
    return "unknown failure"


def normalize_message(message: str) -> str:
    normalized = re.sub(r"https?://\S+", "<url>", message)
    normalized = re.sub(r"\b[0-9a-f]{8,}\b", "<hex>", normalized, flags=re.IGNORECASE)
    normalized = re.sub(r"\d+", "<n>", normalized)
    return " ".join(normalized.split()).lower()[:240]


def calculate_streak(
        current_fingerprints: list[str], history: list[dict[str, Any]], threshold: int
) -> dict[str, Any]:
    counts: dict[str, int] = {}
    source = "unavailable"

    for fingerprint in current_fingerprints:
        counts[fingerprint] = 1
        for item in history:
            source = item.get("source", source)
            if item.get("missing") or item.get("status") == "success":
                break
            prior_fingerprints = set(item.get("fingerprints") or [])
            if fingerprint not in prior_fingerprints:
                break
            counts[fingerprint] += 1
            if counts[fingerprint] >= threshold:
                break

    max_count = max(counts.values(), default=0)
    return {
            "threshold": threshold,
            "max_count": max_count,
            "counts": counts,
            "source": source,
    }


def load_github_history(module_name: str, artifact_name: str, limit: int) -> list[dict[str, Any]]:
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    repository = os.environ.get("GITHUB_REPOSITORY")
    branch = os.environ.get("GITHUB_REF_NAME")
    current_run_id = os.environ.get("GITHUB_RUN_ID")

    if not all([token, repository, branch, current_run_id]):
        return [{"source": "unavailable", "missing": True}]

    workflow_path = current_run_path(token, repository, current_run_id) or current_workflow_path()
    if not workflow_path:
        return [{"source": "unavailable", "missing": True}]

    query = urllib.parse.urlencode({"branch": branch, "status": "completed", "per_page": 50})
    try:
        runs = github_json(token, repository, f"/actions/runs?{query}").get("workflow_runs", [])
    except (OSError, urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
        return [{"source": "unavailable", "missing": True, "error": str(error)}]

    history: list[dict[str, Any]] = []
    for run in runs:
        if str(run.get("id")) == current_run_id:
            continue
        if run.get("path") != workflow_path:
            continue
        if run.get("head_branch") != branch:
            continue

        try:
            classification = download_classification(token, repository, run["id"], artifact_name)
        except (OSError, urllib.error.URLError, TimeoutError, json.JSONDecodeError, zipfile.BadZipFile):
            classification = None
        if classification is None:
            history.append({"source": "github", "missing": True, "run_id": run["id"]})
        else:
            history.append(
                    {
                            "source": "github",
                            "run_id": run["id"],
                            "status": classification.get("status"),
                            "fingerprints": classification.get("fingerprints", []),
                            "module": classification.get("module", module_name),
                    }
            )
        if len(history) >= limit:
            break
    if not history:
        history.append({"source": "github", "missing": True})
    return history


def current_run_path(token: str, repository: str, current_run_id: str) -> str | None:
    try:
        run = github_json(token, repository, f"/actions/runs/{current_run_id}")
    except (OSError, urllib.error.URLError, TimeoutError, json.JSONDecodeError):
        return None
    path = run.get("path")
    return path if isinstance(path, str) else None


def current_workflow_path() -> str | None:
    workflow_ref = os.environ.get("GITHUB_WORKFLOW_REF")
    repository = os.environ.get("GITHUB_REPOSITORY")
    if not workflow_ref or not repository:
        return None
    prefix = f"{repository}/"
    if prefix not in workflow_ref:
        return None
    path_with_ref = workflow_ref.split(prefix, 1)[1]
    return path_with_ref.rsplit("@", 1)[0]


def download_classification(
        token: str, repository: str, run_id: int, artifact_name: str
) -> dict[str, Any] | None:
    artifacts = github_json(token, repository, f"/actions/runs/{run_id}/artifacts").get(
            "artifacts", []
    )
    artifact = next((item for item in artifacts if item.get("name") == artifact_name), None)
    if artifact is None:
        return None

    data = github_bytes(token, artifact["archive_download_url"])
    with zipfile.ZipFile(BytesIO(data)) as archive:
        for name in archive.namelist():
            if name.endswith("classification.json"):
                with archive.open(name) as classification_file:
                    return json.load(classification_file)
    return None


def github_json(token: str, repository: str, api_path: str) -> dict[str, Any]:
    url = f"https://api.github.com/repos/{repository}{api_path}"
    return json.loads(github_bytes(token, url).decode("utf-8"))


def github_bytes(token: str, url: str) -> bytes:
    request = urllib.request.Request(
            url,
            headers={
                    "Accept": "application/vnd.github+json",
                    "Authorization": f"Bearer {token}",
                    "X-GitHub-Api-Version": "2022-11-28",
            },
    )
    with urllib.request.urlopen(request, timeout=20) as response:
        return response.read()


def write_outputs(classification: dict[str, Any], output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    classification_path = output_dir / "classification.json"
    classification_path.write_text(json.dumps(classification, indent=2, sort_keys=True) + "\n")

    if classification["status"] == "transient_warning":
        message = classification["summary"] + " " + ", ".join(classification["fingerprints"])
        print(f"::warning title=XChange transient integration failure::{escape_annotation(message)}")

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with Path(summary_path).open("a", encoding="utf-8") as summary:
            summary.write(f"### Integration CI classification: {classification['module']}\n\n")
            summary.write(f"- Status: `{classification['status']}`\n")
            summary.write(f"- Summary: {classification['summary']}\n")
            summary.write(f"- Transient failures: {len(classification['transient_failures'])}\n")
            summary.write(f"- Hard failures: {len(classification['hard_failures'])}\n")
            summary.write(f"- Max transient streak: {classification['streak']['max_count']}\n")


def escape_annotation(message: str) -> str:
    return message.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def redact_log_line(line: str) -> str:
    return SECRET_VALUE_PATTERN.sub(lambda match: f"{match.group(1)}<redacted>", line)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--module-name", required=True)
    parser.add_argument("--history-limit", type=int, default=5)
    parser.add_argument("--streak-threshold", type=int, default=DEFAULT_STREAK_THRESHOLD)
    parser.add_argument("--output-dir", type=Path, default=OUTPUT_DIR)
    args = parser.parse_args(argv)

    output_dir = args.output_dir
    log_path = output_dir / "maven.log"
    redacted_log_path = output_dir / "maven.redacted.log"
    exit_code = run_maven(args.module_name, log_path, redacted_log_path)
    log_text = log_path.read_text(encoding="utf-8", errors="replace")
    failures = parse_failsafe_reports(args.module_name)

    artifact_name = f"integration-ci-{args.module_name}"
    history = load_github_history(args.module_name, artifact_name, args.history_limit)
    classification = classify_results(
            args.module_name,
            exit_code,
            log_text,
            failures,
            history=history,
            streak_threshold=args.streak_threshold,
    )
    write_outputs(classification, output_dir)

    if classification["status"] in {"success", "transient_warning"}:
        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
