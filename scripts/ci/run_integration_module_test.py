#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import io
import os
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch


SCRIPT_PATH = Path(__file__).with_name("run-integration-module.py")
SPEC = importlib.util.spec_from_file_location("run_integration_module", SCRIPT_PATH)
runner = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = runner
SPEC.loader.exec_module(runner)


class FakeMavenProcess:

    def __init__(self, lines: list[str], return_code: int = 0) -> None:
        self.stdout = iter(lines)
        self.return_code = return_code
        self.killed = False

    def wait(self) -> int:
        return self.return_code

    def kill(self) -> None:
        self.killed = True


class IntegrationClassificationTest(unittest.TestCase):

    def test_deribit_http_502_is_transient(self) -> None:
        failure = runner.FailureRecord(
                kind="error",
                test_class="org.knowm.xchange.deribit.v2.DeribitExceptionIntegration",
                test_name="setUp",
                type_name="org.knowm.xchange.exceptions.ExchangeException",
                message="si.mazi.rescu.HttpStatusIOException: HTTP status code was not OK: 502",
                text="Caused by: si.mazi.rescu.HttpStatusIOException: HTTP status code was not OK: 502",
        )

        classification = runner.classify_results("xchange-deribit", 1, "", [failure])

        self.assertEqual("transient_warning", classification["status"])
        self.assertEqual("http_502", classification["transient_failures"][0]["category"])
        self.assertEqual([], classification["hard_failures"])

    def test_coinex_invalid_uri_mapping_is_hard_failure(self) -> None:
        failure = runner.FailureRecord(
                kind="error",
                test_class="org.knowm.xchange.coinex.service.CoinexMarketDataServiceRawIntegration",
                test_name="valid_chainInfos",
                type_name="com.fasterxml.jackson.databind.exc.InvalidFormatException",
                message="Cannot deserialize value of type `java.net.URI`",
                text="InvalidFormatException: Illegal character in path at index 32",
        )

        classification = runner.classify_results("xchange-coinex", 1, "", [failure])

        self.assertEqual("failure", classification["status"])
        self.assertEqual("jackson_mapping", classification["hard_failures"][0]["category"])

    def test_hard_mapping_failure_takes_precedence_over_http_transient(self) -> None:
        failure = runner.FailureRecord(
                kind="error",
                test_class="ExampleIntegration",
                test_name="setup",
                type_name="com.fasterxml.jackson.databind.exc.InvalidFormatException",
                message="HTTP status code was not OK: 503 while mapping response",
                text="InvalidFormatException: Cannot deserialize value of type `java.net.URI`",
        )

        classification = runner.classify_results("xchange-example", 1, "", [failure])

        self.assertEqual("failure", classification["status"])
        self.assertEqual("jackson_mapping", classification["hard_failures"][0]["category"])

    def test_repo_null_pointer_takes_precedence_over_http_transient(self) -> None:
        failure = runner.FailureRecord(
                kind="error",
                test_class="ExampleIntegration",
                test_name="setup",
                type_name="java.lang.NullPointerException",
                message="HTTP status code was not OK: 503",
                text="java.lang.NullPointerException\n\tat org.knowm.xchange.example.Example.parse(Example.java:1)",
        )

        classification = runner.classify_results("xchange-example", 1, "", [failure])

        self.assertEqual("failure", classification["status"])
        self.assertEqual("repo_null_pointer", classification["hard_failures"][0]["category"])

    def test_timeout_log_without_reports_is_transient(self) -> None:
        classification = runner.classify_results(
                "xchange-kraken",
                1,
                "java.net.SocketTimeoutException: Read timed out",
                [],
        )

        self.assertEqual("transient_warning", classification["status"])
        self.assertEqual("timeout", classification["transient_failures"][0]["category"])

    def test_assertion_failure_is_hard_failure(self) -> None:
        failure = runner.FailureRecord(
                kind="failure",
                test_class="ExampleIntegration",
                test_name="shouldMatch",
                type_name="org.opentest4j.AssertionFailedError",
                message="expected true but was false",
                text="AssertionFailedError",
        )

        classification = runner.classify_results("xchange-example", 1, "", [failure])

        self.assertEqual("failure", classification["status"])
        self.assertEqual("assertion_failure", classification["hard_failures"][0]["category"])

    def test_compilation_error_without_reports_is_hard_failure(self) -> None:
        classification = runner.classify_results(
                "xchange-example",
                1,
                "[ERROR] COMPILATION ERROR : cannot find symbol",
                [],
        )

        self.assertEqual("failure", classification["status"])
        self.assertEqual("compilation_error", classification["hard_failures"][0]["category"])

    def test_repeated_transient_reaches_persistent_threshold(self) -> None:
        failure = runner.FailureRecord(
                kind="error",
                test_class="ExampleIntegration",
                test_name="setup",
                type_name="si.mazi.rescu.HttpStatusIOException",
                message="HTTP status code was not OK: 503",
                text="",
        )
        first = runner.classify_results("xchange-example", 1, "", [failure])
        fingerprint = first["fingerprints"][0]

        second = runner.classify_results(
                "xchange-example",
                1,
                "",
                [failure],
                history=[{"source": "test", "status": "transient_warning", "fingerprints": [fingerprint]}],
        )
        third = runner.classify_results(
                "xchange-example",
                1,
                "",
                [failure],
                history=[
                        {"source": "test", "status": "transient_warning", "fingerprints": [fingerprint]},
                        {"source": "test", "status": "transient_warning", "fingerprints": [fingerprint]},
                ],
        )

        self.assertEqual("transient_warning", second["status"])
        self.assertEqual(2, second["streak"]["max_count"])
        self.assertEqual("persistent_transient_failure", third["status"])
        self.assertEqual(3, third["streak"]["max_count"])

    def test_missing_artifact_resets_streak(self) -> None:
        failure = runner.FailureRecord(
                kind="error",
                test_class="ExampleIntegration",
                test_name="setup",
                type_name="si.mazi.rescu.HttpStatusIOException",
                message="HTTP status code was not OK: 504",
                text="",
        )

        classification = runner.classify_results(
                "xchange-example",
                1,
                "",
                [failure],
                history=[{"source": "test", "missing": True}],
        )

        self.assertEqual("transient_warning", classification["status"])
        self.assertEqual(1, classification["streak"]["max_count"])

    def test_changed_fingerprint_resets_each_streak_independently(self) -> None:
        streak = runner.calculate_streak(
                ["A", "B"],
                [
                        {"source": "test", "status": "transient_warning", "fingerprints": ["A"]},
                        {"source": "test", "status": "transient_warning", "fingerprints": ["B"]},
                        {"source": "test", "status": "transient_warning", "fingerprints": ["B"]},
                ],
                threshold=3,
        )

        self.assertEqual(2, streak["counts"]["A"])
        self.assertEqual(1, streak["counts"]["B"])
        self.assertEqual(2, streak["max_count"])

    def test_log_redaction_masks_common_secret_values(self) -> None:
        line = "Authorization: Bearer abc123 token=def456 api_key: ghi789 password=hunter2\n"

        self.assertEqual(
                "Authorization: Bearer <redacted> token=<redacted> api_key: <redacted> password=<redacted>\n",
                runner.redact_log_line(line),
        )

    def test_run_maven_streams_redacted_output_and_keeps_raw_log(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output_dir = Path(directory)
            raw_log = output_dir / "maven.log"
            redacted_log = output_dir / "maven.redacted.log"
            fake_process = FakeMavenProcess(
                    ["Authorization: Bearer abc123 token=def456\n"],
                    return_code=7,
            )
            stdout = io.StringIO()

            with (
                    patch.object(runner.subprocess, "Popen", return_value=fake_process),
                    redirect_stdout(stdout),
            ):
                exit_code = runner.run_maven("xchange-example", raw_log, redacted_log)

            self.assertEqual(7, exit_code)
            self.assertEqual("Authorization: Bearer abc123 token=def456\n", raw_log.read_text())
            self.assertEqual(
                    "Authorization: Bearer <redacted> token=<redacted>\n",
                    redacted_log.read_text(),
            )
            self.assertEqual(redacted_log.read_text(), stdout.getvalue())
            self.assertNotIn("abc123", stdout.getvalue())

    def test_parses_failsafe_xml(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            previous_cwd = os.getcwd()
            os.chdir(directory)
            try:
                report_dir = Path("xchange-example/target/failsafe-reports")
                report_dir.mkdir(parents=True)
                report_dir.joinpath("TEST-ExampleIntegration.xml").write_text(
                        """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="ExampleIntegration" tests="1" errors="1" failures="0">
    <testcase classname="ExampleIntegration" name="setup">
        <error type="java.net.SocketTimeoutException" message="Read timed out">stack</error>
    </testcase>
</testsuite>
""",
                        encoding="utf-8",
                )

                failures = runner.parse_failsafe_reports("xchange-example")
            finally:
                os.chdir(previous_cwd)

        self.assertEqual(1, len(failures))
        self.assertEqual("ExampleIntegration", failures[0].test_class)
        self.assertEqual("setup", failures[0].test_name)


if __name__ == "__main__":
    unittest.main()
