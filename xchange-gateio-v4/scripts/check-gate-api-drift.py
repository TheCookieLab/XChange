#!/usr/bin/env python3
"""Drift check for the pinned Gate API v4 protocol fixture.

Offline mode (default, CI-safe):
  - fixture integrity: pinned metadata, unique method+path endpoints
  - implemented-surface manifest: every manifest endpoint present in the
    fixture (extra entries allowed with provenance), and every required param
    in the fixture for an implemented endpoint is covered by the manifest
  Exits 1 when drift is detected in implemented paths/verbs/required fields.

Online mode (--fresh <dir>, requires gate.com access):
  - re-extract per-domain JSON dumps (see extract-gate-docs.js), normalize,
    and diff the endpoint set and required params against the pinned fixture.
  - drift on implemented endpoints exits 1; unrelated surface changes are
    reported (exit 0) so the pinned date can be refreshed deliberately.

Usage:
  check-gate-api-drift.py [--fixture FILE] [--manifest FILE] [--fresh DIR]
"""
import argparse
import json
import sys

DEFAULT_FIXTURE = "src/main/resources/protocol/gate-api-v4-2026-08-13.json"
DEFAULT_MANIFEST = "src/main/resources/protocol/implemented-endpoints.json"
DOMAINS = ["spot", "unified", "futures", "delivery", "options", "wallet", "account", "withdrawal"]


def required_params(endpoint):
    """Required param names; body-nested fields are prefixed body., the bare
    body container itself is dropped."""
    out = set()
    for p in endpoint.get("params", []):
        if not p.get("required"):
            continue
        name = p.get("name", "")
        if p.get("in") == "body" and p.get("depth", 0) == 0:
            continue  # container; nested fields carry the real requirements
        out.add(("body." if p.get("in") == "body" else "") + name)
    return out


def fixture_index(fixture):
    return {(e["method"], e["path"]): e for e in fixture["endpoints"]}


def check_offline(fixture, manifest, report):
    errors = []
    if fixture.get("fixture") != "gate-api-v4":
        errors.append("fixture marker missing")
    if not fixture.get("pinned_at"):
        errors.append("pinned_at missing")
    if not fixture.get("source_base"):
        errors.append("source_base missing")
    idx = fixture_index(fixture)
    if len(idx) != len(fixture["endpoints"]):
        errors.append("duplicate method+path endpoints in fixture")
    for i, ep in enumerate(fixture["endpoints"]):
        for p in ep.get("params", []):
            if not all(k in p for k in ("name", "in", "required", "depth")):
                errors.append(f"endpoint {ep['method']} {ep['path']}: malformed param {p}")
                break
    for entry in manifest:
        key = (entry["method"], entry["path"])
        if entry.get("extra"):
            continue  # pinned with provenance; not on the docs pages
        ep = idx.get(key)
        if ep is None:
            errors.append(f"IMPLEMENTED endpoint missing from fixture: {key[0]} {key[1]}")
            continue
        missing = required_params(ep) - set(entry.get("required_params", []))
        if missing:
            errors.append(
                f"DRIFT: {key[0]} {key[1]} requires params not in manifest: {sorted(missing)}"
            )
    for e in errors:
        report.append(f"ERROR: {e}")
    return not errors


def normalize_fresh(path):
    with open(path) as f:
        data = json.load(f)
    eps = []
    for ep in data["endpoints"]:
        required = set()
        for p in ep.get("params", []):
            if not str(p.get("Required", "")).strip().lower() == "true":
                continue
            name = str(p.get("Name", "")).strip()
            depth = 0
            while name.startswith("»"):
                depth += 1
                name = name[1:]
            if name.startswith(" "):
                name = name[1:]
            p_in = str(p.get("In", "")).strip().lower()
            if p_in == "body" and depth == 0:
                continue
            required.add(("body." if p_in == "body" else "") + name)
        eps.append({"method": ep["method"], "path": ep["path"], "required": required})
    return eps


def check_online(fixture, fresh_dir, manifest, report):
    fresh = []
    for d in DOMAINS:
        path = f"{fresh_dir}/{d}.json"
        try:
            fresh.extend(normalize_fresh(path))
        except FileNotFoundError:
            report.append(f"WARN: no fresh dump for domain {d} ({path})")
    pinned = [
        {"method": e["method"], "path": e["path"], "required": required_params(e)}
        for e in fixture["endpoints"]
    ]
    pinned_idx = {(e["method"], e["path"]): e["required"] for e in pinned}
    fresh_idx = {(e["method"], e["path"]): e["required"] for e in fresh}
    implemented = {
        (m["method"], m["path"]) for m in manifest if not m.get("extra")
    }
    errors, notes = [], []
    for key in sorted(pinned_idx.keys() - fresh_idx.keys()):
        note = f"REMOVED from docs: {key[0]} {key[1]}"
        (errors if key in implemented else notes).append(note)
    for key in sorted(fresh_idx.keys() - pinned_idx.keys()):
        note = f"ADDED in docs: {key[0]} {key[1]}"
        (errors if key in implemented else notes).append(note)
    for key in sorted(pinned_idx.keys() & fresh_idx.keys()):
        added = fresh_idx[key] - pinned_idx[key]
        if added:
            note = f"REQUIRED-PARAM DRIFT: {key[0]} {key[1]} now requires {sorted(added)}"
            (errors if key in implemented else notes).append(note)
    for e in errors:
        report.append(f"ERROR: {e}")
    for n in notes:
        report.append(f"NOTE: {n}")
    return not errors


def main():
    ap = argparse.ArgumentParser(description="Gate API v4 protocol drift check")
    ap.add_argument("--fixture", default=DEFAULT_FIXTURE)
    ap.add_argument("--manifest", default=DEFAULT_MANIFEST)
    ap.add_argument("--fresh", help="dir with freshly extracted per-domain dumps for online diff")
    args = ap.parse_args()
    with open(args.fixture) as f:
        fixture = json.load(f)
    with open(args.manifest) as f:
        manifest = json.load(f)
    report = []
    if args.fresh:
        ok = check_online(fixture, args.fresh, manifest, report)
    else:
        ok = check_offline(fixture, manifest, report)
    for line in report:
        print(line)
    if not report:
        print("no drift detected")
    print("PASS" if ok else "FAIL")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
