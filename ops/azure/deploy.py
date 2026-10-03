#!/usr/bin/env python3
"""Deploy the tested JAR through Kudu using the operator's Entra identity."""
import hashlib
import json
import time
import requests
from bootstrap import az, psql, ROOT, APP, VAULT

artifact = ROOT / "backend/build/libs/junseo-backend-0.1.0.jar"
inventory = json.loads((ROOT / "docs/azure-release.json").read_text())
expected = hashlib.sha256(artifact.read_bytes()).hexdigest()
if expected != inventory["sha256"]:
    raise RuntimeError("Build artifact differs from the verified release manifest; test and refresh the manifest first")
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
(ROOT / "docs/azure-release.json").write_text(json.dumps(inventory, indent=2) + "\n")
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
print("Runtime UP; migration-history permissions restricted.", flush=True)
