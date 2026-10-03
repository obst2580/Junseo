#!/usr/bin/env python3
"""Connect a supplied Junseo Firebase project; put the server key only in Key Vault."""
import argparse
import json
import os
from pathlib import Path
import tempfile
from bootstrap import az, APP, RG, VAULT


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--google-services", type=Path, required=True)
    parser.add_argument("--service-account", type=Path, required=True)
    args = parser.parse_args()
    client = json.loads(args.google_services.read_text())
    server = json.loads(args.service_account.read_text())
    project = client["project_info"]["project_id"]
    package = "com.junseo.app"
    packages = [c["client_info"]["android_client_info"]["package_name"] for c in client["client"]]
    if package not in packages or server.get("project_id") != project or server.get("type") != "service_account":
        raise RuntimeError("Firebase files must belong to the same project and the com.junseo.app Android app")
    if not server.get("private_key", "").startswith("-----BEGIN PRIVATE KEY-----"):
        raise RuntimeError("Service-account private key is missing")
    if az("account", "show")["id"] != "f14e91e2-b819-4cd6-ac39-e4a3909c17b9":
        raise RuntimeError("Unexpected Azure subscription")
    with tempfile.NamedTemporaryFile(mode="w", delete=False) as f:
        os.chmod(f.name, 0o600)
        json.dump(server, f)
        temporary = f.name
    try:
        result = az("keyvault", "secret", "set", "--vault-name", VAULT,
                    "--name", "fcm-service-account", "--file", temporary)
        settings = {
            "JUNSEO_FCM_ENABLED": "true", "JUNSEO_FCM_PROJECT_ID": project,
            "JUNSEO_FCM_SERVICE_ACCOUNT_JSON": "@Microsoft.KeyVault(SecretUri=" + result["id"] + ")",
        }
        with open(temporary, "w") as f:
            json.dump(settings, f)
        az("webapp", "config", "appsettings", "set", "-g", RG, "-n", APP, "--settings", "@" + temporary)
    finally:
        os.unlink(temporary)
    print("Firebase project connected through Key Vault:", project)
    print("Rebuild Android with GOOGLE_SERVICES_JSON set to the supplied client configuration file.")


if __name__ == "__main__":
    main()
