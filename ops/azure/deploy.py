#!/usr/bin/env python3
"""Deploy the tested JAR through Kudu using the operator's Entra identity."""
import hashlib
import argparse
import json
import time
import requests
from bootstrap import az, psql, ROOT, APP, VAULT

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--manifest", type=str, default="backend/build/azure-release-candidate.json")
args = parser.parse_args()
manifest_path = ROOT / args.manifest
if not manifest_path.is_file():
    raise RuntimeError("Prepare a tested candidate with python3 ops/azure/prepare_release.py first")
inventory = json.loads(manifest_path.read_text())
artifact = ROOT / inventory["artifact"]
expected = hashlib.sha256(artifact.read_bytes()).hexdigest()
if expected != inventory["sha256"]:
    raise RuntimeError("Build artifact differs from the verified release manifest; test and refresh the manifest first")
for name, source_hash in inventory["sourceFiles"].items():
    if hashlib.sha256((ROOT / name).read_bytes()).hexdigest() != source_hash:
        raise RuntimeError("Source changed after release preparation; test, build and prepare again")
if az("account", "show")["id"] != "f14e91e2-b819-4cd6-ac39-e4a3909c17b9":
    raise RuntimeError("Unexpected subscription")
base = f"https://{APP}.scm.azurewebsites.net"
access = az("account", "get-access-token")
headers = {"Authorization": "Bearer " + access["accessToken"]}
print("Deploying verified JAR with Entra authentication; basic publishing remains disabled.", flush=True)
# Async OneDeploy fails on this runtime with SCM basic authentication disabled.
deployment = az("webapp", "deploy", "-g", "studylog-rg", "-n", APP, "--src-path", str(artifact),
                "--type", "jar", "--async", "false", "--track-status", "false")
status = deployment.get("status")
if status != 4:
    raise RuntimeError("Kudu deployment did not succeed")
remote = requests.get(base + "/api/vfs/site/wwwroot/app.jar", headers=headers, stream=True, timeout=90)
remote.raise_for_status()
digest = hashlib.sha256()
for chunk in remote.iter_content(1024 * 1024):
    digest.update(chunk)
if digest.hexdigest() != expected:
    raise RuntimeError("Deployed artifact hash does not match tested JAR")
inventory.update({"deployedSha256": expected, "deploymentId": deployment["id"], "kuduStatus": status,
                  "endTime": deployment.get("end_time")})
print("Remote JAR SHA-256 matches the tested artifact:", expected, flush=True)
# Run only after Flyway has finished; the runtime never needs migration-history access.
for attempt in range(40):
    try:
        health = requests.get(f"https://{APP}.azurewebsites.net/actuator/health", timeout=10)
        if health.status_code == 200 and health.json().get("status") == "UP":
            break
    except (requests.RequestException, ValueError):
        pass
    if attempt % 6 == 0:
        print("Waiting for runtime, DB migration and Blob readiness.", flush=True)
    time.sleep(5)
else:
    raise RuntimeError("Deployment uploaded, but production health did not become UP")
password = az("keyvault", "secret", "show", "--vault-name", VAULT, "--name", "db-migration-password")["value"]
psql("junseo", "REVOKE ALL ON TABLE public.flyway_schema_history FROM junseo_runtime;", "junseo_migrator", password)
previous = json.loads((ROOT / "docs/azure-release.json").read_text())
inventory.update({"status": "deployed", "previousDeployedSha256": previous.get("deployedSha256"),
                  "health": "UP"})
(ROOT / "docs/azure-release.json").write_text(json.dumps(inventory, indent=2) + "\n")
print("Runtime UP; migration-history permissions restricted.", flush=True)
