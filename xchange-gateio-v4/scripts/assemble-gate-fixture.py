#!/usr/bin/env python3
"""Assemble the pinned Gate API v4 protocol fixture from extracted domain files.

Normalizes: adds domain, splits '»' nesting depth from param names, sorts
endpoints, and appends extra endpoints (documented in the official SDK but not
present on the docs pages) with explicit provenance.

Usage:
  assemble-gate-fixture.py <extracted-dir> <output.json>
"""
import json
import os
import sys

DOMAINS = ["spot", "unified", "futures", "delivery", "options", "wallet", "account", "withdrawal"]

# Endpoints served by the live API (verified in the official gateapi-go SDK,
# pinned at openapi-generator 7.1.8) but not rendered on the docs pages as of
# the pinned date. Kept here so the drift tool tracks them explicitly.
EXTRA_ENDPOINTS = [
    {
        "domain": "spot",
        "method": "GET",
        "path": "/spot/accounts",
        "summary": "Query spot accounts (consolidated into /unified/accounts in the docs pages; still served by the live API)",
        "requires_auth": True,
        "params": [{"name": "currency", "in": "query", "type": "string", "required": False, "depth": 0, "description": "Retrieve data of the specified currency"}],
        "responses": [{"status": "200", "schema": "Account"}],
        "schemas": [],
        "source": "gateapi-go sdk (openapi-generator 7.1.8), absent from docs pages 2026-08-13",
    }
]


def split_name(name):
    """Split a docs name like '»» trigger_price' into (name, depth).

    Nested fields are prefixed by one '»' per level with a single trailing
    space; consume the complete leading run, not just a single marker.
    """
    depth = 0
    while name.startswith("»"):
        depth += 1
        name = name[1:]
    if name.startswith(" "):
        name = name[1:]
    return name, depth


def main():
    src_dir, out = sys.argv[1], sys.argv[2]
    endpoints = []
    for d in DOMAINS:
        with open(os.path.join(src_dir, f"{d}.json")) as f:
            data = json.load(f)
        for ep in data["endpoints"]:
            params = []
            for p in ep.get("params", []):
                name, depth = split_name(p.get("Name", ""))
                params.append({
                    "name": name,
                    "in": p.get("In", "").strip().lower(),
                    "type": p.get("Type", "").strip(),
                    "required": str(p.get("Required", "")).strip().lower() == "true",
                    "depth": depth,
                    "description": p.get("Description", "").strip(),
                })
            responses = [
                {"status": r.get("Status", "").strip(), "schema": r.get("Schema", "").strip()}
                for r in ep.get("responses", [])
            ]
            schemas = []
            for s in ep.get("schemas", []):
                fields = []
                for f in s.get("fields", []):
                    name, depth = split_name(f.get("Name", ""))
                    fields.append({
                        "name": name,
                        "type": f.get("Type", "").strip(),
                        "depth": depth,
                        "description": f.get("Description", "").strip(),
                    })
                schemas.append({"status": s.get("status"), "fields": fields})
            endpoints.append({
                "domain": d,
                "method": ep["method"],
                "path": ep["path"],
                "summary": ep.get("summary", "").strip(),
                "requires_auth": ep.get("requiresAuth", True),
                "params": params,
                "responses": responses,
                "schemas": schemas,
            })
    endpoints.extend(EXTRA_ENDPOINTS)
    endpoints.sort(key=lambda e: (e["domain"], e["method"], e["path"]))

    fixture = {
        "fixture": "gate-api-v4",
        "version": "1",
        "description": (
            "Normalized protocol snapshot of the official Gate API v4 contract, extracted from the "
            "server-rendered OpenAPI docs pages (www.gate.com/docs/developers/apiv4/en/<domain>/). "
            "Hand-written adapter code is the public API; this fixture pins the wire contract for "
            "drift detection. Extra endpoints carry explicit provenance."
        ),
        "pinned_at": "2026-08-13",
        "source_base": "https://www.gate.com/docs/developers/apiv4/en/",
        "domains": DOMAINS,
        "endpoints": endpoints,
    }
    with open(out, "w") as f:
        json.dump(fixture, f, indent=1, ensure_ascii=False)
    print(f"wrote {len(endpoints)} endpoints -> {out}")


if __name__ == "__main__":
    main()
