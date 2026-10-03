#!/usr/bin/env python3
"""Prepare a local deployment manifest after `./gradlew test bootJar` (no Azure writes)."""
import hashlib
import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    artifact = ROOT / "backend/build/libs/junseo-backend-0.1.0.jar"
    reports = sorted((ROOT / "backend/build/test-results/test").glob("TEST-*.xml"))
    if not artifact.is_file() or not reports:
        raise RuntimeError("Run ./gradlew test bootJar in backend first")
    sources = sorted(p for p in (ROOT / "backend/src").rglob("*") if p.is_file())
    sources += [ROOT / "backend/build.gradle.kts", ROOT / "backend/settings.gradle.kts"]
    oldest_report = min(p.stat().st_mtime for p in reports)
    if any(p.stat().st_mtime > oldest_report for p in sources):
        raise RuntimeError("Sources changed after the test reports; rerun ./gradlew test bootJar")
    if any(p.stat().st_mtime > artifact.stat().st_mtime for p in sources):
        raise RuntimeError("Sources changed after the JAR; rebuild bootJar")
    totals = dict.fromkeys(("tests", "failures", "errors", "skipped"), 0)
    for report in reports:
        suite = ET.parse(report).getroot()
        for key in totals:
            totals[key] += int(suite.attrib.get(key, 0))
    if totals["tests"] == 0 or totals["failures"] or totals["errors"] or totals["skipped"]:
        raise RuntimeError("A complete passing backend test run is required")
    manifest = {
        "sourceCommit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "preparedAt": datetime.now(timezone.utc).isoformat(),
        "artifact": str(artifact.relative_to(ROOT)),
        "sha256": digest(artifact),
        "sourceFiles": {str(p.relative_to(ROOT)): digest(p) for p in sources},
        "backendTests": totals,
        "status": "prepared-not-deployed",
    }
    target = ROOT / "backend/build/azure-release-candidate.json"
    target.write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Prepared {target.relative_to(ROOT)}: {totals['tests']} tests, JAR {manifest['sha256']}")
    print("Azure was not modified. Deploy explicitly with python3 ops/azure/deploy.py.")


if __name__ == "__main__":
    main()
